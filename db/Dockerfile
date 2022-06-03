FROM postgres

ENV POSTGRES_USER hermes
ENV POSTGRES_PASSWORD pass_hermes
ENV POSTGRES_DB hermes

ADD 0schema_creation.sql /docker-entrypoint-initdb.d/0schema_creation.sql
ADD 1message.sql /docker-entrypoint-initdb.d/1message.sql
ADD 2recipient.sql /docker-entrypoint-initdb.d/2recipient.sql