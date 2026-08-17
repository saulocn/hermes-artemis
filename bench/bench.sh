#!/usr/bin/env bash
# Measures how fast the pipeline drains a backlog under the docker-compose resource limits.
#
#   ./bench/bench.sh <label> [messages] [recipients-per-message]
#   COMPOSE_PROFILES=rabbit ./bench/bench.sh rabbit 100 100
#
# Seeds a backlog through the API, then polls hermes.recipient until every row is sent.
# MAIL_MOCK=true, so this measures API -> DB -> enqueuer -> broker -> mailer, not SMTP.
#
# Two numbers matter and they are not the same:
#   - time to first delivery: dominated by the enqueuer's 30s scheduler, not by capacity.
#   - sustained throughput:   recipients/s while the pipeline is actually moving.
set -euo pipefail

LABEL="${1:?usage: bench.sh <label> [messages] [recipients-per-message]}"
MESSAGES="${2:-20}"
PER_MESSAGE="${3:-100}"
DRAIN_TIMEOUT="${DRAIN_TIMEOUT:-180}"

compose() { docker compose "$@"; }

# The enqueuer service is named differently per profile.
ENQUEUER=$(compose ps --services 2>/dev/null | grep -E '^enqueuer' | head -1)
: "${ENQUEUER:=enqueuer}"

count() {
  compose exec -T hermes-db psql -U hermes -d hermes -tAc "$1" | tr -d '[:space:]'
}

# Recipient lists are kept small per request because the app runs with
# quarkus.transaction-manager.default-transaction-timeout=2s.
recipients_json() {
  python3 -c "
import sys
batch, per, label = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3]
print(','.join('\"%s-%d-%d@hermes.test\"' % (label, batch, i) for i in range(per)))
" "$1" "$PER_MESSAGE" "$LABEL"
}

echo "== $LABEL: seeding $((MESSAGES * PER_MESSAGE)) recipients ($MESSAGES messages x $PER_MESSAGE) =="
echo "   enqueuer service: $ENQUEUER"
for b in $(seq 1 "$MESSAGES"); do
  curl -sf -o /dev/null -X POST localhost:8080/message \
    -H 'Content-Type: application/json' \
    -d "{\"title\":\"bench $LABEL $b\",\"text\":\"<p>bench</p>\",\"contentType\":\"text/html\",\"recipients\":[$(recipients_json "$b")]}"
done

seeded=$(count "select count(*) from hermes.recipient")
echo "rows in DB: $seeded"

echo "== waiting for drain =="
start=$(date +%s)
first_delivery=""
last_change=""
last=0
while :; do
  sent=$(count "select count(*) from hermes.recipient where recipient_sent")
  now=$(date +%s)
  elapsed=$((now - start))
  if [ "$sent" != "$last" ]; then
    [ -z "$first_delivery" ] && first_delivery=$elapsed
    last_change=$elapsed
    echo "  t=${elapsed}s sent=$sent/$seeded"
    last=$sent
  fi
  [ "$sent" -ge "$seeded" ] && break
  # Stragglers are expected: when the enqueuer's emitter buffer overflows, the affected rows
  # wait for the fallback job (up to ~20 min). That is recovery latency, not loss, so report
  # it instead of failing the run.
  if [ "$elapsed" -gt "$DRAIN_TIMEOUT" ]; then
    echo "  stragglers: $((seeded - sent)) of $seeded still unsent after ${elapsed}s"
    break
  fi
  sleep 2
done

overflows=$(compose logs "$ENQUEUER" 2>&1 | grep -c 'SRMSG00034' || true)

# The honest span, from the delivery timestamps themselves.
#
# The polled window below cannot be trusted on a large run: seeding N messages takes tens of
# seconds, delivery starts as soon as the first chunk is published, and the clock above only
# starts after seeding finishes. So every delivery that happened *during* seeding is counted
# against a window that excludes it. That is why "time to 1st delivery" reads 0s on big runs —
# it is not fast, it is late. Measured on a 100k run: polled window 59s against a real
# claimed_on span of 104s, reporting 1695/s for something that actually ran at 962/s.
#
# `is not null` is mandatory: without it the min/max cannot match the partial index and the
# query degrades to a sequential scan.
claimed_span=$(count "select coalesce(ceil(extract(epoch from max(claimed_on) - min(claimed_on))), 0) from hermes.recipient where claimed_on is not null")
claimed_count=$(count "select count(*) from hermes.recipient where claimed_on is not null")

echo
echo "== $LABEL result =="
python3 - "$seeded" "$sent" "${first_delivery:-0}" "${last_change:-0}" "$overflows" "$claimed_span" "$claimed_count" <<'PY'
import sys
seeded, sent, first, last, overflows, span, counted = (int(x) for x in sys.argv[1:8])
window = max(last - first, 1)
span = max(span, 1)
print(f"recipients seeded:    {seeded}")
print(f"delivered:            {sent}")
print(f"stragglers:           {seeded - sent} (recovered later by the fallback job)")
print(f"time to 1st delivery: {first}s (enqueuer scheduler runs every 30s)")
print()
print(f"sustained throughput: {counted/span:.0f} recipients/s "
      f"({counted/span*60:.0f}/min, {counted/span*3600:.0f}/h)")
print(f"  from claimed_on:    {counted} delivered across a {span}s span")
print(f"  same arithmetic as /admin/rates -> claimed.sustainedPerSecond, so the console and this")
print(f"  script agree on the same run. Trust this line.")
print()
print(f"polled window:        {sent/window:.0f} recipients/s over {window}s -- OVERSTATED")
print(f"  Kept for continuity with older results only. The clock starts after seeding, so any")
print(f"  delivery that overlapped seeding is credited to a window that excludes it.")
print(f"emitter overflows:    {overflows} (SRMSG00034 in the enqueuer log)")
PY
