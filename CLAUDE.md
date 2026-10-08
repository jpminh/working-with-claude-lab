# CLAUDE.md

- ops-dashboard: Spring Boot 3.2 (Java 17) read-only REST API under `/api`, plus a vanilla-JS page in `src/main/resources/static/` (no build step, no framework).
- Run: `docker compose up -d db && ./mvnw spring-boot:run`, or without Docker `SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run` (in-memory H2). Open http://localhost:8080.
- Test: `./mvnw test` (JUnit 5 + H2 + MockMvc) and `npm test` (Jest + jsdom). Both must be green before a change is done; report both counts.
- Backend lives in `src/main/java/com/marlowefinch/ops/`: controllers call repositories that use plain SQL through Spring JDBC. No JPA. Schema and seed are Flyway migrations in `src/main/resources/db/migration/`.
- "Today" is pinned to 2026-09-21 (`ops.today`, `ClockConfig.java`). Never use the real clock; default ranges are the last 30 days ending today.
- Frontend: everything is inside `initApp(document, fetchImpl)` in `app.js` so it runs in the browser and in Jest. Charts are inline SVG; colours belong in `style.css`, not in JavaScript.
- Every element id in `index.html` must be registered in `REGISTERED_IDS` in `src/test/javascript/setup/loadApp.js`, or `harness.test.js` fails.
- Write tests first. Frontend tests go in `src/test/javascript/*.test.js` and load the page with `loadApp()`.
- Tickets are in `docs/tickets/`. Where a ticket and the chat disagree, the ticket file wins.
- pom.xml dependencies are frozen. Any change needs a CHG ticket.
