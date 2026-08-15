IMAGE_NAME=hermes-enqueuer
CONTAINER_NAME=hermes-enqueuer

# Rede própria em vez de --network host: a bridge default não resolve nome de container,
# e no Docker Desktop (macOS) o host network fica na VM, invisível para o host e para os
# outros containers. Com rede definida pelo usuário há DNS por nome e as portas publicam.
NETWORK=hermes-net

net:
	@docker network create ${NETWORK} >/dev/null 2>&1 || true

package:
		./mvnw clean package

build-docker: package
		docker build -t ${IMAGE_NAME} --file ./src/main/docker/Dockerfile.jvm .
rm: 
	docker rm -f ${CONTAINER_NAME} || true

run: build-docker rm net
		docker run -d --memory="1g" --cpus="1" --network ${NETWORK} -p 8082:8082 --env-file ./sample.env ${EXTRA_ENV} --name  ${CONTAINER_NAME} ${IMAGE_NAME}