FROM node:14

# Criando o diretório da app
WORKDIR /usr/src/app

# Instalando dependências
COPY package*.json ./
COPY . .

RUN npm install

EXPOSE 8080
CMD [ "node", "index" ]