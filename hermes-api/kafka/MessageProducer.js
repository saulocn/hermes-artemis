const kafka = require('kafka-node')
const client = new kafka.KafkaClient({ kafkaHost: 'kafka:29092' })
Producer = kafka.Producer
const producer = new Producer(client)

const sendMessage = message => new Promise((resolve, reject) => {
    const payload = {
        topic: 'messages',
        messages: message._id,
        timestamp: Date.now()
    }
    producer.send([payload], function (err, data) {
        if (err) {
            reject(err)
            return
        }
        resolve(data)
    });
})

module.exports = { sendMessage }