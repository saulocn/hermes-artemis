build-mailer:
		cd hermes-mailer && ./mvnw clean package
build-api:
		cd hermes-api && ./mvnw clean package
build: build-mailer build-api