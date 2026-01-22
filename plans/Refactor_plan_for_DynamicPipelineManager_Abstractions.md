## Refactor Plan for DynamicPipelineManager (Abstractions)

### Problem Statement
`DynamicPipelineManager` has two constructors where one depends on concrete implementations (`FileSystemRecursiveFileScannerAdapter`, `FileSystemScannerConfig`) rather than abstractions, violating the Dependency Inversion Principle.

### Goals
- Depend on abstractions, not concretions
- Maintain backward compatibility during transition
- Improve testability and maintainability
- Follow SOLID principles

### 1. Create Abstractions

#### FileScanner Interface
```java
package com.hdekker.ai_workflow.files;

import reactor.core.publisher.Flux;
import com.hdekker.ai_workflow.files.FileHistory;

public interface FileScanner {
    Flux<FileHistory> flux();
}
```

#### FileWriter Interface
```java
package com.hdekker.ai_workflow.files;

import java.nio.file.Path;
import java.util.function.Consumer;
import com.hdekker.ai_workflow.prompt.PromptResponse;

public interface FileWriter {
    Consumer<PromptResponse> createPersister(Path outputDirectory);
}
```

### 2. Update Existing Concrete Classes

#### FileSystemRecursiveFileScannerAdapter
- Add `implements FileScanner` to existing class
- No other changes needed since it already has `flux()` method

#### FileSystemFileWriter (New Class)
```java
package com.hdekker.ai_workflow.files;

import java.nio.file.Path;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import com.hdekker.ai_workflow.prompt.PromptResponse;

@Component
public class FileSystemFileWriter implements FileWriter {

    @Override
    public Consumer<PromptResponse> createPersister(Path outputDirectory) {
        return pr -> PromptResponseFileSystemAdapter.createFile(pr, outputDirectory);
    }
}
```

### 3. Refactor DynamicPipelineManager (Gradual Approach)

#### Phase 1: Add New Abstract Constructor
```java
public DynamicPipelineManager(
        FileScanner fileScanner,
        FileWriter fileWriter,
        Path outputDirectory,
        ChatClient chatClient) {
    this.pipelineConfigurator = new PromptPipelineConfigurator(
            fileScanner.flux(),
            chatClient,
            fileWriter.createPersister(outputDirectory));
}
```

#### Phase 2: Keep Original Constructor for Testing
Keep the existing constructor that takes `Flux<FileHistory>`, `ChatClient`, `Consumer<PromptResponse>` for unit tests.

#### Phase 3: Temporarily Keep Concrete Constructor
Keep the constructor with `FileSystemRecursiveFileScannerAdapter` and `FileSystemScannerConfig` during transition.

#### Phase 4: Remove Concrete Constructor
After successful testing, remove the old concrete constructor.

### 4. Update Spring Configuration

#### DynamicPipelineManagerConfiguration
```java
@Configuration
public class DynamicPipelineManagerConfiguration {

    @Autowired
    FileSystemRecursiveFileScannerAdapter fileScanner;

    @Autowired
    FileSystemScannerConfig fileScannerConfig;

    @Autowired
    ChatClient chatClient;

    @Autowired
    FileSystemFileWriter fileWriter;

    @Bean
    public DynamicPipelineManager dynamicPipelineManager() throws IOException {
        Path outputFolderPath = fileScannerConfig.getUrl().getFile().toPath();
        return new DynamicPipelineManager(fileScanner, fileWriter, outputFolderPath, chatClient);
    }
}
```

### 5. Testing Strategy

#### Unit Tests
- Continue using the original `Flux<FileHistory>` constructor
- Mock `FileScanner` and `FileWriter` for new constructor tests

#### Integration Tests
- Test Spring context with new bean definitions
- Verify pipelines are created correctly with abstractions

#### Compatibility Tests
- Ensure old concrete constructor works until removed
- Verify behavior is unchanged during transition

### 6. Implementation Steps

1. Create the two interfaces in `com.hdekker.ai_workflow.files`
2. Create `FileSystemFileWriter` class
3. Update `FileSystemRecursiveFileScannerAdapter` to implement `FileScanner`
4. Add new constructor to `DynamicPipelineManager`
5. Update Spring configuration
6. Run tests to verify functionality
7. Remove old concrete constructor
8. Run final verification with `./mvnw verify`

### 7. Benefits

- **Dependency Inversion**: Class depends on abstractions
- **Testability**: Easier to mock dependencies
- **Flexibility**: Can swap implementations (e.g., for testing or different file systems)
- **Maintainability**: Changes to file system logic don't affect pipeline manager

### 8. Risks & Mitigations

- **Breaking Changes**: Gradual approach maintains compatibility
- **Spring Configuration**: Test thoroughly before removing old constructor
- **Performance**: No expected impact since abstractions are thin

---

This plan ensures a smooth transition from concrete dependencies to abstractions while maintaining system stability.