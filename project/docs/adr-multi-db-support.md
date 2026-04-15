# ADR: Multiple SQLite Database Configuration with Spring Boot

## Status

Accepted

## Context

This application requires separate SQLite databases for different concerns:
- **Agent Database**: Stores agent-related data (sessions, messages, projects, parts)
- **Memory Database**: Stores extraction state and memory-related metadata

Both databases use SQLite with JPA/Hibernate for persistence. The architecture must support multiple data sources with independent entity management, transaction management, and repository configurations.

## Decision

### Architecture Overview

The multi-database architecture uses Spring Boot's support for multiple `DataSource` beans with explicit configuration for each database. Each database has its own:
- `DataSource` (HikariCP connection pool)
- `EntityManagerFactory`
- `TransactionManager`
- Entity package scan
- Repository package scan

### Component Structure

```
src/main/java/com/hdekker/qdrant/
├── config/
│   ├── DataSourceProperties.java      # Custom properties binding
│   ├── AgentDbConfig.java             # Agent DB configuration (PRIMARY)
│   └── MemoryDbConfig.java            # Memory DB configuration
├── entity/
│   ├── agent/                         # Agent entities (Message, Session, etc.)
│   └── memory/                        # Memory entities (ExtractionState)
├── repository/
│   ├── agent/                         # Agent repositories
│   └── memory/                        # Memory repositories
└── adapter/
    └── SqliteMemorySource.java        # Memory source adapter
```

### Dependency Declarations (pom.xml)

```xml
<properties>
    <java.version>21</java.version>
</properties>

<dependencies>
    <!-- Spring Boot Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- SQLite JDBC Driver -->
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.51.3.0</version>
    </dependency>

    <!-- Hibernate SQLite Dialect -->
    <dependency>
        <groupId>org.hibernate.orm</groupId>
        <artifactId>hibernate-community-dialects</artifactId>
    </dependency>
</dependencies>
```

### Configuration Properties (application.properties)

```properties
# SQLite Database URLs
app.datasource.agent.url=jdbc:sqlite:path/to/agent.db
app.datasource.memory.url=jdbc:sqlite:path/to/memory.db

# Disable default JPA auto-configuration to prevent conflicts
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

### DataSourceProperties.java

Custom properties class that binds nested configuration for each database:

```java
@Component
@ConfigurationProperties(prefix = "app.datasource")
public class DataSourceProperties {
    
    @NestedConfigurationProperty
    private Agent agent;
    
    @NestedConfigurationProperty
    private Memory memory;
    
    // Getters and setters for Agent and Memory inner classes
    // Each inner class has a single 'url' property
}
```

### AgentDbConfig.java (Primary Database)

The agent database is marked as `@Primary` since it is the default database for the application:

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "com.hdekker.qdrant.repository.agent",
    entityManagerFactoryRef = "agentEntityManagerFactory",
    transactionManagerRef = "agentTransactionManager"
)
public class AgentDbConfig {
    
    @Bean(name = "agentDataSource")
    @Primary
    public DataSource agentDataSource() {
        DataSourceBuilder<?> builder = DataSourceBuilder.create();
        builder.type(HikariDataSource.class);
        builder.driverClassName("org.sqlite.JDBC");
        builder.url(dataSourceProperties.getAgent().getUrl());
        return builder.build();
    }
    
    @Bean(name = "agentEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean agentEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(agentDataSource());
        emf.setJpaVendorAdapter(memoryJpaVendorAdapter());
        emf.setPackagesToScan("com.hdekker.qdrant.entity.agent");
        emf.setJpaProperties(memoryJpaProperties());
        return emf;
    }
    
    @Bean(name = "agentTransactionManager")
    @Primary
    public PlatformTransactionManager agentTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(agentEntityManagerFactory().getObject());
        return transactionManager;
    }
}
```

### MemoryDbConfig.java (Secondary Database)

The memory database is a secondary data source without `@Primary` annotation:

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "com.hdekker.qdrant.repository.memory",
    entityManagerFactoryRef = "memoryEntityManagerFactory",
    transactionManagerRef = "memoryTransactionManager"
)
public class MemoryDbConfig {
    
    @Bean(name = "memoryDataSource")
    public DataSource memoryDataSource() {
        DataSourceBuilder<?> builder = DataSourceBuilder.create();
        builder.type(HikariDataSource.class);
        builder.driverClassName("org.sqlite.JDBC");
        builder.url(dataSourceProperties.getMemory().getUrl());
        return builder.build();
    }
    
    @Bean(name = "memoryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean memoryEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(memoryDataSource());
        emf.setJpaVendorAdapter(memoryJpaVendorAdapter());
        emf.setPackagesToScan("com.hdekker.qdrant.entity.memory");
        emf.setJpaProperties(memoryJpaProperties());
        return emf;
    }
    
