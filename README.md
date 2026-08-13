# Pilotage Booking System — Backend

Spring Boot REST API for vessel registration, route selection, booking, coupon payment, pilot assignment, status tracking, dashboards, and reports.

## Requirements

- Java 25
- Docker

The Maven wrapper is included; a separate Maven installation is not required.

## Run the tests

Start Docker, then run:

```bash
./mvnw clean verify
```

The integration suite starts PostgreSQL 18 with Testcontainers, runs Flyway migrations, and exercises the application through its HTTP API. No local database configuration is needed.

## Run the application locally

### 1. Start PostgreSQL 18

Create the database container once:

```bash
docker run --name pilot-db \
  -e POSTGRES_DB=pilot_booking_system \
  -e POSTGRES_USER=pilot \
  -e POSTGRES_PASSWORD=pilot \
  -p 5432:5432 \
  -v pilot-pgdata:/var/lib/postgresql \
  -d postgres:18
```

On later runs:

```bash
docker start pilot-db
```

PostgreSQL 18 uses `/var/lib/postgresql` as the container volume path.

### 2. Create the local profile

Create `src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pilot_booking_system
    username: pilot
    password: pilot
  jpa:
    hibernate:
      ddl-auto: validate

application:
  security:
    jwt:
      issuer: http://localhost:8080
      access-token-ttl: PT15M
      secret: ${JWT_SECRET}
```

The file is ignored by Git. Generate a local Base64-encoded HMAC secret:

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
```

### 3. Start the backend

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway creates or updates the schema automatically.

### 4. Open the API

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Register an OWNER with `POST /api/v1/auth/register`, then log in with `POST /api/v1/auth/login`. In Swagger UI, select **Authorize** and provide the returned access token.

## Create a local ADMIN

There is no default ADMIN account. First register an account through the API so its password is encoded correctly:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "fullName": "Local Administrator",
    "email": "admin@example.com",
    "phone": null,
    "password": "Choose-A-Strong-Password!"
  }'
```

Replace that account's OWNER role with ADMIN in the local database:

```bash
docker exec -i pilot-db \
  psql -U pilot -d pilot_booking_system -v ON_ERROR_STOP=1 \
  -c "begin;
      delete from user_roles
      where user_id = (select id from users where email = 'admin@example.com');

      insert into user_roles (user_id, role_id)
      select u.id, r.id
      from users u
      join roles r on r.name = 'ADMIN'
      where u.email = 'admin@example.com';
      commit;"
```

Log in again to obtain a JWT containing the ADMIN role.

## Run the backend with Docker

Build the image:

```bash
docker build -t pilot-booking-system-backend .
```

Create `.env.docker`:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/pilot_booking_system
SPRING_DATASOURCE_USERNAME=pilot
SPRING_DATASOURCE_PASSWORD=pilot
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
JWT_ISSUER=http://localhost:8080
JWT_ACCESS_TOKEN_TTL=PT15M
JWT_SECRET=replace-with-output-from-openssl
```

Run the image:

```bash
docker run --rm \
  --name pilot-booking-backend \
  --env-file .env.docker \
  -p 8080:8080 \
  pilot-booking-system-backend
```

On Linux, add `--add-host=host.docker.internal:host-gateway` to the command.

## Project documentation

- [Business flow](docs/business-flow.md)
- [API design](docs/api-design.md)
- [Schema design and ERD](docs/schema-design.md)
