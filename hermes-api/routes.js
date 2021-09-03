
const glob = require('glob')
const path = require('path')

module.exports = (app) => {
  glob(path.join(__dirname, 'controller', '**', '*.js'), {}, (er, files) => {
    if (er) throw er
    files.forEach((file) => require(file)(app))
  })
}
