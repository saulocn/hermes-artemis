# Esta imagem base só existe para amd64. Em host arm64 (Apple Silicon) ela roda sob
# emulação: medido, o broker leva ~90s para ficar saudável, contra segundos do RabbitMQ,
# que é nativo. A plataforma é declarada no docker-compose.yml (platform: linux/amd64) e
# não aqui — o BuildKit desaconselha constante no FROM porque impede build multi-arch.
# Ver a seção "Plataforma" no README.
FROM saulocn/artemismq:2.22.0

COPY broker.xml /artemis/etc/broker.xml

ENV ARTEMIS_BROKER=hermes
ENV ARTEMIS_USER=hermes
# A senha vem em tempo de execução (docker-compose.yml e artemis/Makefile), não como ENV:
# aqui ela ficaria gravada numa camada da imagem, que é o que o aviso
# SecretsUsedInArgOrEnv do BuildKit aponta.

# Web Server
EXPOSE 8161 \
    # JMX Exporter
    9404 \
    # CORE,MQTT,AMQP,HORNETQ,STOMP,OPENWIRE
    61616 \
    # HORNETQ,STOMP
    5445 \
    # AMQP
    5672 \
    # MQTT
    1883 \
    #STOMP
    61613

CMD [ "/bin/sh", "/artemis/artemis.sh" ]