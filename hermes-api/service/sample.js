const db = require('../data/db')

function findAll () {
  return db.getConnection().collection('sample').find().toArray()
}

function insert (customer) {
  return db.getConnection().collection('sample').insertOne(customer)
}

module.exports = { findAll, insert }
