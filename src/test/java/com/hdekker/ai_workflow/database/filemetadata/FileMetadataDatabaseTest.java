package com.hdekker.ai_workflow.database.filemetadata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import com.hdekker.ai_workflow.config.DatabaseConfig;
import com.hdekker.ai_workflow.config.DataSourceProperties;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@ContextConfiguration(classes = {DatabaseConfig.class, DataSourceProperties.class})
@Transactional
public class FileMetadataDatabaseTest {

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	public void givenFileMetadataEntity_WhenSaved_ExpectEntityPersisted() {
		
		FileMetadataEntity entity = new FileMetadataEntity();
		entity.setUrl("file:///test/example.txt");
		entity.setHash("abc123");
		
		entityManager.persist(entity);
		entityManager.flush();
		entityManager.clear();
		
		FileMetadataEntity found = entityManager.find(FileMetadataEntity.class, "file:///test/example.txt");
		
		assertThat(found).isNotNull();
		assertThat(found.getUrl()).isEqualTo("file:///test/example.txt");
		assertThat(found.getHash()).isEqualTo("abc123");
	}
	
	@Test
	public void givenFileMetadataEntity_WhenReadByUrl_ExpectEntityReturned() {
		
		FileMetadataEntity entity = new FileMetadataEntity();
		entity.setUrl("file:///test/readme.md");
		entity.setHash("xyz789");
		
		entityManager.persist(entity);
		entityManager.flush();
		entityManager.clear();
		
		FileMetadataEntity found = entityManager.find(FileMetadataEntity.class, "file:///test/readme.md");
		
		assertThat(found).isNotNull();
		assertThat(found.getHash()).isEqualTo("xyz789");
	}
	
	@Test
	public void givenFileMetadataEntity_WhenDeleted_ExpectEntityRemoved() {
		
		FileMetadataEntity entity = new FileMetadataEntity();
		entity.setUrl("file:///test/to-delete.txt");
		entity.setHash("delete456");
		
		entityManager.persist(entity);
		entityManager.flush();
		entityManager.clear();
		
		FileMetadataEntity found = entityManager.find(FileMetadataEntity.class, "file:///test/to-delete.txt");
		assertThat(found).isNotNull();
		
		entityManager.remove(found);
		entityManager.flush();
		entityManager.clear();
		
		FileMetadataEntity afterDelete = entityManager.find(FileMetadataEntity.class, "file:///test/to-delete.txt");
		assertThat(afterDelete).isNull();
	}
	
}
