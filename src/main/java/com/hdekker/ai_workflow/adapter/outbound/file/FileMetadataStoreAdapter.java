package com.hdekker.ai_workflow.adapter.outbound.file;

import com.hdekker.ai_workflow.application.file.port.FileMetadataRepository;

/**
 * Adapter-layer alias for {@link FileMetadataRepository}.
 * <p>
 * Extends the application-layer port so that adapter implementations
 * and test doubles are compatible with both the port interface and
 * legacy references that use this adapter-layer name.
 */
public interface FileMetadataStoreAdapter extends FileMetadataRepository {
}
