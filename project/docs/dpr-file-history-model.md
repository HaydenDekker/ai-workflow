# DPR: File History Model

---

## Purpose

This document describes the `FileHistory` event model, how files are uniquely identified, how hash comparison detects changes, and how file metadata is stored across multiple scanners.

---

## File History Event Structure

The `FileHistory` object represents a single file system event (CREATE, MODIFY, DELETE) detected by a scanner. It contains:

| Field | Type | Description |
|-------|------|-------------|
| `url` | `String` | **Absolute file path** — unique identifier across all scanners |
| `timestamp` | `Instant` | When the event was detected |
| `eventType` | `Enum` | CREATE, MODIFY, or DELETE |
| `fileSize` | `Long` | File size in bytes at time of detection |
| `contentHash` | `String` | SHA-256 hash of file content for change detection |
| `previousHash` | `String` | Previously stored hash (if file existed before) |

### Absolute Path Keys

**All file metadata uses absolute paths as unique keys** in `FileMetadataEntity.url`. This ensures:

- No collisions between scanners watching different folders with the same relative paths
- Existing database schema requires no changes
- Hash comparison works correctly across scanner boundaries

### Example

```java
// Event from scanner watching /projectA/src
FileHistory eventA = new FileHistory(
    "/projectA/src/Main.java",        // absolute path
    Instant.now(),
    EventType.MODIFY,
    4096L,
    "abc123...",                       // SHA-256 hash
    "def456..."                        // previous hash
);

// Event from scanner watching /projectB/src — same relative path, different absolute
FileHistory eventB = new FileHistory(
    "/projectB/src/Main.java",        // different absolute path
    Instant.now(),
    EventType.MODIFY,
    4096L,
    "abc123...",                       // same content hash (same file content)
    "def456..."
);

// These are distinct events — different URLs, even if content is identical
```

---

## Hash Comparison for Change Detection

### How It Works

When a file event is detected, the scanner compares the file's current content hash against the previously stored hash:

```
Event Detected
     │
     ▼
Read file content
     │
     ▼
Compute SHA-256 hash
     │
     ▼
Lookup stored hash by absolute path
     │
     ├── Hash matches → Skip event (no meaningful change)
     │
     └── Hash differs → Emit FileHistory event
```

### Implementation

```java
public boolean hashMatches(String absolutePath) {
    Optional<FileMetadataEntity> stored = metadataRepository.findByUrl(absolutePath);
    if (stored.isEmpty()) {
        return false;  // New file — always process
    }
    
    String currentHash = computeSha256(absolutePath);
    boolean matches = stored.get().getContentHash().equals(currentHash);
    
    if (!matches) {
        // Update stored hash
        stored.get().setContentHash(currentHash);
        metadataRepository.save(stored.get());
    }
    
    return matches;
}
```

### Hash Storage

| Field | Type | Description |
|-------|------|-------------|
| `url` | `String` (PK) | Absolute file path |
| `contentHash` | `String` | Last known SHA-256 hash |
| `lastModified` | `Instant` | When the hash was last updated |
| `fileSize` | `Long` | File size at last detection |

### SHA-256 Algorithm

File content is hashed using **SHA-256** for change detection:

```java
private String computeSha256(String filePath) throws IOException {
    try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(is.readAllBytes());
        return bytesToHex(hash);
    }
}

private String bytesToHex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
}
```

**Why SHA-256?**
- Collision-resistant: highly unlikely for file content
- Standard library: no external dependencies
- Consistent across platforms
- Fast enough for typical source files

---

## Metadata Storage Across Scanners

When multiple scanners watch different folders, metadata is stored in a **single database table** with absolute paths as keys:

```sql
CREATE TABLE file_metadata (
    url TEXT PRIMARY KEY,           -- absolute path, unique across all scanners
    content_hash TEXT NOT NULL,
    last_modified TIMESTAMP NOT NULL,
    file_size INTEGER NOT NULL
);
```

### Cross-Scanner Behavior

| Scenario | Behavior |
|----------|----------|
| Same file watched by two scanners | Impossible — absolute paths are unique |
| Same content in two different files | Two rows with different URLs, same hash |
| File moved from one watched folder to another | Delete event for old path, create event for new path |
| File deleted from one folder, re-created elsewhere | Treated as two independent events (different URLs) |

---

## File Metadata Entity

```java
@Entity
@Table(name = "file_metadata")
public class FileMetadataEntity {

    @Id
    private String url;              // absolute file path

    private String contentHash;
    private Instant lastModified;
    private Long fileSize;

    // Getters and setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public Instant getLastModified() { return lastModified; }
    public void setLastModified(Instant lastModified) { this.lastModified = lastModified; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
}
```

---

## See Also

- [DPR: Scanner Concept](dpr-scanner-concept.md) — How scanners detect and rate-limit file events
- [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) — How agents subscribe to scanner flux
- [DPR: Agent-Scanner Relationship](dpr-agent-scanner-relationship.md) — How agents subscribe to scanner flux
