# Hermes — build, execução e testes.
#
# Requisitos: Docker (tudo que sobe container), JDK 17+ (o build usa o ./mvnw de cada
# módulo, não precisa de Maven instalado) e Node 22 para a tela.
#
# `make` sem argumento lista os alvos.

.DEFAULT_GOAL := help

COMPOSE := docker-compose
# Sem profile, o compose só alcança os serviços do profile ativo — containers do outro
# broker sobrevivem e seguem lendo o mesmo Postgres, corrompendo medição e teste.
ALL_PROFILES := COMPOSE_PROFILES=artemis,rabbit

.PHONY: help build build-api build-enqueuer build-mailer \
	test test-java test-api test-enqueuer test-mailer test-web build-web test-e2e \
	up up-rabbit down ps logs \
	run-compose run-compose-rabbit rm-compose \
	require-stack require-compose bench bench-rabbit loadtest \
	run-db run-cache run-mq run-rabbit run-api run-enqueuer run-mailer run-web \
	rm-db rm-cache rm-mq rm-rabbit rm-api rm-enqueuer rm-mailer rm-web rm-net \
	run-all run-all-rabbit rm-all clean clean-images purge ps-all

help: ## Lista os alvos disponíveis
		@grep -hE '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  %-22s %s\n", $$1, $$2}'

# ---------------------------------------------------------------- build

build-mailer: ## Empacota o hermes-mailer
		cd hermes-mailer && ./mvnw clean package

build-api: ## Empacota o hermes-api
		cd hermes-api && ./mvnw clean package

build-enqueuer: ## Empacota o hermes-enqueuer
		cd hermes-enqueuer && ./mvnw clean package

build: build-mailer build-enqueuer build-api ## Empacota os três módulos Java

# ---------------------------------------------------------------- testes
# As classes *IT rodam no failsafe (`verify`) porque sobem container; `mvn test`
# sozinho continua rápido e sem Docker.

test-api: ## Testa o hermes-api
		cd hermes-api && ./mvnw -B verify

test-enqueuer: ## Testa o hermes-enqueuer (sobe Postgres e RabbitMQ)
		cd hermes-enqueuer && ./mvnw -B verify

test-mailer: ## Testa o hermes-mailer (sobe Postgres, Redis, Artemis e RabbitMQ)
		cd hermes-mailer && ./mvnw -B verify

test-java: test-api test-enqueuer test-mailer ## Testa os três módulos Java

# `npm test` é vitest em watch e não termina sozinho; --run é o que o torna utilizável
# em script e em CI.
test-web: ## Testa a tela (vitest, modo CI)
		cd hermes-web && npm install && npm test -- --run

build-web: ## Compila a tela (tsc + vite build)
		cd hermes-web && npm install && npm run build

# Sem `timeout` aqui: ele não existe no macOS, e a versão anterior deste alvo falhava
# instantaneamente com "command not found" — derrubando a stack antes de rodar um teste
# sequer, e reportando isso como falha de health check.
test-e2e: ## Sobe a stack, roda o E2E e derruba
		@$(MAKE) up
		@echo "Aguardando a stack ficar saudável (o Artemis leva ~95s)..."
		@ok=0; for i in $$(seq 1 75); do \
			if curl -sf -o /dev/null http://localhost:8080/q/health/ready; then ok=1; break; fi; \
			sleep 2; \
		done; \
		if [ $$ok -eq 0 ]; then \
			echo "A stack não ficou saudável em 150s."; \
			$(MAKE) down; \
			exit 1; \
		fi
		@# The cd is scoped to a subshell on purpose. It used to sit on this same continued line
		@# as the teardown, so `$(MAKE) down` ran inside hermes-web/ — which has no `down` target.
		@# The stack was never torn down, and because that error was the last command it became
		@# the target's exit code, masking whatever Playwright had reported.
		@(cd hermes-web && npx playwright test); \
		exit_code=$$?; \
		$(MAKE) down; \
		exit $$exit_code

test: test-java test-web ## Roda tudo que é teste automatizado

# ---------------------------------------------------------------- compose

up: ## Sobe a stack com Artemis em background
		$(COMPOSE) up -d --build

up-rabbit: ## Sobe a stack com RabbitMQ em background
		COMPOSE_PROFILES=rabbit $(COMPOSE) up -d --build

down: ## Derruba tudo dos dois profiles, com volumes
		$(ALL_PROFILES) $(COMPOSE) down -v --remove-orphans

ps: ## Estado dos containers dos dois profiles
		$(ALL_PROFILES) $(COMPOSE) ps

logs: ## Segue o log dos serviços (SERVICE=mailer para filtrar)
		$(ALL_PROFILES) $(COMPOSE) logs -f $(SERVICE)

rm-compose: down ## Alias de `down`

run-compose: rm-compose build ## Sobe com Artemis em primeiro plano
		$(COMPOSE) up --build

run-compose-rabbit: rm-compose build ## Sobe com RabbitMQ em primeiro plano
		COMPOSE_PROFILES=rabbit $(COMPOSE) up --build

# ---------------------------------------------------------------- medição
# Os três exigem a stack de pé. Sem a guarda abaixo o bench.sh falharia lá dentro,
# num `docker compose exec psql`, com um erro que não diz o que fazer.

require-stack: # interno: sem ## de propósito, não é alvo para o usuário chamar
		@curl -sf -o /dev/null http://localhost:8080/q/health/ready || { \
			echo "A stack não está no ar."; \
			echo "Suba com 'make up' (Artemis) ou 'make up-rabbit' (RabbitMQ) e tente de novo."; \
			exit 1; \
		}

