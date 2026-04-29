# ADR-008: Memory Extraction

## Context

This application implements an automated memory extraction system that processes conversation data from the agent database and stores structured memories in a vector store (Qdrant). The system uses Large Language Models (LLMs) to extract meaningful memories from raw conversation data at multiple levels of granularity.

Key requirements:
- Extract memories from conversation data at MESSAGE, TURN, and SESSION levels
- Use LLMs to identify and structure meaningful memories
- Store extracted memories in Qdrant with metadata (memtype, keywords)
- Track extraction state for incremental processing
- Maintain separation between agent data (conversations) and memory data (extraction state)
- Support both synchronous and queued LLM processing

## Decision

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Spring Boot Application                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                        Controller Layer                                │  │
│  │                                                                        │  │
│  │  MemoryController (REST API)                                          │  │
│  │  ├── POST /api/memories (add raw memory)                              │  │
│  │  ├── POST /api/memories/store-thoughts (extract & store)             │  │
│  │  ├── POST /api/memories/search (semantic search)                     │  │
│  │  └── GET /api/memories (get all memories)                             │  │
│  └──────────────────────────────┬────────────────────────────────────────┘  │
│                                 │                                             │
│                                 │ Uses                                        │
│                                 ▼                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                        Use Case Layer (Application Services)          │  │
│  │                                                                        │  │
│  │  ┌────────────────────────────┐    ┌──────────────────────────────┐  │  │
│  │  │ StoreMemoriesUseCase       │    │ LlmMemoryExtractorService    │  │  │
│  │  │ (implements StoreMemoriesPort)  │ (implements MemoryExtractorService)│ │
│  │  │                            │    │                              │  │  │
│  │  │ storeMemories(request)     │───▶│ extractMemories(request)     │  │  │
│  │  │                            │    │                              │  │  │
│  │  └────────┬───────────────────┘    └──────────┬───────────────────┘  │  │
│  │           │                                   │                       │  │
│  │           │ Uses                              │ Uses                  │  │
│  │           ▼                                   ▼                       │  │
│  │  ┌──────────────────────────────────────────────────────────────┐  │  │
│  │  │              Ports (Interface Abstractions)                   │  │  │
│  │  │                                                               │  │  │
│  │  │  StoreMemoriesPort       │  MemoryExtractorService           │  │  │
│  │  │  (interface)             │  (interface)                      │  │  │
│  │  └──────────────────────────┴───────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                        Adapter Layer (Infrastructure)                 │  │
│  │                                                                        │  │
│  │  ┌────────────────────────────┐    ┌──────────────────────────────┐  │  │
│  │  │ SqliteMemorySource         │    │ QdrantService                │  │  │
│  │  │ (reads agent DB)           │    │ (wraps VectorStore port)     │  │  │
│  │  │                            │    │                              │  │  │
│  │  │ getRequest(id, level)      │    │ addMemory(content)           │  │  │
│  │  │                            │    │ addMemoryWithMemtype(...)    │  │  │
│  │  └────────────────────────────┘    │ searchMemories(query, topK)  │  │  │
│  │                                    └──────────┬───────────────────┘  │  │
│  │                                               │                       │  │
│  │                                               │ Uses                  │  │
│  │                                               ▼                       │  │
│  │  ┌──────────────────────────────────────────────────────────────┐  │  │
│  │  │         VectorStore (Spring AI Port)                         │  │  │
│  │  │         Auto-configured QdrantVectorStore bean               │  │  │
│  │  └──────────────────────────────────────────────────────────────┘  │  │
│  │                                                                      │  │
│  │  ┌────────────────────────────┐    ┌──────────────────────────────┐ │  │
│  │  │ DatabaseMemoryAdapter      │    │ DefaultLlmAdapter            │ │  │
│  │  │ (adapts StoreMemoriesPort) │    │ (adapts LlmAdapter interface)│ │  │
│  │  │                            │    │                              │ │  │
│  │  │ extractAndStore(session)   │    │ processRequest(request)      │ │  │
│  │  └────────────────────────────┘    └──────────────────────────────┘ │  │
│  │                                                                      │  │
│  │  ┌──────────────────────────────────────────────────────────────┐  │  │
│  │  │              LlmAdapterQueue (Reactive Queue)                 │  │  │
│  │  │                                                               │  │  │
│  │  │  enqueue(request) ──▶ Flux ──▶ LlmAdapter.processRequest()   │  │  │
│  │  └──────────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                        Data Models                                    │  │
│  │                                                                        │  │
│  │  MemoryExtractRequest (contexts: List<ExtractionContext>, level)     │  │
│  │  ExtractionContext (id, content, metadata)                           │  │
│  │  ExtractionLevel (MESSAGE | TURN | SESSION)                          │  │
│  │  MemoryExtractorResult (memories: List<ExtractedMemory>)             │  │
│  │  ExtractedMemory (content, memtype, keywords)                        │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
                                   │ Uses
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         External Services                                    │
├──────────────────────────────┬──────────────────────────┬──────────────────┤
│    Agent Database (SQLite)   │   Memory Database (SQLite)│  Qdrant Vector  │
│                              │                          │  Store           │
│  Message, Session, Part      │  ExtractionState         │  localhost:6334  │
│  (com.hdekker.qdrant.entity) │  (com.hdekker.qdrant.    │  Collection:     │
│                              │   entity.memory)         │  memories        │
└──────────────────────────────┴──────────────────────────┴──────────────────┘
                                   │
                                   │ HTTP POST /v1/chat/completions
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         LLM Service (llama.cpp)                             │
│                         http://192.168.2.103:8081                           │
│                         Model: gemma3:4b-it-q8_0                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Structure

