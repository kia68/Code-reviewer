package de.ude.codereviewer.ingestion.service;

import java.util.List;

public record IngestionResult(String sourcePath, List<String> relativeFilePaths, long totalSizeBytes) {

    public int fileCount() {
        return relativeFilePaths.size();
    }
}
