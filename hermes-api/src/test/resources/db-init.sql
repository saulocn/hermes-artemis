-- Dev Services runs this before Hibernate's schema export. The hermes schema is created
-- here (mirroring db/0schema_creation.sql) so Hibernate only has to manage tables.
CREATE SCHEMA IF NOT EXISTS hermes;
