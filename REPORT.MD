## Part 1. Получение метрик и логов

Для того чтобы все правильно поднялось в **Docker Swarm** надо будет немного поработать с самим кодом сервисов.

Для работы с библиотекой **Micrometer** и экспорта логов надо установить некоторые зависимости в файле **pom.xml**


```
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

```
management.endpoints.web.exposure.include=prometheus
management.endpoint.prometheus.enabled=true
```

В самом коде сервиса импортируем необходимые библиотеки

```
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
```

Регистрируем метрику

```
private final Counter messagesSent;

@Autowired
public QueueProducer(MeterRegistry meterRegistry) {
    this.messagesSent = meterRegistry.counter("rabbitmq_messages_sent");
}
```

Привяжем к конкретному методу инкремент метрики (показано на примере метрики количества отправленных сообщений в **rabbitmq**)

```
messagesSent.increment();
```

Теперь при обращении к эндпоинту **/actuator/prometheus** будем получать список метрик среди которых и самописные.

![custom_metric](screenshots/image1.png)

## Part 2. Визуализация



## Part 3. Отслеживание критических событий


