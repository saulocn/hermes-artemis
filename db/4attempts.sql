-- Makes a failing delivery distinguishable from one that is merely queued.
--
-- The two booleans cannot express it. A send that throws rolls the claim back, so the row
-- returns to recipient_processed = true, recipient_sent = false — exactly what a message waiting
-- in the queue looks like. The dashboard counted both as "in flight", forever.
--
-- The counter is written from the consumer in its own transaction, so it survives the rollback
-- that put the message back on the queue.
--
-- NOTE: files in db/ only run when the Postgres volume is initialised. On an existing volume
-- apply this by hand:
--   docker compose exec -T hermes-db psql -U hermes -d hermes -f - < db/4attempts.sql

ALTER TABLE hermes.recipient
    ADD COLUMN IF NOT EXISTS recipient_attempts INT NOT NULL DEFAULT 0;

-- Partial: the dashboard only ever asks for rows that have failed at least once, and those are
-- a small minority of a large table.
CREATE INDEX IF NOT EXISTS recipient_idx4
    ON hermes.recipient(recipient_attempts)
    WHERE recipient_attempts > 0;
