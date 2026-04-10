# Setting Up Vaadin Hilla in an Existing Java Application

This guide documents the process of integrating Vaadin Hilla into an existing Spring Boot Java application, based on the Budget Mainia prototype.

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- Node.js 18+ (for frontend build tools)
- An existing Spring Boot application (or create a new one)

## Step 1: Add Hilla Dependencies to pom.xml

### Parent Configuration

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.3</version>
</parent>
```

### Properties

```xml
<properties>
    <java.version>21</java.version>
    <vaadin.version>25.0.7</vaadin.version>
</properties>
```

### Vaadin Repositories

```xml
<repositories>
    <repository>
        <id>vaadin-prereleases</id>
        <url>https://maven.vaadin.com/vaadin-prereleases</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>

<pluginRepositories>
    <pluginRepository>
        <id>vaadin-prereleases</id>
        <url>https://maven.vaadin.com/vaadin-prereleases</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </pluginRepository>
</pluginRepositories>
```

### Dependency Management (BOM)

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.vaadin</groupId>
            <artifactId>vaadin-bom</artifactId>
            <version>${vaadin.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Core Dependencies

```xml
<dependencies>
    <!-- Vaadin Core -->
    <dependency>
        <groupId>com.vaadin</groupId>
        <artifactId>vaadin</artifactId>
    </dependency>

    <!-- Vaadin Spring Boot Integration -->
    <dependency>
        <groupId>com.vaadin</groupId>
        <artifactId>vaadin-spring-boot-starter</artifactId>
    </dependency>

    <!-- Hilla (Full-stack integration) -->
    <dependency>
        <groupId>com.vaadin</groupId>
        <artifactId>hilla-spring-boot-starter</artifactId>
    </dependency>

    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Dev Tools (optional) -->
    <dependency>
        <groupId>com.vaadin</groupId>
        <artifactId>vaadin-dev</artifactId>
        <optional>true</optional>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-devtools</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### Build Plugins

```xml
<build>
    <defaultGoal>spring-boot:run</defaultGoal>
    <plugins>
        <!-- Spring Boot Maven Plugin -->
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>

        <!-- Vaadin Maven Plugin -->
        <plugin>
            <groupId>com.vaadin</groupId>
            <artifactId>vaadin-maven-plugin</artifactId>
            <version>${vaadin.version}</version>
            <executions>
                <execution>
                    <goals>
                        <goal>build-frontend</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>

        <!-- Optional: Code formatting -->
        <plugin>
            <groupId>com.diffplug.spotless</groupId>
            <artifactId>spotless-maven-plugin</artifactId>
            <version>2.43.0</version>
            <configuration>
                <java>
                    <palantirJavaFormat>
                        <version>2.50.0</version>
                    </palantirJavaFormat>
                </java>
                <typescript>
                    <includes>
                        <include>src/main/frontend/**/*.ts</include>
                        <include>src/main/frontend/**/*.tsx</include>
                    </includes>
                    <excludes>
                        <exclude>src/main/frontend/generated/**</exclude>
                    </excludes>
                    <prettier>
                        <prettierVersion>3.3.3</prettierVersion>
                        <configFile>.prettierrc.json</configFile>
                    </prettier>
                </typescript>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Step 2: Create Frontend Directory Structure

Create the following directory structure:

```
src/main/frontend/
├── index.html
├── themes/
│   └── your-theme-name/
│       ├── styles.css
│       └── theme.json
└── views/
    ├── @index.tsx
    └── @layout.tsx
```

### index.html

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Your App Name</title>
    <style>
      body {
        margin: 0;
        width: 100vw;
        height: 100vh;
      }
      #outlet {
        height: 100%;
      }
    </style>
  </head>
  <body>
    <div id="outlet"></div>
  </body>
</html>
```

### theme.json

```json
{
  "lumoImports": ["typography", "color", "spacing", "badge", "utility"]
}
```

## Step 3: Configure application.properties

