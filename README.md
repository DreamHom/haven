# DreamHomes Haven — The Engine 🏠

> The secure backend powering DreamHomes. Every listing, every inspection request, every offer — it runs through here.

## What This Is

## Tech Stack

- **Java 21** (LTS)
- **Spring Boot 3.3.5** — Web, Data JPA, Security, Validation
- **PostgreSQL** — primary data store
- **Spring Kafka** — event streaming
- **JJWT 0.12.x** — JWT issuance and verification
- **Lombok** — boilerplate reduction
- **JUnit 5 + Spring Security Test + Spring Kafka Test** — TDD toolkit

## Architecture

[UML and system design diagrams — coming soon]

## Modules

## Development Philosophy

TDD-first. Tests are written before implementation. No exceptions.

## Getting Started

Requires Java 21 and Docker (Docker Desktop on macOS).

```bash
# 1. Start local infra (Postgres + Kafka, persistent volumes)
docker compose up -d

# 2. Configure environment (defaults match docker-compose.yml)
export JWT_SECRET=replace-with-a-secret-of-at-least-32-bytes

# 3. Run the app
mvn spring-boot:run
```

Stop infra with `docker compose down`. Wipe data with `docker compose down -v`.

## API Documentation

## Running Tests

```bash
mvn test     # fast unit tests (no infra required)
mvn verify   # adds integration tests (Testcontainers — needs Docker running)
```

Unit tests follow the `*Test` / `*Tests` naming convention and run via Surefire.
Integration tests follow the `*IT` convention, extend `AbstractPostgresIT`,
and run via Failsafe — each gets a real Postgres container with no boilerplate.

## Project Structure

```
haven/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/dreamhomes/haven/
│   │   │   └── DreamhomesHavenApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/dreamhomes/haven/
│           └── DreamhomesHavenApplicationTests.java
└── README.md
```

## License

See [LICENSE](LICENSE).

Built for Moniepoint DreamDev Bootcamp 2026.
