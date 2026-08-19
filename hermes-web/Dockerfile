FROM node:22-alpine AS build
WORKDIR /app
# Copy manifests first so the dependency layer survives source-only changes.
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
# Shipped as a template, not as conf.d/default.conf: the image entrypoint runs envsubst
# over /etc/nginx/templates, which is what lets the API upstream differ between the
# compose network and the standalone `make run-all` path. See the comments in the file.
ENV HERMES_API_UPSTREAM=hermes-api:8080
COPY default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
