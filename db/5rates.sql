-- Adds timestamps for rate computation: when a recipient was published and when it was claimed.
--
-- The pipeline records created_on at insert and nothing after — every later state transition is a
-- boolean flip with no time attached, so no rate can be computed. These two timestamps let the
-- dashboard show send latency (claimed - published) and publish latency (published - created).
--
-- WHY NULLABLE WITH NO DEFAULT: DEFAULT now() would recreate the exact bug being fixed: every row
-- would look published at insert time. NULL means "has not happened yet", which is what the rate
-- predicates need. And a volatile default would rewrite the 1.1M rows a load test leaves behind,
-- while nullable-no-default is a catalog-only change.
--
-- WHY claimed_on AND NOT delivered_on: the claim is a compare-and-set inside the delivery
-- transaction and rolls back if the send throws, so a *committed* claimed_on means the mail was
-- accepted — the set is the delivered set. But the *instant* is one SMTP round trip before the
-- accept. Anyone computing send latency as claimed_on - published_on gets a number short by
-- exactly that round trip.
--
-- GOTCHA (worth its own line): min(published_on) without a qualifier cannot use a partial index.
-- It must be written `where published_on is not null` to match the index predicate, or it
-- degrades to a sequential scan. The API will need that value.
--
-- The partial indexes are only small while a backlog exists; at steady state every row is
-- published (and claimed). On an existing 1.1M-row volume the index creation should use
-- CREATE INDEX CONCURRENTLY (which cannot run inside a transaction block — fine under psql
-- autocommit, pointless here where the table is empty at init).
--
-- NOTE: files in db/ only run when the Postgres volume is initialised. On an existing volume
-- apply this by hand:
--   docker compose exec -T hermes-db psql -U hermes -d hermes -f - < db/5rates.sql

ALTER TABLE hermes.recipient
    ADD COLUMN IF NOT EXISTS published_on TIMESTAMP,
    ADD COLUMN IF NOT EXISTS claimed_on   TIMESTAMP;

CREATE INDEX IF NOT EXISTS recipient_idx5
    ON hermes.recipient (published_on) WHERE published_on IS NOT NULL;

CREATE INDEX IF NOT EXISTS recipient_idx6
    ON hermes.recipient (claimed_on) WHERE claimed_on IS NOT NULL;

CREATE INDEX IF NOT EXISTS recipient_idx7
    ON hermes.recipient (created_on);
