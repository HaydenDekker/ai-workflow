# Plan: Add SQLite Database Configuration

## Overview
Add single SQLite database configuration for `FileMetadataEntity` with foundation for future multi-database support (per ADR).

---

## Phase 1: Setup & Verification

- [x] Add Maven dependencies to `pom.xml`:
  - `sqlite-jdbc` (3.51.3.0)
  - `hibernate-community-dialects`
- [x] Run existing tests to establish baseline: `./mvnw test -q`
- [x] Verify all tests pass before making changes

## Phase 2: Disable JPA Auto-Configuration

- [ ] Update `application.yml`:
  - Add `HibernateJpaAutoConfiguration` to `spring.autoconfigure.exclude`
- [ ] Run any existing test to confirm no regression from disabling auto-config

## Phase 3: Create Database Configuration

- [ ] Create `DataSourceProperties.java` in `com.hdekker.ai_workflow.config`:
  - Bind `app.database.*` properties
  - Single nested `Database` class with `url` property (for now, single DB)
- [ ] Create `DatabaseConfig.java` in `com.hdekker.ai_workflow.config`:
  - `@Primary` DataSource bean (HikariCP, SQLite JDBC)
  - `@Primary` EntityManagerFactory bean (scan `com.hdekker.ai_workflow.database`)
  - `@Primary` TransactionManager bean
  - JPA properties (SQLite dialect, connection release mode, etc.)
- [ ] Update `application.yml`:
  - Add `app.database.url=jdbc:sqlite:/tmp/ai-workflow.db`
- [ ] Run tests to verify configuration doesn't break existing functionality

## Phase 4: Create Entity Test

- [ ] Create test class `FileMetadataDatabaseTest.java` in `src/test/java/com/hdekker/ai_workflow/database/filemetadata/`:
  - Use `@DataJpaTest` with custom config
  - Test: save dummy `FileMetadataEntity` (url + hash)
  - Test: read back entity by URL
  - Test: delete entity
  - Use in-memory SQLite or temp file
- [ ] Run test - expect failure (entity not yet wired to database)
- [ ] Fix entity if needed (add proper annotations, getters/setters visibility)
- [ ] Run test again - expect success

---

## Future Work (ADR Multi-DB Support)

Once single SQLite DB is working:

- [ ] Split entities into `agent/` and `memory/` packages
- [ ] Create second database (`MemoryDbConfig`)
- [ ] Move/refactor entities to appropriate databases

---

## Test Commands

| Phase | Command |
|-------|---------|
| Baseline | `./mvnw test -q` |
| After Phase 2 | `./mvnw test -q` |
| After Phase 3 | `./mvnw test -q` |
| Phase 4 | `./mvnw test -Dtest=FileMetadataDatabaseTest -q` |
