IMAGE_NAME=hermes-pg
CONTAINER_NAME=hermes-pg

# Rede própria em vez de --network host: a bridge default não resolve nome de container,
# e no Docker Desktop (macOS) o host network fica na VM, invisível para o host e para os
# outros containers. Com rede definida pelo usuário há DNS por nome e as portas publicam.
NETWORK=hermes-net

net:
	@docker network create ${NETWORK} >/dev/null 2>&1 || true

build:
	docker build -t ${IMAGE_NAME} .
run: build rm net
	# 2 CPUs, matching the compose profile. At 0.5 the database saturated its quota and was
	# measured as the joint bottleneck with the broker; raising both together is what took
	# throughput from ~1.200/s to ~2.300/s. That change never reached this path.
	docker run -d --network ${NETWORK} -p 5432:5432  --memory="1g" --cpus="2" --name  ${CONTAINER_NAME} ${IMAGE_NAME}
rm: 
	docker rm -f ${CONTAINER_NAME} || true