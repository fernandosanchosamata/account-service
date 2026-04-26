# account-service

Microservicio encargado de la gestion de cuentas bancarias y tarjetas de debito.
Permite crear cuentas, consultar productos por cliente, consultar saldos, realizar
depositos, retiros, compensaciones y administrar tarjetas de debito asociadas a
cuentas principales o secundarias.

## Tecnologias

- Java 17
- Spring Boot 3.2.4
- Spring WebFlux
- Spring Data MongoDB Reactive
- Spring Data Redis Reactive
- RxJava 3
- Reactor Adapter
- Spring Cloud Config
- Eureka Client
- Resilience4j Circuit Breaker
- Springdoc OpenAPI
- Maven
- JUnit 5, Mockito, Testcontainers
- JaCoCo
- Spotless
- Checkstyle
- Docker

## Puerto

La aplicacion expone HTTP en:

```text
http://localhost:8083
```

## OpenAPI

Swagger UI:

```text
http://localhost:8083/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8083/v3/api-docs
```

## Levantar sin Docker

Requisitos locales:

- Java 17
- Maven
- MongoDB en `localhost:27017`
- Redis en `localhost:6379`
- Config Server opcional en `localhost:8888`
- Eureka opcional en `localhost:8761`

Comando:

```powershell
cd .\account-service
mvn spring-boot:run
```

Tambien se puede generar el jar y ejecutarlo:

```powershell
cd .\account-service
mvn clean package
java -jar .\target\account-service-0.0.1-SNAPSHOT.jar
```

## Levantar con Docker

Primero se debe levantar la infraestructura desde el repositorio o ruta de
infraestructura:

```text
https://github.com/fernandosanchosamata/infra
```

Ejemplo:

```powershell
cd .\infra
docker compose up -d
```

La infraestructura debe dejar disponibles MongoDB, Redis, Config Server y Eureka.
La forma recomendada es ejecutar `account-service` dentro del mismo entorno Docker
Compose para que use nombres de servicio en la red Docker.

Construir imagen de `account-service`:

```powershell
cd .\account-service
mvn clean package
docker build -t account-service .
```

Si se ejecuta como contenedor separado con `docker run`, usar
`host.docker.internal` para conectarse a la infraestructura publicada en el host:

```powershell
docker run --rm --name account-service -p 8083:8083 `
  -e SPRING_CONFIG_IMPORT=optional:configserver:http://host.docker.internal:8888 `
  -e SPRING_DATA_MONGODB_URI=mongodb://host.docker.internal:27017/ntt_account `
  -e SPRING_DATA_REDIS_HOST=host.docker.internal `
  -e SPRING_DATA_REDIS_PORT=6379 `
  -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://host.docker.internal:8761/eureka/ `
  account-service
```

Si se ejecuta dentro del mismo Docker Compose que la infraestructura, usar nombres
de servicio:

```text
MongoDB: mongodb://mongodb:27017/ntt_account
Redis: redis:6379
Config Server: http://config-server:8888
Eureka: http://eureka-server:8761/eureka/
```

## Tests

Unitarios:

```powershell
cd .\account-service
mvn test
```

Integracion:

```powershell
cd .\account-service
mvn verify
```

Los tests de integracion viven en:

```text
src/integration-test/java
```

Usan Testcontainers, por lo que requieren Docker disponible.

## Formato y Checkstyle

Aplicar formato:

```powershell
cd .\account-service
mvn spotless:apply
```

Validar Checkstyle:

```powershell
cd .\account-service
mvn checkstyle:check
```

## JaCoCo

Generar reporte:

```powershell
cd .\account-service
mvn test jacoco:report
```

Ruta del reporte HTML:

```text
target/site/jacoco/index.html
```

## MongoDB

Este servicio usa la base de datos:

```text
ntt_account
```

URI por defecto:

```text
mongodb://localhost:27017/ntt_account
```

Colecciones principales:

```text
accounts
debit_cards
```

Ingresar con Mongo Shell:

```powershell
mongosh
use ntt_account
show collections
db.accounts.find().pretty()
db.debit_cards.find().pretty()
```

El proyecto sigue el enfoque logico de `database per service`: cada microservicio
trabaja sobre su propia base de datos MongoDB. Puede estar en el mismo servidor
fisico de MongoDB durante desarrollo, pero cada servicio mantiene sus datos
aislados en una base independiente.

## Reglas de cuentas y Redis

Las reglas de negocio de cuentas se definen en `application.yml`, bajo:

```text
account.rules.definitions
```

Estas reglas indican limites y condiciones por combinacion de tipo de cuenta,
tipo de cliente y perfil, por ejemplo:

- maximo de cuentas permitidas
- transacciones gratuitas
- comision por transaccion extra
- mantenimiento mensual
- dia permitido para cuentas a plazo fijo
- requisito de tarjeta de credito activa

Cuando el servicio necesita una regla, primero intenta leerla desde Redis. Si no
existe en cache, la carga desde el YAML y la persiste en Redis como dato maestro
cacheado usando el TTL configurado en:

```text
account.rules.cache-ttl
```
