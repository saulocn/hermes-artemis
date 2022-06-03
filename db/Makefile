IMAGE_NAME=hermes-pg
CONTAINER_NAME=hermes-pg

build:
	docker build -t ${IMAGE_NAME} .
run: build  rm
	docker run -d -p 5432:5432  --memory="1g" --cpus="0.5" --name  ${CONTAINER_NAME} ${IMAGE_NAME}
rm: 
	docker rm -f ${CONTAINER_NAME} || true