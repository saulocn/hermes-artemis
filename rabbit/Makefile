CONTAINER_NAME=hermes-rabbit
IMAGE_NAME=hermes-rabbit
NETWORK_MODE=

# Rede própria em vez de --network host: a bridge default não resolve nome de container,
# e no Docker Desktop (macOS) o host network fica na VM, invisível para o host e para os
# outros containers. Com rede definida pelo usuário há DNS por nome e as portas publicam.
NETWORK=hermes-net

net:
	@docker network create ${NETWORK} >/dev/null 2>&1 || true

build:
	docker build -t ${IMAGE_NAME} .
# 1g/2cpu, the same the compose profile gives the broker. This path was left at 512m/1cpu when
# the compose numbers were raised after measurement, and that is where the OOM kill happened.
# The 1g must also match total_memory_available_override_value in rabbitmq.conf.
run: build rm net
	docker run -d --network ${NETWORK} --memory="1g" --cpus="2" -p 5672:5672 -p 15672:15672 --name  ${CONTAINER_NAME} ${IMAGE_NAME}
rm:
	docker rm -f ${CONTAINER_NAME} || true
