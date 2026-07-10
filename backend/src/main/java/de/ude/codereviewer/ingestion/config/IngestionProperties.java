package de.ude.codereviewer.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionProperties(
        String storageDir,
        long maxFileSizeBytes,
        long maxZipSizeBytes,
        int maxZipEntries,
        long maxExtractedBytes) {
}
