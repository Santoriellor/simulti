# Stage 1: Build Angular app
FROM node:20 AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build -- --configuration=production

# Serve with nginx as an unprivileged user (uid 101, listens on 8080).
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY --from=build /app/dist/frontend /usr/share/nginx/html
COPY nginx/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
