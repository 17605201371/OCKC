FROM node:22-alpine AS web
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/src/ src/
COPY --from=web /src/frontend/dist/ src/main/resources/static/
RUN mvn -B verify
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S community && adduser -S community -G community
WORKDIR /app
COPY --from=build /src/backend/target/course-community-0.1.0.jar /app/app.jar
USER community
ENV SERVER_ADDRESS=0.0.0.0 PORT=8080
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
