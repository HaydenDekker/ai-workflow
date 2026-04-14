# ADR: Qdrant Vector Store Integration with Spring AI

## Status

Accepted

## Context

This application integrates a Qdrant vector store for semantic memory storage and retrieval. The integration uses Spring AI's vector store abstraction to enable similarity search and semantic memory operations. The system supports both OpenAI-compatible embedding models (including local models via llama.cpp) and Qdrant as the vector database backend.

Key requirements:
- Store memories as vector embeddings in Qdrant
- Support similarity search with configurable top-k results
- Filter memories by metadata (memtype, keywords, content_hash)
- Use OpenAI-compatible embedding models (local or remote)
- Maintain document deduplication via content hashing

## Decision

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Spring Boot Application                  │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              QdrantService (Service Layer)           │    │
│  │  - addMemory(content)                                │    │
│  │  - searchMemories(query, topK)                       │    │
│  │  - addMemoryWithMemtype(content, memtype, keywords) │    │
│  │  - searchMemoriesWithFilter(query, topK, filter)    │    │
│  └─────────────────────────────────────────────────────┘    │
│                        │                                      │
│                        ▼                                      │
│  ┌─────────────────────────────────────────────────────┐    │
│  │            VectorStore (Spring AI Abstraction)      │    │
│  │  Auto-configured QdrantVectorStore bean             │    │
│  └─────────────────────────────────────────────────────┘    │
│                        │                                      │
│                        ▼                                      │
│  ┌─────────────────────────────────────────────────────┐    │
│  │           EmbeddingModel (Spring AI Abstraction)    │    │
│  │  OpenAiEmbeddingModel (OpenAI-compatible API)       │    │
│  └─────────────────────────────────────────────────────┘    │
│                        │                                      │
└────────────────────────┼──────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    External Services                         │
├──────────────────────────────┬──────────────────────────────┤
│      Qdrant Vector Store     │   Embedding Service          │
│      localhost:6334          │   http://192.168.2.103:3300  │
│      Collection: memories    │   Model: qwen3-embedding     │
└──────────────────────────────┴──────────────────────────────┘
```

### Dependency Declarations (pom.xml)

```xml
<properties>
    <java.version>21</java.version>
    <spring-ai.version>2.0.0-M3</spring-ai.version>
</properties>

<dependencies>
    <!-- Spring AI Qdrant Vector Store Starter -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-qdrant</artifactId>
    </dependency>

    <!-- Spring AI OpenAI Starter (for embedding model) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>

    <!-- Spring WebMVC (for REST API) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>

    <!-- Jackson for JSON processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Configuration Properties (application.properties)

```properties
# Qdrant Vector Store Configuration
spring.ai.vectorstore.qdrant.host=localhost
spring.ai.vectorstore.qdrant.port=6334
spring.ai.vectorstore.qdrant.collection-name=memories
spring.ai.vectorstore.qdrant.initialize-schema=true

# Embedding Model Configuration (OpenAI-compatible API)
spring.ai.openai.embedding.api-key=not-needed
spring.ai.openai.embedding.base-url=http://192.168.2.103:3300/v1
spring.ai.openai.embedding.options.model=qwen3-embedding

# Optional: Chat Model Configuration (separate from embeddings)
spring.ai.openai.chat.api-key=not-needed
spring.ai.openai.chat.base-url=http://192.168.2.103:8080/v1
spring.ai.openai.chat.options.model=gemma3:4b-it-q8_0

# HTTP client timeouts
spring.ai.openai.chat.options.timeout=300s
spring.ai.retry.maxAttempts=3
spring.ai.retry.backoff-delay=5s

# Logging
logging.level.org.springframework.ai=DEBUG
logging.level.org.springframework.retry=DEBUG
```

### EmbeddingModel Configuration (OpenAiEmbeddingConfig.java)

Custom embedding model configuration for OpenAI-compatible APIs:

```java
package com.hdekker.qdrant.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class OpenAiEmbeddingConfig {

    @Bean
    public OpenAiApi embeddingApi() {
        return OpenAiApi.builder()
                .baseUrl("http://192.168.2.103:3300")
                .apiKey(new SimpleApiKey("not-needed"))
                .embeddingsPath("/v1/embeddings")
                .build();
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(OpenAiApi openAiApi) {
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED);
    }
}
```

Key points:
- `MetadataMode.EMBED`: Embeds both text content and metadata
- Custom `OpenAiApi` bean allows custom base URL for local models
- API key can be set to any value if not required by the model server

### QdrantService.java (Service Layer)

Business logic layer for vector store operations:

```java
package com.hdekker.qdrant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Service
public class QdrantService {

    private static final Logger logger = LoggerFactory.getLogger(QdrantService.class);
    private final VectorStore vectorStore;

    public QdrantService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    private String generateContentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate content hash", e);
        }
    }

    public String addMemory(String content) {
        String contentHash = generateContentHash(content);
        String id = UUID.nameUUIDFromBytes(content.getBytes(StandardCharsets.UTF_8)).toString();
        
        // Check for duplicates
        var existing = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(content)
                .topK(1)
                .build()
        );
        
        if (!existing.isEmpty() && existing.get(0).getId().equals(id)) {
            return id; // Duplicate found
        }
        
        Document document = new Document(
            id,
            content,
            Map.of("content_hash", contentHash)
        );
        vectorStore.add(List.of(document));
        return id;
    }

    public List<Document> searchMemories(String query, int topK) {
        return vectorStore.similaritySearch(query);
    }

    public String addMemoryWithMemtype(String content, String memtype, List<String> keywords) {
        String contentHash = generateContentHash(content + memtype);
        String id = UUID.nameUUIDFromBytes((content + memtype).getBytes(StandardCharsets.UTF_8)).toString();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("content_hash", contentHash);
        metadata.put("memtype", memtype);
        metadata.put("keywords", keywords);
        
        Document document = new Document(id, content, metadata);
        vectorStore.add(List.of(document));
        return id;
    }

    public List<Document> searchMemoriesWithFilter(String query, int topK, Filter.Expression filter) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .filterExpression(filter)
            .build();
        return vectorStore.similaritySearch(request);
    }
}
```

