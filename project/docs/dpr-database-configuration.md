# DPR: SQLite Database Configuration Pattern

**Related ADR**: [ADR-007: Multi-Database Architecture](../adrs/adr-007-multi-database.md)

## Purpose

This Design Pattern Record (DPR) documents the process for adding new SQLite databases and tables to the application using Spring Boot, JPA, and Hibernate.

## Architecture Decision Summary

The application uses **separate SQLite databases** for agent data and memory data, each with independent:
- `DataSource` (HikariCP connection pool)
- `EntityManagerFactory`
- `TransactionManager`
- Entity package scan
- Repository package scan

The agent database is marked `@Primary`; the memory database is secondary. Cross-database transactions require manual coordination. For the full decision rationale and alternatives, see [ADR-007](../adrs/adr-007-multi-database.md).

---

## Adding a New SQLite Database

This document assumes the base architecture is already in place (dependencies configured, JPA auto-configuration disabled).

### Step 1: Create Database Configuration Properties

Create `DataSourceProperties.java` in `com.hdekker.ai_workflow.config`:

```java
package com.hdekker.ai_workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.database")
public class DataSourceProperties {

    @NestedConfigurationProperty
    private Database database = new Database();

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public static class Database {
        private String url = "jdbc:sqlite:/tmp/ai-workflow.db";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
```

**For multiple databases**, add additional nested classes:

```java
@NestedConfigurationProperty
private DomainB domainB = new DomainB();

public static class DomainB {
    private String url = "jdbc:sqlite:/tmp/domain-b.db";
    // getters/setters
}
```

### Step 2: Create Database Configuration Class

Create `DomainBDbConfig.java` (for secondary databases) or update `DatabaseConfig.java` (for primary):

```java
package com.hdekker.ai_workflow.config;

import java.util.Properties;

import javax.sql.DataSource;

import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.hdekker.ai_workflow.database.domainb",
    entityManagerFactoryRef = "domainBEntityManagerFactory",
    transactionManagerRef = "domainBTransactionManager"
)
public class DomainBDbConfig {

    @Autowired
    private DataSourceProperties dataSourceProperties;

    @Bean(name = "domainBDataSource")
    public DataSource domainBDataSource() {
        DataSourceBuilder<?> builder = DataSourceBuilder.create();
        builder.type(HikariDataSource.class);
        builder.driverClassName("org.sqlite.JDBC");
        builder.url(dataSourceProperties.getDomainB().getUrl());
        return builder.build();
    }

    @Bean(name = "domainBEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean domainBEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(domainBDataSource());
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        emf.setPackagesToScan("com.hdekker.ai_workflow.database.domainb");
        emf.setJpaProperties(jpaProperties());
        return emf;
    }

    @Bean(name = "domainBTransactionManager")
    public JpaTransactionManager domainBTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(domainBEntityManagerFactory().getObject());
        return transactionManager;
    }

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
}
```

**Primary Database**: Add `@Primary` annotation to all beans in the primary database configuration.

### Step 3: Update Application Configuration

Add database URL to `application.yml`:

```yaml
app:
  database:
    url: jdbc:sqlite:/path/to/database.db
```

For multiple databases:

```yaml
app:
  database:
    url: jdbc:sqlite:/path/to/primary.db
    domainB:
      url: jdbc:sqlite:/path/to/domain-b.db
```

**Test Configuration**: Update `src/test/resources/application.yml` with test database URLs.

---

## Adding a New Table to a Database

### Step 1: Register Repository to Database Config

Ensure the repository package is included in the `@EnableJpaRepositories` `basePackages` of your database configuration. For a new database domain like `domainb`:

```java
@EnableJpaRepositories(
    basePackages = "com.hdekker.ai_workflow.database.domainb",
    entityManagerFactoryRef = "domainBEntityManagerFactory",
    transactionManagerRef = "domainBTransactionManager"
)
```

### Step 2: Create Entity Class

Create the entity in the appropriate package under the database's entity directory:

