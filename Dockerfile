FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY lib/mysql-connector-j-26.10.0.jar /app/lib/mysql-connector-j-26.10.0.jar
COPY backend/src /app/backend/src
COPY frontend /app/frontend

RUN mkdir -p out && \
    javac -cp "lib/mysql-connector-j-26.10.0.jar" \
    -d out $(find backend/src -name "*.java")

ENV FRONTEND_DIR=frontend

CMD ["java", "-cp", "out:lib/mysql-connector-j-26.10.0.jar", "com.laundry.Main"]