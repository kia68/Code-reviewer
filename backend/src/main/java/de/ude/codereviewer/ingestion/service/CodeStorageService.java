package de.ude.codereviewer.ingestion.service;

import org.springframework.web.multipart.MultipartFile;

public interface CodeStorageService {

    // Validates the upload (.java file or .zip) and persists it; throws ResponseStatusException on rejection.
    IngestionResult store(Long reviewRunId, MultipartFile file);
}
