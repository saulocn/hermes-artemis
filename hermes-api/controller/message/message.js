const router = require('express').Router()
const service = require('../../service/MessageService')
const {pluck} = require('../../validation/pluck')

module.exports = (app) => {
  router
    .route('/')
    .post(async (req, res) => {
      try {
        const message = pluck(req.body, 'title', 'text', 'recipients', 'content-type')
        await service.sendMessage(message)
        res.status(202).json(message)
      } catch (err) {
        console.error('erro', err)
        res.status(500).json(err)
      }
    }
    )
    .get(async (req, res) => {
      try {
        const messages = await service.findMessages()
        res.status(202).json(messages)
      } catch (err) {
        console.error('erro', err)
        res.status(500).json(err)
      }
    }
    )

    router
    .get('/:id', async (req, res) => {
      try {
        const messages = await service.findMessage(req.params.id)
        res.status(202).json(messages)
      } catch (err) {
        console.error('erro', err)
        res.status(500).json(err)
      }
    }
    )
  app.use('/messages', router)
}
