package com.hdekker.ai_workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for observability features.
 * 
 * Prefix: app.observability
 * 
 * Example application.yml:
 * app:
 *   observability:
 *     endpoint: http://192.168.2.108:11434
 *     model: gemma3:27b
 *     polling-interval: 60000
 *     warn-after-hours: 1
 *     health-timeout: 5000
 */
@Configuration
@ConfigurationProperties(value = "app.observability")
public class ObservabilityProperties {
    
    /**
     * OpenAI-compatible endpoint URL to health check.
     * Default: http://localhost:8080
     */
    private String endpoint = "http://localhost:8080";
    
    /**
     * Expected model name at the endpoint.
     * Default: null
     */
    private String model;
    
    /**
     * Polling interval in milliseconds.
     * Default: 60000 (1 minute)
     */
    private long pollingInterval = 60000;
    
    /**
     * Hours after which UP status becomes WARN if no new data.
     * Default: 1 hour
     */
    private long warnAfterHours = 1;
    
    /**
     * Health check timeout in milliseconds.
     * Default: 5000 (5 seconds)
     */
    private int healthTimeout = 5000;
    
    // Getters and Setters
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public long getPollingInterval() { return pollingInterval; }
    public void setPollingInterval(long pollingInterval) { this.pollingInterval = pollingInterval; }
    
    public long getWarnAfterHours() { return warnAfterHours; }
    public void setWarnAfterHours(long warnAfterHours) { this.warnAfterHours = warnAfterHours; }
    
    public int getHealthTimeout() { return healthTimeout; }
    public void setHealthTimeout(int healthTimeout) { this.healthTimeout = healthTimeout; }
}
