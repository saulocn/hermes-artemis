IMAGE_NAME=hermes-api
CONTAINER_NAME=hermes-api

package:
		./mvnw clean package

build-docker: package
		docker build -t ${IMAGE_NAME} --file ./src/main/docker/Dockerfile.jvm .
rm: 
	docker rm -f ${CONTAINER_NAME} || true

run: build-docker rm
		docker run  --memory="1g" --cpus="1" --network host --env-file ./sample.env --name  ${CONTAINER_NAME} ${IMAGE_NAME}