```java
package com.hdekker.ai_workflow.database.domainb;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "example_table")
public class ExampleEntity {

    @Id
    private String id;

    private String name;

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

**Important Entity Annotations**:
- `@Entity`: Marks the class as a JPA entity
- `@Table(name = "...")`: Specifies the database table name
- `@Id`: Marks the primary key field
- Field types must be supported by SQLite/JPA

### Step 3: Create Repository Interface

Create a repository interface in the same package:

```java
package com.hdekker.ai_workflow.database.domainb;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExampleRepository extends JpaRepository<ExampleEntity, String> {
    // Custom query methods can be added here
    // Example: List<ExampleEntity> findByName(String name);
}
```

**Repository Configuration**:
- Repositories are automatically registered via `@EnableJpaRepositories` in the database config
- The `basePackages` in `@EnableJpaRepositories` must include the repository's package
- Spring Data JPA provides CRUD operations by default

### Step 4: Verify Package Structure

Ensure the package structure aligns with the database configuration:

```
src/main/java/com/hdekker/ai_workflow/database/
├── domainb/
│   ├── ExampleEntity.java          # Entity in this package
│   └── ExampleRepository.java      # Repository in this package
└── filemetadata/
    ├── FileMetadataEntity.java
    └── FileMetaRepository.java
```

The `setPackagesToScan()` in the `EntityManagerFactory` bean must include this package.

### Step 5: Run Tests

Verify the table is created and accessible:

```bash
./mvnw test -Dtest=ExampleEntityTest -q
```

---

## Key Configuration Details

### JPA Properties Explanation

| Property | Value | Purpose |
|----------|-------|---------|
| `hibernate.id.new_generator_mappings` | `true` | Use JPA 2.0+ ID generators |
| `hibernate.connection.release_mode` | `after_transaction` | Optimize connection handling for SQLite |
| `jakarta.persistence.sharedCache.mode` | `NONE` | Disable shared cache (each DB independent) |
| `hibernate.dialect` | `org.hibernate.community.dialect.SQLiteDialect` | SQLite-specific SQL generation |
| `hibernate.hbm2ddl.auto` | `update` | Auto-create/update schema at startup |
| `spring.jpa.hibernate.naming.physical-strategy` | `PhysicalNamingStrategyStandardImpl` | Consistent table naming |

### Database Bean Naming

For multiple databases, use unique bean names:

| Bean Type | Primary DB | Secondary DB |
|-----------|------------|--------------|
| DataSource | `dataSource` or `primaryDataSource` | `domainBDataSource` |
| EntityManagerFactory | `entityManagerFactory` or `primaryEntityManagerFactory` | `domainBEntityManagerFactory` |
| TransactionManager | `transactionManager` or `primaryTransactionManager` | `domainBTransactionManager` |

The `@EnableJpaRepositories` annotation references these bean names via `entityManagerFactoryRef` and `transactionManagerRef`.

---

## Testing Database Configuration

### Create a Database Test

```java
package com.hdekker.ai_workflow.database.domainb;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
    "app.database.url=jdbc:sqlite::memory:"
})
@Transactional
public class ExampleEntityDatabaseTest {

    @Autowired
    private ExampleRepository exampleRepository;

    @Test
    public void testSaveAndRetrieve() {
        ExampleEntity entity = new ExampleEntity();
        entity.setId("test-id");
        entity.setName("Test Name");

        exampleRepository.save(entity);

        ExampleEntity retrieved = exampleRepository.findById("test-id").orElseThrow();
        assertThat(retrieved.getName()).isEqualTo("Test Name");
    }
}
```

---

## Testing with Multiple Databases

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

---

## Common Issues and Solutions

### Issue: Tables Not Created
**Solution**: Ensure `hibernate.hbm2ddl.auto=update` is set in JPA properties.

### Issue: Wrong Database Used
**Solution**: Verify `@EnableJpaRepositories` `basePackages` matches entity package. Check `@Primary` annotations on correct beans.

### Issue: Connection Errors
**Solution**: SQLite requires `org.sqlite.JDBC` driver. Ensure `hibernate.connection.release_mode=after_transaction`.

### Issue: Cross-Database Entity Mapping
**Solution**: Keep entities in separate packages. Each `EntityManagerFactory` scans only its own package.

---

## References

- [Spring Boot Multi-DataSource](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#data.sql.multi-datasource)
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Hibernate SQLite Dialect](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#configurations-dialect)
- [SQLite JDBC](https://github.com/xerial/sqlite-jdbc)
