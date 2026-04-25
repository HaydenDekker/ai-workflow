package com.hdekker.ai_workflow.app;

import com.hdekker.ai_workflow.app.pipeline.management.ScannerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds dummy scanner data on application startup for Phase 1 UI development.
 */
@Component
public class ScannerDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScannerDataSeeder.class);

    private final ScannerRegistry scannerRegistry;

    public ScannerDataSeeder(ScannerRegistry scannerRegistry) {
        this.scannerRegistry = scannerRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        scannerRegistry.seedDummyData();
        log.info("Scanner data seeder completed");
    }
}
