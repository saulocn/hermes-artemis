IMAGE_NAME=hermes-cache
CONTAINER_NAME=hermes-cache

build:
	docker build -t ${IMAGE_NAME} .
run: build  rm
	docker run -d -p 6379:6379  --memory="1g" --cpus="0.5" --name  ${CONTAINER_NAME} ${IMAGE_NAME}
rm: 
	docker rm -f ${CONTAINER_NAME} || true