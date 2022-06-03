CREATE SEQUENCE recipient_seq;

CREATE TABLE hermes.recipient (
    recipient_id BIGINT NOT NULL DEFAULT nextval('recipient_seq') PRIMARY KEY,
    recipient_mail VARCHAR(255),
    message_id BIGINT,
    recipient_sent BOOLEAN DEFAULT false,
    recipient_processed BOOLEAN DEFAULT false,
	created_on TIMESTAMP NOT NULL DEFAULT NOW(),
   CONSTRAINT fk_message
      FOREIGN KEY(message_id) 
	  REFERENCES message(message_id)
);


CREATE INDEX recipient_idx1 
ON hermes.recipient(recipient_sent, created_on);