```properties
server.port=${PORT:8080}
logging.level.org.atmosphere = warn
spring.mustache.check-template-location = false

# Launch the default browser when starting the application in development mode
vaadin.launch-browser=true

# Package scanning for Vaadin/Hilla
vaadin.allowed-packages = com.vaadin,org.vaadin,com.yourcompany

# JPA datasource initialization (optional)
spring.jpa.defer-datasource-initialization = true
```

## Step 4: Create Main Application Class

```java
package com.yourcompany;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@PWA(name = "Your App", shortName = "YourApp", offlineResources = {})
@StyleSheet(Lumo.STYLESHEET)
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## Step 5: Create Backend Service (Hilla Endpoint)

```java
package com.yourcompany.services;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.BrowserCallable;
import org.springframework.stereotype.Service;

@BrowserCallable
@AnonymousAllowed
@Service
public class HelloWorldService {

    public String sayHello(String name) {
        if (name == null || name.isEmpty()) {
            return "Hello stranger";
        } else {
            return "Hello " + name;
        }
    }
}
```

**Key annotations:**

- `@BrowserCallable`: Makes the service callable from TypeScript/JavaScript
- `@AnonymousAllowed`: Allows unauthenticated access (use appropriate security annotations for production)
- `@Service`: Spring service annotation

## Step 6: Create Frontend Views

### Main Layout (@layout.tsx)

```tsx
import {
  createMenuItems,
  useViewConfig,
} from "@vaadin/hilla-file-router/runtime.js";
import { effect, signal } from "@vaadin/hilla-react-signals";
import { AppLayout, Icon } from "@vaadin/react-components";
import { Suspense, useEffect } from "react";
import { NavLink, Outlet } from "react-router";

const documentTitleSignal = signal("");
effect(() => {
  document.title = documentTitleSignal.value;
});