    @Bean(name = "memoryTransactionManager")
    public PlatformTransactionManager memoryTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(memoryEntityManagerFactory().getObject());
        return transactionManager;
    }
}
```

### JPA Properties

Both databases use identical JPA/Hibernate configuration:

```java
private Properties jpaProperties() {
    Properties props = new Properties();
    props.put("hibernate.id.new_generator_mappings", "true");
    props.put("hibernate.connection.release_mode", "after_transaction");
    props.put("jakarta.persistence.sharedCache.mode", "NONE");
    props.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
    props.put("hibernate.hbm2ddl.auto", "update");
    props.put("spring.jpa.hibernate.naming.physical-strategy", PhysicalNamingStrategyStandardImpl.class.getName());
    return props;
}
```

Key settings:
- `hibernate.id.new_generator_mappings=true`: Use JPA 2.0+ ID generators
- `hibernate.connection.release_mode=after_transaction`: Optimize connection handling for SQLite
- `sharedCache.mode=NONE`: Disable shared cache (each DB is independent)
- `hibernate.dialect`: Use SQLite-specific Hibernate dialect
- `hibernate.hbm2ddl.auto=update`: Automatically create/update database schema at startup (required for SQLite to create tables)
- `spring.jpa.hibernate.naming.physical-strategy`: Use standard physical naming strategy for consistent table naming

### Entity Package Separation

Entities must be in separate packages to avoid cross-database mapping:

```java
// Agent entities - stored in agent database
@Entity
@Table(name = "message")
public class Message {
    @Id
    private String id;
    // ...
}

// Memory entities - stored in memory database
@Entity
@Table(name = "extraction_state")
public class ExtractionState {
    @Id
    private String id;
    // ...
}
```

### Repository Configuration

Repositories are auto-wired to the correct database based on their package:

```java
// Agent repository - uses agentDataSource
@Repository
public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findBySessionId(String sessionId);
}

// Memory repository - uses memoryDataSource
@Repository
public interface ExtractionStateRepository extends JpaRepository<ExtractionState, String> {
    Optional<ExtractionState> findById(String id);
}
```

### Service Layer Usage

Services can inject repositories from both databases:

```java
@Service
public class SomeService {
    
    private final MessageRepository messageRepository;      // Agent DB
    private final ExtractionStateRepository stateRepository; // Memory DB
    
    public SomeService(MessageRepository messageRepository,
                      ExtractionStateRepository stateRepository) {
        this.messageRepository = messageRepository;
        this.stateRepository = stateRepository;
    }
}
```

## Consequences

### Benefits

1. **Separation of Concerns**: Different data domains stored in separate databases
2. **Independent Transactions**: Each database has its own transaction manager
3. **Scalability**: Databases can be moved to different servers or replaced independently
4. **Testability**: Each database can be tested independently with separate test databases

### Trade-offs

1. **Complexity**: More configuration code to maintain
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

## Testing Strategy

### Test Classification

| Test Type | Annotation | Scope | Database | Use Case |
|-----------|-----------|-------|----------|----------|
| Unit Test | `@ExtendWith(MockitoExtension.class)` | Isolated | None | Business logic, mocked repositories |
| Data JPA Test | `@DataJpaTest` | Repository layer | H2 in-memory | Entity mapping, repository queries |
| Integration Test | `@SpringBootTest` | Full context | SQLite (production) | End-to-end workflows |

### Database Choice for Tests

- **H2 for Repository Tests**: `@DataJpaTest` uses H2 in-memory by default
  - Fast execution (no file I/O)
  - Automatic cleanup between tests
  - Compatible with standard JPA operations
  - Does not load custom multi-DB configuration

- **SQLite for Integration Tests**: Full application context tests
  - Production-parity database
  - Tests multi-DB configuration
  - Tests actual file-based persistence
  - Tag with `@Tag("integration")` for selective execution

### Configuration Strategy

- Production: Custom multi-DB config excludes `HibernateJpaAutoConfiguration`
- Tests (`@DataJpaTest`): Does not load main application context, uses auto-configured H2
- Integration Tests (`@SpringBootTest`): Use production SQLite configuration

### Test Tagging Convention

```java
// Unit test (mocked)
@ExtendWith(MockitoExtension.class)
public class ServiceTest { ... }

// Repository test (H2, fast)
@DataJpaTest
public class RepositoryTest { ... }

// Integration test (SQLite, full context)
@Tag("integration")
@SpringBootTest
public class WorkflowIntegrationTest { ... }
```

Run integration tests separately:
```bash
./mvnw verify                          # All tests
./mvnw test                            # Unit + Data JPA tests only
./mvnw verify -Dit.test=*IntegrationTest  # Integration tests only
```

## How to Add a Third Database

1. Create a new configuration class (e.g., `ThirdDbConfig.java`)
2. Define `DataSource`, `EntityManagerFactory`, and `TransactionManager` beans
3. Use `@EnableJpaRepositories` with unique bean names
4. Add a nested class in `DataSourceProperties` for the new database URL
5. Create entity and repository packages for the new domain
6. Do NOT add `@Primary` unless this should become the default database

## References

- Spring Boot Multi-DataSource: https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#data.sql.multi-datasource
- Spring Data JPA: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/
- Hibernate SQLite Dialect: https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#configurations-dialect
