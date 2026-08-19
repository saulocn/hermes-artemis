-- Without these two, the hottest queries in the system are sequential scans.
--
--   recipient_processed : the enqueuer's 30s query (FIND_NOT_PROCESSED) filters on it.
--   message_id          : every per-message rollup the dashboard shows joins on it.
--
-- recipient_idx1 (recipient_sent, created_on) already covers the fallback query.
--
-- NOTE: files in db/ only run when the Postgres volume is initialised. On an existing volume
-- apply this by hand:
--   docker compose exec -T hermes-db psql -U hermes -d hermes -f - < db/3indexes.sql

CREATE INDEX IF NOT EXISTS recipient_idx2 ON hermes.recipient(recipient_processed);

CREATE INDEX IF NOT EXISTS recipient_idx3 ON hermes.recipient(message_id);
