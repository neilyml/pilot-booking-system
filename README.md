# Pilotage Booking & Coupon Payment Management System

A Spring Boot REST API for vessel registration, pilotage booking, coupon payment, pilot assignment, operational reporting, and status tracking. The application uses PostgreSQL 18, Flyway migrations, Spring Data JPA, Spring Security, symmetric HMAC-signed JWTs, and Springdoc OpenAPI.

## Documentation

- [Business flow](docs/business-flow.md)
- [API design](docs/api-design.md)
- [Schema design and ERD](docs/schema-design.md)

## Prerequisites

- Java 25
- Docker with a running daemon
- A POSIX-compatible shell

The Maven wrapper is included, so a separate Maven installation is not required.

Verify the main tools:

```bash
java -version
docker version
./mvnw -version
```

## Start PostgreSQL 18

Create the local database container:

```bash
docker run --name pilot-db \
  -e POSTGRES_DB=pilot_booking_system \
  -e POSTGRES_USER=pilot \
  -e POSTGRES_PASSWORD=pilot \
  -p 5432:5432 \
  -v pilot-pgdata:/var/lib/postgresql \
  -d postgres:18
```

PostgreSQL 18 uses `/var/lib/postgresql` as the volume mount target for this image. Do not use the older `/var/lib/postgresql/data` path.

For an existing container:

```bash
docker start pilot-db
docker ps --filter name=pilot-db
```

Flyway creates and validates the application schema automatically when the application starts. It does not create the PostgreSQL database itself.

## Configure the local profile

`application-local.yml` is intentionally ignored by Git because it contains local environment configuration. Create `src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: ${LOCAL_DB_URL:jdbc:postgresql://localhost:5432/pilot_booking_system}
    username: ${LOCAL_DB_USERNAME}
    password: ${LOCAL_DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate

application:
  security:
    jwt:
      issuer: ${JWT_ISSUER:http://localhost:8080}
      access-token-ttl: ${JWT_ACCESS_TOKEN_TTL:PT15M}
      secret: ${JWT_SECRET}
```

Generate a Base64-encoded 256-bit HMAC secret for local development:

```bash
openssl rand -base64 32
```

Keep the generated value out of source control, logs, screenshots, and shared shell scripts.

## Run the application

Export the required local values and activate the profile:

```bash
export LOCAL_DB_USERNAME=pilot
export LOCAL_DB_PASSWORD=pilot
export JWT_SECRET='replace-with-your-base64-secret'

./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The local database URL, issuer, and token lifetime use the defaults shown above. Override them only when needed:

```bash
export LOCAL_DB_URL='jdbc:postgresql://localhost:5432/pilot_booking_system'
export JWT_ISSUER='http://localhost:8080'
export JWT_ACCESS_TOKEN_TTL='PT15M'
```

You may also pass Spring properties through the Maven CLI:

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="\
--spring.datasource.username=pilot \
--spring.datasource.password=pilot \
--application.security.jwt.secret=replace-with-your-base64-secret"
```

Environment variables are preferable for secrets because command-line values may be visible in shell history and process listings.

## API documentation

After the application starts:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Use `POST /api/v1/auth/login` to obtain a JWT. In Swagger UI, click **Authorize** and paste only the token value into the bearer-token field.

## Create a local administrator

There is no administrator bootstrap in production code and no public administrator-registration endpoint. For local development, register an owner through the API so the password is encoded by the application, then replace its OWNER role with ADMIN in the local database.

Register the account:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "fullName": "Local Administrator",
    "email": "admin@example.com",
    "phone": null,
    "password": "replace-with-a-strong-password"
  }'
```

Assign only the ADMIN role:

```bash
docker exec -i pilot-db \
  psql -U pilot -d pilot_booking_system \
  -c "delete from user_roles
      where user_id = (select id from users where email = 'admin@example.com');
      insert into user_roles (user_id, role_id)
      select u.id, r.id
      from users u
      cross join roles r
      where u.email = 'admin@example.com'
        and r.name = 'ADMIN'
      on conflict do nothing;"
```

This operation changes only the specified local account. Log in after assigning the role because roles are embedded in the JWT when it is issued.

## Run the test suite

The integration tests use RestTestClient, Testcontainers, and a real PostgreSQL 18 container. Docker must be running.

```bash
./mvnw clean verify
```

The build runs Flyway migrations against the Testcontainers database and validates Hibernate mappings against the resulting schema.

## Useful local commands

Check the application process:

```bash
curl -i http://localhost:8080/v3/api-docs
```

Open a PostgreSQL shell:

```bash
docker exec -it pilot-db psql -U pilot -d pilot_booking_system
```

Stop local services without deleting data:

```bash
docker stop pilot-db
```

The named volume `pilot-pgdata` preserves the PostgreSQL data when the container is stopped or removed.
