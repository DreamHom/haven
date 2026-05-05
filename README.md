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

```bash
# 1. Configure environment
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=replace-with-a-secret-of-at-least-32-bytes
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# 2. Build
mvn clean install

# 3. Run
mvn spring-boot:run
```

Requires Java 21, a running PostgreSQL instance, and a Kafka broker.

## API Documentation

## Running Tests

```bash
mvn test
```

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
