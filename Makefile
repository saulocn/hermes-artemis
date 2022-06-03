build-mailer:
		cd hermes-mailer && ./mvnw clean package
build-api:
		cd hermes-api && ./mvnw clean package
build: build-mailer build-api

rm-compose: 
		docker-compose rm -f

run-compose: rm-compose build
		docker-compose up --build

run-db:
		cd db && make run

run-mq:
		cd artemis && make run

run-api:
		cd hermes-api && make run

run-mailer:
		cd hermes-mailer && make run

rm-db:
		cd db && make rm

rm-mq:
		cd artemis && make rm

rm-api:
		cd hermes-api && make rm

rm-mailer:
		cd hermes-mailer && make rm

rm-all: rm-mailer rm-api rm-db rm-mq

run-all: rm-all run-db run-mq run-mailer run-api