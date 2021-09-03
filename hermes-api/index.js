const express = require('express')
const chalk = require('chalk')
const app = express()
const pack = require('./package')
const mode = process.env.NODE_ENV || 'dev'
const config = require(`./config/${mode}.json`)[mode]
const db = require('./data/db')
require('./kafka/MessageConsumer')
require('./kafka/MailConsumer')

db.connect()
  .then(() => {
    require('./routes')(app)

    app.use(express.json())
    app.use(express.urlencoded({
      extended: true
    }))

    app.listen(config.port, () => {
      console.log(chalk.yellow('.......................................'))
      console.log(chalk.green(mode))
      console.log(chalk.green(`Port:\t\t${config.port}`))
      console.log(chalk.green(`Mode:\t\t${config.mode}`))
      console.log(chalk.green(`App version:\t${pack.version}`))
      console.log(chalk.green('database connection is established'))
      console.log(chalk.yellow('.......................................'))
    })
  })