# O bench.sh consulta o banco por `docker compose exec`, então precisa do projeto compose —
# com os containers avulsos de `make run-all` a API responde mas o script não acha o serviço.
require-compose: require-stack # interno
		@test -n "$$($(ALL_PROFILES) $(COMPOSE) ps -q 2>/dev/null)" || { \
			echo "O benchmark roda sobre o docker compose, e não há projeto compose no ar."; \
			echo "Se você subiu com 'make run-all', derrube com 'make rm-all' e use 'make up'."; \
			exit 1; \
		}

bench: require-compose ## Benchmark de dreno no Artemis (10k destinatários)
		./bench/bench.sh artemis 100 100

bench-rabbit: require-compose ## Benchmark de dreno no RabbitMQ (10k destinatários)
		COMPOSE_PROFILES=rabbit ./bench/bench.sh rabbit 100 100

# Usa o k6 local se existir; senão cai na imagem oficial. SCENARIO=smoke|ramp|soak|burst.
loadtest: require-stack ## Teste de carga da ingestão com k6
		@if command -v k6 >/dev/null 2>&1; then \
				k6 run -e SCENARIO=$${SCENARIO:-ramp} bench/load-test.js; \
		else \
				docker run --rm -i -v "$$PWD/bench:/bench" \
						-e BASE_URL=http://host.docker.internal:8080 \
						-e SCENARIO=$${SCENARIO:-ramp} \
						grafana/k6 run /bench/load-test.js; \
		fi

# ---------------------------------------------------------------- serviços avulsos

run-db: ## Sobe só o Postgres
		cd db && make run

run-cache: ## Sobe só o Redis
		cd cache && make run

run-mq: ## Sobe só o Artemis
		cd artemis && make run

run-rabbit: ## Sobe só o RabbitMQ
		cd rabbit && make run

run-api: ## Sobe só o hermes-api
		cd hermes-api && make run

run-enqueuer: ## Sobe só o hermes-enqueuer
		cd hermes-enqueuer && make run

run-mailer: ## Sobe só o hermes-mailer
		cd hermes-mailer && make run

run-web: ## Sobe só a tela
		cd hermes-web && make run

rm-db: ## Remove o container do Postgres
		cd db && make rm

rm-cache: ## Remove o container do Redis
		cd cache && make rm

rm-mq: ## Remove o container do Artemis
		cd artemis && make rm

rm-rabbit: ## Remove o container do RabbitMQ
		cd rabbit && make rm

rm-api: ## Remove o container do hermes-api
		cd hermes-api && make rm

rm-enqueuer: ## Remove o container do hermes-enqueuer
		cd hermes-enqueuer && make rm

rm-mailer: ## Remove o container do hermes-mailer
		cd hermes-mailer && make rm

rm-web: ## Remove o container da tela
		cd hermes-web && make rm

rm-net: ## Remove a rede dos containers avulsos
		-docker network rm hermes-net 2>/dev/null || true

rm-all: rm-mailer rm-enqueuer rm-api rm-web rm-db rm-mq rm-rabbit rm-cache rm-net ## Remove todos os containers avulsos

# ---------------------------------------------------------------- limpeza
# Há dois caminhos de execução (compose e containers avulsos) e eles não se enxergam:
# `down` só alcança o compose, `rm-all` só os avulsos. `clean` cobre os dois.

clean: down rm-all ## Remove containers e rede dos dois caminhos (compose e avulsos)
		@echo "Containers e rede removidos."

clean-images: clean ## O acima, mais as imagens construídas pelo projeto
		-@docker images --format '{{.Repository}}:{{.Tag}}' \
			| grep -E '^(saulocn/)?hermes-' \
			| xargs -r docker rmi -f 2>/dev/null || true
		@echo "Imagens do projeto removidas (as imagens base continuam em cache)."

purge: clean-images ## O acima, mais volumes órfãos. Irreversível: apaga os dados do Postgres
		docker volume prune -f
		@echo "Volumes órfãos removidos."

ps-all: ## Lista containers do projeto nos dois caminhos, inclusive parados
		@docker ps -a --filter name=hermes --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}'

run-all: rm-all run-db run-mq run-cache run-mailer run-enqueuer run-api run-web ## Sobe todos os serviços avulsos com Artemis

# O sample.env aponta para o Artemis; aqui o -e sobrepõe o --env-file. Sem o
# MQ_MAIL_ADDRESS os apps usariam jms.queue.MailQueue, que não existe no RabbitMQ.
RABBIT_APP_ENV = -e MQ_HOST=hermes-rabbit -e MQ_MAIL_ADDRESS=/queues/MailQueue
# O hermes-api não fala AMQP, mas lê profundidade de fila da API de gestão do broker.
RABBIT_API_ENV = -e BROKER_KIND=rabbit -e BROKER_HOST=hermes-rabbit -e BROKER_MGMT_PORT=15672

run-all-rabbit: rm-all run-db run-rabbit run-cache ## Idem, com RabbitMQ
		cd hermes-mailer && make run EXTRA_ENV='$(RABBIT_APP_ENV)'
		cd hermes-enqueuer && make run EXTRA_ENV='$(RABBIT_APP_ENV)'
		cd hermes-api && make run EXTRA_ENV='$(RABBIT_API_ENV)'
		$(MAKE) run-web