export default function MainLayout() {
  const currentTitle = useViewConfig()?.title;

  useEffect(() => {
    if (currentTitle) {
      documentTitleSignal.value = currentTitle;
    }
  });

  return (
    <AppLayout>
      <header className="box-border flex flex-col w-full" slot="navbar">
        <div className="flex items-center px-l">
          <h1 className="my-m me-auto text-l">Your App Name</h1>
        </div>
        <nav className="flex overflow-auto px-m py-xs">
          <ul className="flex gap-s list-none m-0 p-0">
            {createMenuItems().map(({ to, title, icon }) => (
              <li key={"li" + to}>
                <NavLink
                  className="flex gap-xs h-m items-center px-s text-body"
                  to={to}
                  key={to}
                >
                  {icon ? <Icon src={icon}></Icon> : <></>}
                  <span className="font-medium text-m whitespace-nowrap">
                    {title}
                  </span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </header>

      <Suspense>
        <Outlet />
      </Suspense>
    </AppLayout>
  );
}
```

### Index View (@index.tsx)

```tsx
import { ViewConfig } from "@vaadin/hilla-file-router/types.js";
import { useSignal } from "@vaadin/hilla-react-signals";
import { Button, Notification, TextField } from "@vaadin/react-components";
import { HelloWorldService } from "Frontend/generated/endpoints.js";

export const config: ViewConfig = {
  menu: { order: 0, icon: "line-awesome/svg/home-solid.svg" },
  title: "Home",
};

export default function HomeView() {
  const name = useSignal("");

  return (
    <>
      <section className="flex p-m gap-m items-end">
        <TextField
          label="Your name"
          onValueChanged={(e) => {
            name.value = e.detail.value;
          }}
        />
        <Button
          onClick={async () => {
            const serverResponse = await HelloWorldService.sayHello(name.value);
            Notification.show(serverResponse);
          }}
        >
          Say hello
        </Button>
      </section>
    </>
  );
}
```

### Additional View Example

```tsx
import { ViewConfig } from "@vaadin/hilla-file-router/types.js";
import { useSignal } from "@vaadin/hilla-react-signals";
import { Grid, GridColumn } from "@vaadin/react-components";

export const config: ViewConfig = {
  menu: { order: 1, icon: "line-awesome/svg/list-solid.svg" },
  title: "Data View",
};

type Item = {
  id: number;
  name: string;
  value: number;
};

export default function DataView() {
  const items = useSignal<Item[]>([
    { id: 1, name: "Item 1", value: 100 },
    { id: 2, name: "Item 2", value: 200 },
  ]);

  return (
    <Grid items={items.value}>
      <GridColumn path="id" header="ID" width="100px" />
      <GridColumn path="name" header="Name" />
      <GridColumn path="value" header="Value" textAlign="end" />
    </Grid>
  );
}
```

## Step 7: TypeScript Configuration

Create `tsconfig.json` in project root:

```json
{
  "_version": "9.1",
  "compilerOptions": {
    "sourceMap": true,
    "jsx": "react-jsx",
    "inlineSources": true,
    "module": "esNext",
    "target": "es2023",
    "moduleResolution": "bundler",
    "strict": true,
    "skipLibCheck": true,
    "experimentalDecorators": true,
    "useDefineForClassFields": false,
    "baseUrl": "src/main/frontend",
    "paths": {
      "@vaadin/flow-frontend": ["generated/jar-resources"],
      "@vaadin/flow-frontend/*": ["generated/jar-resources/*"],
      "Frontend/*": ["*"]
    }
  },
  "include": ["src/main/frontend/**/*"],
  "exclude": ["src/main/frontend/generated/jar-resources/**"]
}
```

## Step 8: Vite Configuration

Create `vite.config.ts`:

```typescript
import { UserConfigFn } from "vite";
import { overrideVaadinConfig } from "./vite.generated";

const customConfig: UserConfigFn = (env) => ({
  // Add custom Vite parameters here
  // https://vitejs.dev/config/
});

export default overrideVaadinConfig(customConfig);
```

## Step 9: Running the Application

### Development Mode

```bash
mvn spring-boot:run
```

Or with Maven wrapper:

```bash
./mvnw spring-boot:run    # Linux/Mac
mvnw.cmd spring-boot:run  # Windows
```

The application will start at http://localhost:8080

### Production Build

```bash
mvnw clean package -Pproduction
```

Run the production JAR:

```bash
java -jar target/your-app-name-1.0-SNAPSHOT.jar
```

## Key Hilla Features

### 1. Type-Safe Backend Calls

Hilla automatically generates TypeScript client code from Java endpoints:

```typescript
import { HelloWorldService } from "Frontend/generated/endpoints.js";

const response = await HelloWorldService.sayHello("World");
```

### 2. File-based Routing

Views in `src/main/frontend/views/` are automatically routed:

- `@index.tsx` → `/`
- `@layout.tsx` → Layout wrapper for all views
- `data-view.tsx` → `/data-view`

### 3. React Components

Use Vaadin components with React:

```tsx
import { Button, TextField, Grid } from "@vaadin/react-components";
```

### 4. Signals for State Management

```tsx
import { useSignal } from "@vaadin/hilla-react-signals";

const count = useSignal(0);
// Usage: count.value
```

## Security Considerations

For production, replace `@AnonymousAllowed` with appropriate security:

```java
import com.vaadin.flow.server.auth.LoginView;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.auth.LoginFailedException;

// For authenticated-only access
@PermitAll
public class SecureService { }

// For specific roles
@RolesAllowed("ADMIN")
public class AdminService { }
```

## Migration from Existing Java Application

1. **Add dependencies** to existing `pom.xml`
2. **Create frontend directory** structure under `src/main/frontend`
3. **Update `application.properties`** with Vaadin settings
4. **Modify main class** to implement `AppShellConfigurator`
5. **Create Hilla endpoints** for existing services
6. **Build frontend views** using React + Vaadin components
7. **Test** with `mvn spring-boot:run`

## Troubleshooting

### Frontend not building

```bash
# Clean and rebuild
mvn clean
mvn vaadin:build-frontend
```

### Port conflicts

```properties
server.port=8081
```

### Generated endpoints not found

```bash
# Regenerate endpoints
mvn hilla:generate
```

## References

- [Hilla Documentation](https://hilla.dev/docs/)
- [Vaadin Documentation](https://vaadin.com/docs/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