```
src/main/java/com/hdekker/qdrant/
├── config/
│   ├── OpenAiChatConfig.java           # ChatModel, ChatClient, OpenAiApi beans
│   ├── OpenAiEmbeddingConfig.java      # EmbeddingModel, OpenAiApi beans
│   ├── AgentDbConfig.java              # Agent database (primary)
│   └── MemoryDbConfig.java             # Memory database (secondary)
│
├── controller/
│   └── MemoryController.java           # REST API endpoints
│
├── usecase/
│   ├── StoreMemoriesUseCase.java       # Application service (implements port)
│   ├── MemoryExtractorService.java     # Interface for extraction service
│   ├── LlmMemoryExtractorService.java  # LLM-based extraction implementation
│   ├── StoreMemoriesUseCase.java       # Orchestration: extract + store
│   ├── LlmAdapter.java                 # Interface for LLM processing
│   ├── DefaultLlmAdapter.java          # Direct LLM processing adapter
│   ├── LlmAdapterQueue.java            # Reactive queue for LLM requests
│   └── MemoryExtractorResult.java      # Extraction result model
│
├── port/
│   └── StoreMemoriesPort.java          # Port interface for memory storage
│
├── adapter/
│   ├── SqliteMemorySource.java         # Reads from agent database
│   └── DatabaseMemoryAdapter.java      # Adapts port for session extraction
│
├── service/
│   └── QdrantService.java              # Business logic wrapper around VectorStore
│
├── entity/
│   ├── agent/                          # Agent entities (Message, Session, Part)
│   └── memory/                         # Memory entities (ExtractionState)
│
├── repository/
│   ├── agent/                          # MessageRepository, PartRepository
│   └── memory/                         # ExtractionStateRepository
│
└── model/
    ├── MemoryExtractRequest.java       # Extraction request model
    ├── ExtractionContext.java          # Context for extraction
    └── ExtractionLevel.java            # MESSAGE, TURN, SESSION enum
```

### Dependency Declarations (pom.xml)

```xml
<properties>
    <java.version>21</java.version>
    <spring-ai.version>2.0.0-M3</spring-ai.version>
</properties>

<dependencies>
    <!-- Spring AI Qdrant Vector Store -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-qdrant</artifactId>
    </dependency>

    <!-- Spring AI OpenAI (for LLM and Embedding) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>

    <!-- Spring Boot Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- SQLite JDBC -->
    <dependency>
        <groupId>org.xerial</groupId>
        <artifactId>sqlite-jdbc</artifactId>
        <version>3.51.3.0</version>
    </dependency>

    <!-- Jackson for JSON processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    
    <!-- Jackson for JSON parsing (in LlmMemoryExtractorService) -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

### Data Flow

#### Memory Extraction Flow

```
1. Trigger (REST API or Batch Job)
   │
   ▼
2. SqliteMemorySource.getRequest(sessionId, ExtractionLevel)
   │   └── Fetches Message/Session/Part from Agent Database
   │   └── Builds JSON content with metadata
   │
   ▼
3. MemoryExtractRequest(contexts, level)
   │   └── Contains ExtractionContext[id, content, metadata]
   │
   ▼
4. StoreMemoriesUseCase.storeMemories(request)
   │
   ├──▶ 5a. LlmMemoryExtractorService.extractMemories(request)
   │        ├── Loads prompt template from file
   │        ├── Calls ChatModel (LLM) with formatted prompt
   │        ├── Parses JSON response
   │        └── Returns MemoryExtractorResult[ExtractedMemory]
   │
   └──▶ 5b. QdrantService.addMemoryWithMemtype(content, memtype, keywords)
            ├── Generates content hash for deduplication
            ├── Creates Document with metadata
            └── Adds to VectorStore (Qdrant)
   │
   ▼
