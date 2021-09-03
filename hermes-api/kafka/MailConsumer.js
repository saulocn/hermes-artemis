const kafka = require('kafka-node')
const client = new kafka.KafkaClient({kafkaHost: 'kafka:29092'})
Consumer = kafka.Consumer

const consumer = new Consumer( client, 
    [{ topic: 'mail', partition: 0 }], 
    {autoCommit: false, groupId:"node-mail-consumer"}
    )

consumer.on('message', function (incomingMessage) {
    const message = incomingMessage.value
    console.log("Mail Received:", message)
})

consumer.on('error', function(err) {
    console.log('error', err)
})

process.on('SIGINT', function() {
    consumer.close(true, function() {
        process.exit()
    })
})