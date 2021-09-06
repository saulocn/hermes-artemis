FROM saulocn/artemismq:2.18.0

COPY broker.xml /artemis/etc/broker.xml

ENV ARTEMIS_BROKER hermes
ENV ARTEMIS_USER hermes
ENV ARTEMIS_PASSWORD pass_hermes

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


ENTRYPOINT /artemis/artemis.sh