### Document Model

Documents stored in Qdrant follow this structure:

```java
// Spring AI Document model
public class Document {
    private String id;                    // Unique identifier
    private String content;               // Text content to embed
    private Map<String, Object> metadata; // Key-value metadata
}
```

Supported metadata types:
- `String`: memtype, content_hash
- `List<String>`: keywords
- Any JSON-serializable type supported by Qdrant

### Filter Expressions

Spring AI provides a type-safe filter expression builder:

```java
import org.springframework.ai.vectorstore.filter.Filter;

// Filter by memtype equality
Filter.Expression memtypeFilter = Filter.ExpressionBuilder
    .builder()
    .field("memtype")
    .isEqualTo("fact");

// Filter by keywords containment
Filter.Expression keywordFilter = Filter.ExpressionBuilder
    .builder()
    .field("keywords")
    .contains("programming");

// Combine filters
Filter.Expression combined = Filter.ExpressionBuilder
    .builder()
    .and(
        Filter.ExpressionBuilder.builder().field("memtype").isEqualTo("fact"),
        Filter.ExpressionBuilder.builder().field("keywords").contains("java")
    );
```

## Consequences

### Benefits

1. **Abstraction**: Spring AI's VectorStore interface abstracts the underlying vector database, allowing easy switching between providers
2. **Automatic Embedding**: Documents are automatically embedded using the configured EmbeddingModel
3. **Built-in Deduplication**: Content hashing prevents duplicate memory storage
4. **Metadata Filtering**: Support for filtering by arbitrary metadata fields
5. **OpenAI Compatibility**: Works with any OpenAI-compatible embedding endpoint (local or remote)

### Trade-offs

1. **Version Maturity**: Spring AI is in M3 (Milestone 3) - API may change
2. **Network Dependency**: Embedding model must be accessible via HTTP API
3. **No Cross-DB Transactions**: Vector store and relational database transactions are independent
4. **Limited Filter Operators**: Filter expressions support only basic operations (equals, contains, etc.)

### Important Notes

1. **Collection Auto-Creation**: When `initialize-schema=true`, Qdrant collection is automatically created with the configured name
2. **Vector Dimension**: Automatically determined by the embedding model (e.g., 1024 for qwen3-embedding)
3. **ID Generation**: Document IDs should be deterministic (e.g., UUID from content hash) to enable deduplication
4. **Content Hashing**: SHA-256 hash stored in metadata for change detection

## How to Configure for Another Project

### Step 1: Add Dependencies

Add the Spring AI BOM and required starters to your `pom.xml`:

```xml
<properties>
    <spring-ai.version>2.0.0-M3</spring-ai.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-vector-store-qdrant</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Step 2: Configure application.properties

```properties
# Qdrant
spring.ai.vectorstore.qdrant.host=YOUR_QDRANT_HOST
spring.ai.vectorstore.qdrant.port=6334
spring.ai.vectorstore.qdrant.collection-name=YOUR_COLLECTION_NAME
spring.ai.vectorstore.qdrant.initialize-schema=true

# Embedding Model
spring.ai.openai.embedding.api-key=YOUR_API_KEY
spring.ai.openai.embedding.base-url=YOUR_EMBEDDING_API_URL/v1
spring.ai.openai.embedding.options.model=YOUR_EMBEDDING_MODEL_NAME
```

### Step 3: Create Service Layer

Inject `VectorStore` and implement your business logic:

```java
@Service
public class MyVectorStoreService {
    private final VectorStore vectorStore;

    public MyVectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public String addDocument(String content) {
        String id = UUID.randomUUID().toString();
        Document document = new Document(id, content, Map.of());
        vectorStore.add(List.of(document));
        return id;
    }

    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
            .query(query)
            .topK(topK)
            .build());
    }
}
```

### Step 4: Start Qdrant

Run Qdrant locally or use a hosted instance:

```bash
# Local Docker
docker run -p 6334:6334 qdrant/qdrant

# Or use Qdrant Cloud (configure host/port in application.properties)
```

## See Also

For detailed llama.cpp integration setup, see: [adr-chat-model-setup-for-llama-cpp.md](adr-chat-model-setup-for-llama-cpp.md)

## References

- Spring AI Documentation: https://docs.spring.io/spring-ai/reference/
- Spring AI Qdrant Vector Store: https://docs.spring.io/spring-ai/reference/api/vector-stores/qdrant.html
- Spring AI OpenAI: https://docs.spring.io/spring-ai/reference/api/chat/openai.html
- llama.cpp Server: https://github.com/ggerganov/llama.cpp/tree/master/examples/server
- GGUF Model Format: https://github.com/ggerganov/ggml/blob/master/docs/gguf.md
- HuggingFace GGUF Models: https://huggingface.co/models?library=gguf
- Qdrant Documentation: https://qdrant.tech/documentation/
