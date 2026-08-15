CONTAINER_NAME=hermes-web
IMAGE_NAME=hermes-web
API_UPSTREAM=hermes-api:8080

# Rede própria em vez de --network host: a bridge default não resolve nome de container,
# e no Docker Desktop (macOS) o host network fica na VM, invisível para o host e para os
# outros containers. Com rede definida pelo usuário há DNS por nome e as portas publicam.
NETWORK=hermes-net

net:
	@docker network create ${NETWORK} >/dev/null 2>&1 || true

build:
	docker build -t ${IMAGE_NAME} .
run: build rm net
	docker run -d --network ${NETWORK} --memory="512m" --cpus="0.5" -p 8090:80 -e HERMES_API_UPSTREAM=${API_UPSTREAM} --name  ${CONTAINER_NAME} ${IMAGE_NAME}
rm:
	docker rm -f ${CONTAINER_NAME} || true