6. Returns StoreMemoriesResult(storedIds, extractedMemories)
```

#### Extraction Levels

| Level    | Description                                    | Use Case                          |
|----------|------------------------------------------------|-----------------------------------|
| MESSAGE  | Single message + all its parts                 | Atomic facts, specific intents    |
| TURN     | Conversation turn (user + assistant messages)  | Tool usage reasoning, outcomes    |
| SESSION  | Full session with all messages and parts       | Project state, long-term goals    |

### Prompt Template System

The LLM extraction uses a configurable prompt template loaded from file:

```java
@Service
public class LlmMemoryExtractorService implements MemoryExtractorService {
    
    @Value("${memory.extractor.prompt.path:prompt/memory-extractor.txt}")
    private String promptTemplatePath;
    
    private String loadPromptTemplate() {
        // Loads from file system or classpath
        // Caches for 5 seconds to avoid repeated I/O
    }
    
    public MemoryExtractorResult extractMemories(MemoryExtractRequest request) {
        String template = loadPromptTemplate();
        String prompt = template.formatted(thoughts); // Context injected
        
        String response = chatClient.prompt()
            .system("Return only valid JSON")
            .user(prompt)
            .call()
            .content();
        
        return parseJsonResponse(response);
    }
}
```

Example prompt template (`prompt/memory-extractor.txt`):
```
Extract memories from the following conversation data:

{}

Return a JSON array of memories with this structure:
[
  {
    "content": "The actual memory text",
    "memtype": "fact|instruction|preference|goal",
    "keywords": ["tag1", "tag2"]
  }
]
```

### LLM Adapter Pattern

The application supports both synchronous and queued LLM processing:

```java
// Port interface
public interface LlmAdapter {
    void processRequest(String request);
}

// Direct implementation (synchronous)
@Component
public class DefaultLlmAdapter implements LlmAdapter {
    private final ChatClient chatClient;
    
    public void processRequest(String request) {
        String response = chatClient.prompt().user(request).call().content();
        // Process response
    }
}

// Queue-based implementation (asynchronous)
@Component
public class LlmAdapterQueue {
    private final Sinks.Many<String> sink;
    private final LlmAdapter llmAdapter;
    
    public void enqueue(String request) {
        sink.tryEmitNext(request); // Non-blocking
    }
    
    // Internal: Flux processes queue sequentially
    subscription = flux.subscribe(request -> llmAdapter.processRequest(request));
}
```

### Configuration Properties

```properties
# ============================================================
# LLM Configuration
# ============================================================
spring.ai.openai.chat.base-url=http://192.168.2.103:8081/v1
spring.ai.openai.chat.api-key=not-needed
spring.ai.openai.chat.options.model=gemma3:4b-it-q8_0
spring.ai.openai.chat.options.timeout=300s

# ============================================================
# Memory Extraction Configuration
# ============================================================
memory.extractor.prompt.path=prompt/memory-extractor.txt

# ============================================================
# Retry Configuration (for LLM server busy scenarios)
# ============================================================
spring.ai.retry.maxAttempts=3
spring.ai.retry.backoff-delay=5s

# ============================================================
# Logging
# ============================================================
logging.level.org.springframework.ai=DEBUG
logging.level.com.hdekker.qdrant.usecase=DEBUG
```

### State Tracking

Extraction state is persisted in the memory database to support incremental processing:

```java
@Entity
@Table(name = "extraction_state")
public class ExtractionState {
    @Id
    private String id;              // e.g., "session:{sessionId}" or "global"
    
    private String entityType;      // e.g., "session", "project"
    private String entityId;        // e.g., session ID, project ID
    
    @Column(columnDefinition = "TEXT")
    private String stateData;       // JSON state (last processed timestamp, etc.)
    
