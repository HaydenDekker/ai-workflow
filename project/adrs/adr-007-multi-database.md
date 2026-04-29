# ADR-007: Multi-Database Architecture with Independent Data Sources

**Status**: Accepted  
**Date**: 2026-04-28  
**Related DPR**: [Database Configuration](../docs/dpr-database-configuration.md)

---

## Context

The application requires separate SQLite databases for different concerns:
- **Agent Database**: Stores agent-related data (sessions, messages, projects, parts)
- **Memory Database**: Stores extraction state and memory-related metadata

Both databases use SQLite with JPA/Hibernate for persistence.

## Decision

The multi-database architecture uses Spring Boot's support for multiple `DataSource` beans with explicit configuration for each database. Each database has its own:
- `DataSource` (HikariCP connection pool)
- `EntityManagerFactory`
- `TransactionManager`
- Entity package scan
- Repository package scan

### Key Decisions

1. **Separate SQLite databases** for agent vs. memory data — ensures clear domain boundaries
2. **Independent transaction management** per database — each DB has its own `TransactionManager`
3. **Explicit `@Primary` on agent database** — agent data is the default data source for the application
4. **Entity package separation** — entities must be in separate packages to avoid cross-database mapping
5. **`hibernate.hbm2ddl.auto=update`** for both databases — automatic schema creation/update at startup

## Alternatives Considered

### Alternative 1: Single Database with Schema Separation

Use one SQLite database with different table prefixes or schemas for agent vs. memory data.

**Rejected**: Doesn't provide true domain isolation; harder to independently scale or replace individual databases.

### Alternative 2: Different Database Engines Per Domain

Use SQLite for agent data and a different engine (e.g., H2, PostgreSQL) for memory data.

**Rejected**: Adds unnecessary operational complexity for current scale; SQLite is sufficient for both domains.

## Consequences

### Benefits

1. **Separation of Concerns**: Different data domains stored in separate databases
2. **Independent Transactions**: Each database has its own transaction manager
3. **Scalability**: Databases can be moved to different servers or replaced independently
4. **Testability**: Each database can be tested independently with separate test databases

### Trade-offs

1. **Complexity**: More configuration code to maintain (duplicated patterns per database)
2. **Distributed Transactions**: Cross-database transactions require manual coordination (not automatically supported)
3. **Boilerplate**: Duplicate configuration patterns for each additional database

### Important Notes

1. **@Primary Annotation**: The primary database (`agent`) is marked with `@Primary` on all its beans. This ensures that when Spring needs a default `DataSource`, `EntityManagerFactory`, or `TransactionManager`, it uses the agent database.
2. **Explicit References**: The `@EnableJpaRepositories` annotation requires explicit specification of `entityManagerFactoryRef` and `transactionManagerRef` to bind repositories to the correct database.
3. **Package Scanning**: Entity packages must be explicitly specified using `setPackagesToScan()` to prevent entities from being mapped to the wrong database.
4. **SQLite Specifics**: 
   - SQLite uses file-based storage
   - Connection release mode should be set to `after_transaction` for optimal performance
   - The SQLite dialect from `hibernate-community-dialects` is required

---

**Author**: AI Workflow Team  
**Last Updated**: 2026-04-28
