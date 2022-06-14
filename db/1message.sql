CREATE SEQUENCE message_seq;

CREATE TABLE hermes.message (
    message_id BIGINT NOT NULL DEFAULT nextval('message_seq') PRIMARY KEY,
    message_text TEXT,
    message_title TEXT,
    content_type TEXT,
	created_on TIMESTAMP NOT NULL DEFAULT NOW()
);