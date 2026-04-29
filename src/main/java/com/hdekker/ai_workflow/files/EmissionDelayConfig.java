package com.hdekker.ai_workflow.files;

/**
 * Configuration properties for the AI scanner subsystem.
 * <p>
 * Controls emission behaviour of the file watcher (e.g. minimum interval
 * between consecutive file emissions to avoid flooding the LLM pipeline).
 */
public class EmissionDelayConfig {

    /** Default delay between emissions in seconds. */
    public static final int DEFAULT_DELAY_SECONDS = 20;

    private int emissionDelaySeconds;

    public EmissionDelayConfig() {
        this.emissionDelaySeconds = DEFAULT_DELAY_SECONDS;
    }

    public EmissionDelayConfig(int emissionDelaySeconds) {
        setEmissionDelaySeconds(emissionDelaySeconds);
    }

    public int getEmissionDelaySeconds() {
        return emissionDelaySeconds;
    }

    public void setEmissionDelaySeconds(int emissionDelaySeconds) {
        if (emissionDelaySeconds <= 0) {
            this.emissionDelaySeconds = DEFAULT_DELAY_SECONDS;
        } else {
            this.emissionDelaySeconds = emissionDelaySeconds;
        }
    }
}
