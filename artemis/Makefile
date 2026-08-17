CONTAINER_NAME=hermes-artemis
IMAGE_NAME=hermes-artemis
NETWORK_MODE= 

# Rede própria em vez de --network host: a bridge default não resolve nome de container,
# e no Docker Desktop (macOS) o host network fica na VM, invisível para o host e para os
# outros containers. Com rede definida pelo usuário há DNS por nome e as portas publicam.
NETWORK=hermes-net

net:
	@docker network create ${NETWORK} >/dev/null 2>&1 || true

build:
	docker build -t ${IMAGE_NAME} .
run: build rm net
	# 1g/2cpu, matching the compose profile. Artemis survived the same load test only because its
	# heap is JVM-governed and MaxRAMPercentage does read the cgroup — it was never protected by
	# having more memory, just by knowing how much it had.
	docker run -d --network ${NETWORK} --memory="1g" --cpus="2" -e ARTEMIS_PASSWORD=pass_hermes -p 8161:8161 -p 61616:61616 -p 5672:5672 --name  ${CONTAINER_NAME} ${IMAGE_NAME}
rm:
	docker rm -f ${CONTAINER_NAME} || true