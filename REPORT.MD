## Part 1. Получение метрик и логов

Для того чтобы все правильно поднялось в **Docker Swarm** надо будет немного поработать с самим кодом сервисов.

Для работы с библиотекой **Micrometer** и экспорта логов надо установить некоторые зависимости в файле **pom.xml**


```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

В **src/main/resources/application.properties** каждого проекта добавим следующее

```properties
management.endpoints.web.exposure.include=prometheus
management.endpoint.prometheus.enabled=true
```

В самом коде сервиса импортируем необходимые библиотеки

```properties
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
```

Регистрируем метрику

```java
private final Counter messagesSent;

@Autowired
public QueueProducer(MeterRegistry meterRegistry) {
    this.messagesSent = meterRegistry.counter("rabbitmq_messages_sent");
}
```

Привяжем к конкретному методу инкремент метрики (показано на примере метрики количества отправленных сообщений в **rabbitmq**)

```properties
messagesSent.increment();
```

Теперь при обращении к эндпоинту **/actuator/prometheus** будем получать список метрик среди которых и самописные.

![custom_metric](screenshots/image1.png)

Укажем в **application.properties** следующую строку, чтобы приложение выгружало свои логи в файл

```properties
logging.file.name=app.log
```

**Promtail** для выгрузки логов в **Loki** будем устанавиливать в контейнер с каждым сервисом потому что так проще. Для этого модифицируем **Dockerfile** для каждого сервиса примерно следующим образом: скачиваем promtail, копируем конфиг и запускаем вместе с сервисом

```dockerfile
RUN curl -LO "https://github.com/grafana/loki/releases/latest/download/promtail-linux-amd64.zip" && \
    unzip promtail-linux-amd64.zip && \
    chmod +x promtail-linux-amd64 && \
    mv promtail-linux-amd64 /usr/local/bin/promtail

COPY promtail-config.yml /workdir/promtail-config.yml
.
.
.
COPY script.sh /workdir/script.sh
RUN chmod +x script.sh
CMD [ "/bin/bash", "script.sh" ]
```

В **script.sh**:
```bash
#!/bin/bash
./wait-for-it.sh database:5432 -s -t 0 -- java -jar target/booking-service-0.0.1-SNAPSHOT.jar &
promtail -config.file=/workdir/promtail-config.yml
```

**promtail-config.yml**
```yml
server:
  http_listen_port: 9080

clients:
  - url: "http://loki:3100/loki/api/v1/push"

scrape_configs:
  - job_name: "booking-service-logs"
    static_configs:
      - labels:
          job: "booking-service"
          host: "booking-service-container"
          __path__: "/workdir/app.log"

```

Теперь в контейнерах настроен агент **Promtail**, который будет собирать логи, которые **Spring** упаковывает в **app.log** и отправлять их в **Loki** контейнер.


Добавим сервис **Loki** в **docker-compose.yml**, настройки он не потребует.
```yml
loki:
  image: grafana/loki
  ports:
    - "3100:3100"
```

Создадим сервис для **prometheus**. Прокинем порты, стоять он будет только на **manager** ноде.
```yml
prometheus:
  image: prom/prometheus
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml
  ports:
    - "9090:9090"
  deploy:
    placement:
      constraints:
        - "node.role == manager"
```

**prometheus.yml**
```yml
global:
  scrape_interval: 15s # собираем раз в 15 сек

scrape_configs:
  - job_name: 'prometheus' # собирает метрики самого себя
    static_configs:
      - targets: ['prometheus:9090']

  - job_name: 'node-exporter' # hw метрики каждой ноды
    static_configs:
      - targets: ['192.168.100.100:9100']
      - targets: ['192.168.100.101:9100']
      - targets: ['192.168.100.102:9100']

  - job_name: 'micrometer' # самописные метрики
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['report-service:8086']
      - targets: ['booking-service:8083']
      - targets: ['gateway-service:8087']
      - targets: ['session-service:8081']
```

Сервис для **node exporter`а**. Взял чисто из документации на **docker hub**, т.к. там писали, что его надо очень аккуратно настраивать, чтобы он не отдавал метркии контейнера.
```yml
node-exporter:
  image: prom/node-exporter
  command:
    - '--path.rootfs=/host'
  network_mode: host
  pid: host
  restart: unless-stopped
  volumes:
    - '/:/host:ro,rslave'
  ports:
    - "9100:9100"
  deploy:
    mode: global
```

