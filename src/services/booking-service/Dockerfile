FROM maven:3.8.1-openjdk-17-slim AS build
WORKDIR /workdir
COPY .mvn /workdir/.mvn
COPY mvnw /workdir/mvnw
COPY pom.xml /workdir/pom.xml
COPY src /workdir/src
RUN unset MAVEN_CONFIG
ENV MAVEN_CONFIG= 
RUN /workdir/mvnw dependency:go-offline
RUN /bin/bash /workdir/mvnw package -DskipTests

FROM openjdk:17-jdk-bullseye
WORKDIR /workdir
COPY wait-for-it.sh /workdir/wait-for-it.sh
COPY --from=build /workdir/target/booking-service-0.0.1-SNAPSHOT.jar /workdir/target/booking-service-0.0.1-SNAPSHOT.jar

RUN curl -LO "https://github.com/grafana/loki/releases/latest/download/promtail-linux-amd64.zip" && \
    unzip promtail-linux-amd64.zip && \
    chmod +x promtail-linux-amd64 && \
    mv promtail-linux-amd64 /usr/local/bin/promtail

COPY promtail-config.yml /workdir/promtail-config.yml

ENV POSTGRES_HOST=database
ENV POSTGRES_PORT=5432
ENV POSTGRES_USER=postgres
ENV POSTGRES_PASSWORD=postgres
ENV POSTGRES_DB=reservations_db
ENV RABBIT_MQ_HOST=rabbit
ENV RABBIT_MQ_PORT=5672
ENV RABBIT_MQ_USER=guest
ENV RABBIT_MQ_PASSWORD=guest
ENV RABBIT_MQ_QUEUE_NAME=messagequeue
ENV RABBIT_MQ_EXCHANGE=messagequeue-exchange
ENV HOTEL_SERVICE_HOST=hotel-service
ENV HOTEL_SERVICE_PORT=8082
ENV PAYMENT_SERVICE_HOST=payment-service
ENV PAYMENT_SERVICE_PORT=8084
ENV LOYALTY_SERVICE_HOST=loyalty-service
ENV LOYALTY_SERVICE_PORT=8085
RUN chmod +x wait-for-it.sh
EXPOSE 8083

COPY script.sh /workdir/script.sh
RUN chmod +x script.sh

CMD [ "/bin/bash", "script.sh" ]