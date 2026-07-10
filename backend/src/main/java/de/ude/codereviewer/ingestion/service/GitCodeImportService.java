package de.ude.codereviewer.ingestion.service;

public interface GitCodeImportService {

    // Validates the URL, shallow-clones the repo and persists its .java files; throws ResponseStatusException on rejection.
    IngestionResult importFromUrl(Long reviewRunId, String repositoryUrl);
}
