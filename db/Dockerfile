FROM postgres

ENV POSTGRES_USER hermes
ENV POSTGRES_PASSWORD pass_hermes
ENV POSTGRES_DB hermes

# Every .sql in this directory, by wildcard rather than one ADD per file.
#
# Naming them individually meant adding a migration was two steps, and forgetting the second one
# failed silently: db/5rates.sql existed in the repository, was never copied into the image, and
# the first sign of it was `column "published_on" does not exist` at runtime — from an API that
# had compiled, passed its unit tests, and passed its integration tests, because those build
# their schema from the entities instead of from these files.
#
# The wildcard keeps the numeric prefix meaningful: docker-entrypoint-initdb.d runs them in
# filename order, so 0..9 is the migration order.
ADD *.sql /docker-entrypoint-initdb.d/