    private LocalDateTime lastProcessed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

## Consequences

### Benefits

1. **Clean Architecture**: Clear separation between ports (interfaces) and adapters (implementations)
2. **Testability**: Each layer can be tested independently with mocks
3. **Flexibility**: Easy to swap LLM providers, vector stores, or data sources
4. **Multi-Level Extraction**: Supports different granularities for different use cases
5. **Incremental Processing**: State tracking enables resuming interrupted extractions
6. **Deduplication**: Content hashing prevents duplicate memory storage

### Tight Coupling Points

⚠️ **The following areas have tight coupling that should be addressed:**

1. **StoreMemoriesUseCase implements StoreMemoriesPort**:
   - The use case directly implements its own port interface
   - This creates a circular dependency pattern
   - **Recommendation**: Extract interface to separate package or use different naming

2. **QdrantService wraps VectorStore port**:
   - QdrantService is infrastructure-level but placed in `service` package
   - Should either be in `adapter` package or be the actual VectorStore bean
   - Current design mixes application and infrastructure concerns

3. **SqliteMemorySource directly uses JPA Repositories**:
   - `SqliteMemorySource` is not a Spring bean (no `@Component`)
   - Directly couples to JPA implementation details
   - **Recommendation**: Make it a proper repository adapter with `@Repository` annotation

4. **LlmMemoryExtractorService uses Jackson (tools.jackson)**:
   - Uses `tools.jackson.databind` instead of `com.fasterxml.jackson`
   - Inconsistent with rest of application (uses `com.fasterxml.jackson` in `SqliteMemorySource`)
   - **Recommendation**: Standardize on one JSON library

5. **MemoryController depends on both QdrantService and StoreMemoriesPort**:
   - Controller exposes both raw vector store operations AND extraction workflow
   - Creates confusion about responsibility boundaries
   - **Recommendation**: Separate controllers for raw vector ops vs. memory extraction

6. **DatabaseMemoryAdapter constructs ExtractionContext inline**:
   - Creates tight coupling between adapter and extraction models
   - Should delegate to a factory or builder
   - **Recommendation**: Extract context building logic to dedicated factory

### Trade-offs

1. **Complexity**: Multi-layer architecture adds boilerplate for simple operations
2. **Performance**: LLM extraction adds latency (1-5 minutes per extraction)
3. **Error Handling**: JSON parsing from LLM responses is fragile
4. **Queue Management**: `LlmAdapterQueue` lacks backpressure handling and metrics
5. **State Tracking**: `ExtractionState` entity defined but not yet integrated into extraction workflow

### Important Notes

1. **ChatClient is Primary Bean**: The `ChatClient` bean is created by `OpenAiChatConfig` and injected throughout
2. **Two Separate Databases**: Agent data and memory state are in separate SQLite databases
3. **VectorStore is Auto-Configured**: Spring AI auto-configures `QdrantVectorStore` as a bean
4. **Prompt Templates are Cached**: 5-second cache to avoid repeated file I/O
5. **LLM Responses Require Parsing**: LLM may return markdown or extra text around JSON

## How to Add New Extraction Level

1. Add new enum value to `ExtractionLevel`:
   ```java
   public enum ExtractionLevel {
       MESSAGE, TURN, SESSION, CONVERSATION  // Add new level
   }
   ```

2. Implement handler in `SqliteMemorySource`:
   ```java
   private MemoryExtractRequest getConversationRequest(String conversationId) {
       // Fetch conversation data
       // Build content and metadata
       return new MemoryExtractRequest(List.of(context), ExtractionLevel.CONVERSATION);
   }
   
   public MemoryExtractRequest getRequest(String id, ExtractionLevel level) {
       return switch (level) {
           case MESSAGE -> getMessageRequest(id);
           case TURN -> getTurnRequest(id);
           case SESSION -> getSessionRequest(id);
           case CONVERSATION -> getConversationRequest(id);  // New case
       };
   }
   ```

3. Update prompt template to handle new level

## How to Swap LLM Provider

1. Create new configuration class:
   ```java
   @Configuration
   public class OllamaChatConfig {
       @Bean
       @Primary
       public ChatModel chatModel() {
           OllamaApi ollamaApi = new OllamaApi("http://localhost:11434");
           return OllamaChatModel.builder()
               .ollamaApi(ollamaApi)
               .defaultOptions(OllamaChatOptions.builder()
                   .model("llama3.2")
                   .build())
               .build();
       }
   }
   ```

2. Update `application.properties` or use profile-specific config

3. No changes needed to `LlmMemoryExtractorService` (depends on `ChatClient` abstraction)

## See Also

- [ADR: Multiple SQLite Database Configuration](adr-multi-db-support.md)
- [ADR-009: Qdrant Vector Store](adr-009-qdrant-vector-store.md)
- [ADR-010: llama.cpp](adr-010-llama-cpp.md)

## References

- Spring AI Documentation: https://docs.spring.io/spring-ai/reference/
- Clean Architecture: https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- Repository Pattern: https://docs.microsoft.com/en-us/azure/architecture/patterns/repository
- Reactor Core: https://projectreactor.io/docs/core/release/reference/
