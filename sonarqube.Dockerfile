FROM maven:3.9.8-eclipse-temurin-21
COPY src /app/src
COPY pom.xml /app
WORKDIR /app
CMD mvn clean verify sonar:sonar \
    -Dsonar.projectKey=dragonia-be \
    -Dsonar.projectName='dragonia-be' \
    -Dsonar.host.url=http://localhost:9000 \
    -Dsonar.token=${SONARQUBE_PROJECT_TOKEN}
