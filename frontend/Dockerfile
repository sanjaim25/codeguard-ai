FROM node:22-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM nginxinc/nginx-unprivileged:1.28-alpine
ENV PORT=8080
ENV BACKEND_URL=http://backend:8080
ENV NGINX_ENVSUBST_FILTER=^(BACKEND_URL|PORT)$
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 8080
