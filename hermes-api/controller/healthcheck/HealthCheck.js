const router = require('express').Router()

module.exports = (app) => {
  router
    .route('/')
    .get((req, res) => {
      res.send({ status: 'OK' })
    }
    )

  app.use('/health', router)
}
