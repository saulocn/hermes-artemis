IMAGE_NAME=hermes-pg
CONTAINER_NAME=hermes-pg

build:
	docker build -t ${IMAGE_NAME} .
run: rm
	docker run -d -p 5432:5432 --name  ${CONTAINER_NAME} ${IMAGE_NAME}
rm:
	docker rm -f ${CONTAINER_NAME} || true