package com.hdekker.ai_workflow.prompt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.stream.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hdekker.ai_workflow.pipeline.domain.AgentDefinition;
import com.hdekker.ai_workflow.pipeline.domain.AgentWorkflow;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Configuration
public class SystemPromptConfiguration {
	
	static Logger log = LoggerFactory.getLogger(SystemPromptConfiguration.class);
	
	public static final String SYSTEM_PROMPT_WORKFLOW_DIRECTORY_SEARCH = "agent-workflows";
	public static final String SYSTEM_PROMPT_WORKFLOW_DIRECTORY_SEARCH_CLASSPATH = "classpath*:"+ SYSTEM_PROMPT_WORKFLOW_DIRECTORY_SEARCH + "/**";
	
	@Autowired
	PromptConfiguration promptConfiguration;
	
	List<AgentWorkflow> agentWorkflows;
	
	final Boolean copiedLocally;
	
	public static List<Path> getImmediateChildDirectories(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .filter(Files::isDirectory) // Filter for directories
                .collect(Collectors.toList());
        }
    }
	
	@Autowired
	ResourcePatternResolver resourcePatternResolver;
	
class AgentWorkflowYAMLConfigReader {
		
static AgentWorkflow readYamlFile(Path workflowPath) throws Exception {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        String yamlContent = Files.readString(workflowPath);
        
        if (yamlContent.contains("chain:")) {
            log.error("YAML file {} uses deprecated 'chain:' property. Use 'agents:' instead.", workflowPath.getFileName());
            throw new IllegalArgumentException("Deprecated YAML format: use 'agents:' instead of 'chain:'");
        }
        
        AgentWorkflow doc = mapper.readValue(workflowPath.toFile(), AgentWorkflow.class);
        
       return doc;
    }
		
	}
	
	record AgentWorkflowFiles(AgentWorkflow workflow, Map<String,Path> supportingFile) {}
	
	public class PromptFileValidator {
		
public static AgentWorkflowFiles validate(AgentWorkflowFiles agentWorkflowFiles) {
			
			Optional<Tuple2<AgentDefinition, Path>> bodyFilesIsMissing = agentWorkflowFiles.workflow().agents().stream().map(pp->{
			return Tuples.of(pp, agentWorkflowFiles.supportingFile().get(pp.body()));
		}).filter(tup->(tup.getT2()==null))
		.findAny();
		 
		 if(bodyFilesIsMissing.isPresent()) {
			 SystemPromptConfiguration.log.error("Missing body file " + bodyFilesIsMissing.get().getT1().title());
			 return null;
		 }
		
		 // TODO add validation for output file template

		return agentWorkflowFiles;
	}
		
	}
	
class AgentWorkflowFileExtractor {
		
public static AgentWorkflowFiles extract(List<Path> paths) {
			
		Path chainPath = paths.stream()
				.filter(p->p.getFileName().toString().contains("agents.yml"))
				.findAny()
				.orElseThrow();
	
	try {
		AgentWorkflow workflow = AgentWorkflowYAMLConfigReader.readYamlFile(chainPath);
			
			List<Path> chainPathFiltered = paths.stream()
					.filter(p->!p.equals(chainPath))
					.toList();
			
			Map<String, Path> chainPathNames = chainPathFiltered.stream()
				.collect(Collectors.toMap(p -> p.getFileName().toString(), p->p));
			
			
			return new AgentWorkflowFiles(workflow, chainPathNames);
			
		} catch (Exception e) {
			
			e.printStackTrace();
			return null;
		}
		
	}
		
	}
	
	public class PromptConfigurationParser {
		
public static AgentWorkflow extractContent(AgentWorkflowFiles agentWorkflowFiles) {
		return new AgentWorkflow(agentWorkflowFiles.workflow()
			.agents()
			.stream()
			.map(pp->{
				
				Path bodyFile = agentWorkflowFiles.supportingFile().get(pp.body());
				String bodyFileAsString = null;
				try {
					bodyFileAsString = Files.readString(bodyFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				Path outputFile = agentWorkflowFiles.supportingFile().get(pp.outputStructure());
				String outputFileAsString = null;
				try {
					outputFileAsString = Files.readString(outputFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				return new AgentDefinition(
						pp.fileInputRegex(),
						pp.title(),
						pp.agentType(),
						bodyFileAsString, 
						outputFileAsString,
						pp.outputFilenameTemplate(),
						pp.targetDirectory());
				
			}).toList());
	}
		

	}
	
	SystemPromptConfiguration(PromptConfiguration promptConfiguration,
			ResourcePatternResolver resourcePatternResolver){
		this.promptConfiguration = promptConfiguration;
		this.resourcePatternResolver = resourcePatternResolver;
		

		Path pathToCopyTo = Paths.get(promptConfiguration.getPredefinedPromptFilePath());
		copyConfigurationInto(pathToCopyTo);
		
		try {
			Stream<AgentWorkflow> promptFiles = Files.list(pathToCopyTo)
					.<Stream<Path>> map(p-> {
						try {
							return Files.list(p);
						} catch (IOException e) {
							e.printStackTrace();
							return Stream.of();
						}
					})
					.map(Stream::toList)
					.map(AgentWorkflowFileExtractor::extract)
					.map(PromptFileValidator::validate)
					.map(PromptConfigurationParser::extractContent);
					
			agentWorkflows = promptFiles.toList();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		copiedLocally = true;
		
	}
	
	private void copyConfigurationInto(Path directory) {
		
		try {
			Resource[] promptDir = resourcePatternResolver.getResources(SYSTEM_PROMPT_WORKFLOW_DIRECTORY_SEARCH_CLASSPATH);
			log.info("" + promptDir.length);
			Streams.of(promptDir)
				.forEach(r-> copyResourceToDirectory(r, directory));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void copyResourceToDirectory(Resource resource, Path directory) {
		try {
			String fullPath = resource.getFile().getAbsolutePath();
			String relativePath = PathUtility.getRelativePath(fullPath, SYSTEM_PROMPT_WORKFLOW_DIRECTORY_SEARCH);
			Path filePath = directory.resolve(relativePath);
			if(!Files.exists(filePath)){
				Files.createDirectories(filePath);
			}
			if(resource.getFile().isDirectory()) return;
			Files.copy(resource.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public List<AgentWorkflow> getAgentWorkflows() {
		return agentWorkflows;
	}

	public Boolean getCopiedLocally() {
		return copiedLocally;
	}
	
	


}
