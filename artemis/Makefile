CONTAINER_NAME=hermes-artemis
IMAGE_NAME=hermes-artemis
NETWORK_MODE= 

build:
	docker build -t ${IMAGE_NAME} .
run: build rm
	docker run -d ${NETWORK_MODE}  --memory="512m" --cpus="1" -p 8161:8161 -p 61616:61616 -p 5672:5672 --name  ${CONTAINER_NAME} ${IMAGE_NAME}
rm:
	docker rm -f ${CONTAINER_NAME} || true