Сервис для **blackbox-exporter**.
```yml
  blackbox-exporter:
    image: prom/blackbox-exporter
    command:
      - '--config.file=/etc/blackbox/blackbox.yml'
    volumes:
      - './blackbox.yml:/etc/blackbox/blackbox.yml'
    ports:
      - "9115:9115"
    deploy:
      placement:
        constraints:
          - "node.role == manager"
```

Конфиг **prometheus**
```yml
  - job_name: 'blackbox'
    metrics_path: /probe
    params:
      module: [http_2xx]
    static_configs:
      - targets:
        - http://gateway-service:8087
        - http://session-service:8081
        - 142.250.190.78 # указываем актуальный ipv4 адрес google.com, т.к. по дефолту blackbox использует 6 версию, а она из контейнеров отказывается работать 

    relabel_configs: # стандартная конструкция
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: blackbox-exporter:9115
```

**blackbox.yml**. Указываем что ходим методом **GET** и ожидаем код 200.
```yml
modules:
  http_2xx:
    prober: http
    timeout: 10s
    http:
      valid_status_codes: [200]
      method: GET
      no_follow_redirects: false
      fail_if_ssl: false
      fail_if_not_ssl: false
```

Сервис для **cadvisor**.
```yml
  cadvisor:
    image: gcr.io/cadvisor/cadvisor
    deploy:
      mode: global # располагается на каждой ноде, т.к. нужен доступ к системным каталогам докера
    ports:
      - target: 8080 
        published: 8100
        protocol: tcp
        mode: host # используем сеть host, вместо ingress, т.к. без этого не работает)
    volumes:
      - /:/rootfs:ro
      - /var/run:/var/run:ro
      - /sys:/sys:ro
      - /var/lib/docker:/var/lib/docker:ro
      - /dev/disk:/dev/disk:ro
```

Джоба в **prometheus.yml**
```yml
- job_name: 'cadvisor'
  static_configs:
    - targets: ['192.168.100.100:8100']
    - targets: ['192.168.100.101:8100']
    - targets: ['192.168.100.102:8100']
```

Проверим доступность метрик на порту 9090:

![prometheus](screenshots/image2.png)

![node_exporter](screenshots/image3.png)

![micrometer](screenshots/image4.png)

![blackbox](screenshots/image5.png)

![cadvisor](screenshots/image6.png)

## Part 2. Визуализация

Добавим сервис **grafana** и прокинем 3000 порт на хост машину.

```yml
grafana:
  image: grafana/grafana
  ports:
    - "3000:3000"
```

Добавим **Data sources**

![datasources](screenshots/image7.png)

| Метрика     | Запрос      |
|-------------|-------------|
| количество нод    | `count(up{job="node-exporter"})`    |
| количество контейнеров    | `count(container_cpu_usage_seconds_total{image!=""})`    |
| использование CPU по сервисам | `rate(container_cpu_usage_seconds_total{container_label_com_docker_swarm_service_name!="", container_label_com_docker_compose_project="src"}[1m])` |
| использование CPU по ядрам и узлам | `sum(rate(node_cpu_seconds_total{job="node-exporter", mode!="idle"}[1m])) by (instance, cpu)` |
| затраченная RAM | `node_memory_MemTotal_bytes{job="node-exporter"} / (1024*1024*1024) - node_memory_MemFree_bytes{job="node-exporter"} / (1024*1024*1024)` |
| доступная память | `sum(node_filesystem_free_bytes{job="node-exporter"} / (1024*1024*1024)) by (instance)` |
| количество CPU | `count(node_cpu_seconds_total{mode="idle", job="node-exporter"}) by (instance)` |
| доступность google.com | `probe_success{job="blackbox", instance="142.250.190.78"}` |
| количество отправленных сообщений в rabbitmq | `rabbitmq_messages_sent_total{job="micrometer"}` |
| количество обработанных сообщений в rabbitmq | `rabbitmq_messages_processed_total{job="micrometer"}` |
| количество бронирований | `hotel_bookings_total{job="micrometer"}` |
| количество полученных запросов на gateway | `requests_received_gateway_total{job="micrometer"}` |
| количество полученных запросов на авторизацию пользователей | `user_authorization_requests_total{job="micrometer"}` |
| логи приложения | ``` {filename="/workdir/app.log"} \|=`` ``` |

![grafana1](screenshots/image8.png)

![grafana2](screenshots/image9.png)

## Part 3. Отслеживание критических событий


