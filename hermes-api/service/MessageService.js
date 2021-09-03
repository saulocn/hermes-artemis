const db = require('../data/db')
const ObjectId = require('mongodb').ObjectId;
const producer = require('../kafka/MessageProducer')
const COLLECTION_NAME = 'messages'

const sendMessage = async (message) => {
    message.sent = false
    message.created = new Date()
    console.log(message)
    await db.getConnection().collection(COLLECTION_NAME).insertOne(message)
    producer.sendMessage(message)
    return message
}

const findMessages = async () => {
    return await db.getConnection().collection(COLLECTION_NAME).find().toArray()
}

const findMessage = async (id) => {
    return await db.getConnection().collection(COLLECTION_NAME).find({_id : new ObjectId(id)}).toArray()
}

module.exports = { sendMessage, findMessages, findMessage }
