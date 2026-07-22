package de.ude.codereviewer.review.service;

import de.ude.codereviewer.analysis.ReviewEngine;
import de.ude.codereviewer.analysis.ast.AstParseReport;
import de.ude.codereviewer.analysis.ast.AstParserService;
import de.ude.codereviewer.analysis.smell.DetectedSmell;
import de.ude.codereviewer.analysis.smell.SmellDetectionService;
import de.ude.codereviewer.analysis.smell.SmellReport;
import de.ude.codereviewer.ingestion.service.CodeStorageService;
import de.ude.codereviewer.ingestion.service.GitCodeImportService;
import de.ude.codereviewer.ingestion.service.IngestionResult;
import de.ude.codereviewer.project.model.Project;
import de.ude.codereviewer.project.repository.ProjectRepository;
import de.ude.codereviewer.review.dto.FindingDto;
import de.ude.codereviewer.review.dto.ReviewRunDto;
import de.ude.codereviewer.review.dto.StoredFileDto;
import de.ude.codereviewer.review.model.Finding;
import de.ude.codereviewer.review.model.ReviewRun;
import de.ude.codereviewer.review.model.ReviewStatus;
import de.ude.codereviewer.review.model.StoredFile;
import de.ude.codereviewer.review.repository.FindingRepository;
import de.ude.codereviewer.review.repository.ReviewRunRepository;
import de.ude.codereviewer.review.repository.StoredFileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewRunService {

    private final ReviewRunRepository reviewRunRepository;
    private final ProjectRepository projectRepository;
    private final FindingRepository findingRepository;
    private final StoredFileRepository storedFileRepository;
    private final CodeStorageService codeStorageService;
    private final GitCodeImportService gitCodeImportService;
    private final AstParserService astParserService;
    private final SmellDetectionService smellDetectionService;
    private final List<ReviewEngine> engines;



    public ReviewRunDto ingest(Long projectId, MultipartFile file) {
        return runIngestion(projectId, reviewRunId -> codeStorageService.store(reviewRunId, file));
    }

    public ReviewRunDto ingestFromGit(Long projectId, String repositoryUrl) {
        return runIngestion(projectId, reviewRunId -> gitCodeImportService.importFromUrl(reviewRunId, repositoryUrl));
    }

    // Intentionally not @Transactional: the IN_PROGRESS row must be committed before ingestion
    // starts, and an ingestion failure must still leave a committed FAILED row for the audit trail.
    private ReviewRunDto runIngestion(Long projectId, Function<Long, IngestionResult> ingestionAction) {
        Project project = projectRepository
                .findById(projectId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found with id: " + projectId));

        ReviewRun reviewRun = reviewRunRepository.save(ReviewRun.builder()
                .project(project)
                .status(ReviewStatus.IN_PROGRESS)
                .triggeredAt(LocalDateTime.now())
                .build());

        IngestionResult result;
        try {
            result = ingestionAction.apply(reviewRun.getId());
        } catch (RuntimeException e) {
            reviewRun.setStatus(ReviewStatus.FAILED);
            reviewRun.setCompletedAt(LocalDateTime.now());
            reviewRunRepository.save(reviewRun);
            throw e;
        }

        reviewRun.setStatus(ReviewStatus.COMPLETED);
        reviewRun.setCompletedAt(LocalDateTime.now());
        reviewRun.setSourcePath(result.sourcePath());
        reviewRun.setFileCount(result.fileCount());
        reviewRun.setTotalSizeBytes(result.totalSizeBytes());
        reviewRunRepository.save(reviewRun);

        persistFilesFromDisk(reviewRun, Path.of(result.sourcePath()));

        return toDto(reviewRun);
    }

    private void persistFilesFromDisk(ReviewRun reviewRun, Path sourceDir) {
        if (storedFileRepository.countByReviewRunId(reviewRun.getId()) > 0) {
            return;
        }
        try (var stream = Files.walk(sourceDir)) {
            List<Path> javaFiles = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
            for (Path file : javaFiles) {
                StoredFile sf = StoredFile.builder()
                        .reviewRun(reviewRun)
                        .filePath(sourceDir.relativize(file).toString())
                        .content(Files.readString(file))
                        .sizeBytes(Files.size(file))
                        .build();
                storedFileRepository.save(sf);
            }
        } catch (IOException e) {
            // Best effort — files on disk are the source of truth if this fails
        }
    }

    @Transactional(readOnly = true)
    public ReviewRunDto getById(Long projectId, Long reviewRunId) {
        ReviewRun reviewRun = findOwnedReviewRun(projectId, reviewRunId);
        return toDto(reviewRun);
    }

    @Transactional(readOnly = true)
    public List<ReviewRunDto> getAllForProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found with id: " + projectId);
        }
        return reviewRunRepository.findByProjectIdOrderByTriggeredAtDesc(projectId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AstParseReport getAstReport(Long projectId, Long reviewRunId) {
        Path sourcePath = requireSourcePath(findOwnedReviewRun(projectId, reviewRunId));
        return astParserService.parseSourceDirectory(sourcePath);
    }

    @Transactional(readOnly = true)
    public SmellReport getSmellReport(Long projectId, Long reviewRunId) {
        Path sourcePath = requireSourcePath(findOwnedReviewRun(projectId, reviewRunId));
        return smellDetectionService.detectSmells(sourcePath);
    }

    @Transactional
    public List<FindingDto> analyzeFindings(Long projectId, Long reviewRunId) {
        ReviewRun reviewRun = findOwnedReviewRun(projectId, reviewRunId);
        Path sourcePath = requireSourcePath(reviewRun);

        findingRepository.deleteByReviewRunId(reviewRunId);
        try {
            List<Finding> findings = engines.stream()
                    .flatMap(engine -> {
                        try {
                            return engine.analyze(sourcePath).stream();
                        } catch (Exception e) {
                            log.warn("Engine {} failed, skipping: {}", engine.source(), e.getMessage());
                            return Stream.empty();
                        }
                    })
                    .map(smell -> toFinding(reviewRun, smell))
                    .toList();

            return findingRepository.saveAll(findings).stream().map(this::toFindingDto).toList();
        } catch (Exception e) {
            log.error("Analysis failed for run {}: {}", reviewRunId, e.getMessage());
            reviewRun.setStatus(ReviewStatus.FAILED);
            reviewRun.setCompletedAt(LocalDateTime.now());
            reviewRunRepository.save(reviewRun);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<FindingDto> getFindings(Long projectId, Long reviewRunId) {
        findOwnedReviewRun(projectId, reviewRunId);
        return findingRepository.findByReviewRunId(reviewRunId).stream()
                .map(this::toFindingDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoredFileDto> listFiles(Long projectId, Long reviewRunId) {
        ReviewRun reviewRun = findOwnedReviewRun(projectId, reviewRunId);
        ensureStoredFilesExist(reviewRun);
        return storedFileRepository.findByReviewRunIdOrderByFilePath(reviewRunId).stream()
                .map(this::toStoredFileDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoredFileDto readFile(Long projectId, Long reviewRunId, String filePath) {
        ReviewRun reviewRun = findOwnedReviewRun(projectId, reviewRunId);
        ensureStoredFilesExist(reviewRun);
        StoredFile sf = storedFileRepository.findByReviewRunIdAndFilePath(reviewRunId, filePath)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "File not found: " + filePath));
        return toStoredFileDtoWithContent(sf);
    }

    @Transactional
    public ReviewRunDto createNewVersion(Long projectId, Long parentRunId, List<StoredFileDto> files) {
        ReviewRun parentRun = findOwnedReviewRun(projectId, parentRunId);
        ensureStoredFilesExist(parentRun);

        Project project = parentRun.getProject();

        long totalSize = files.stream().mapToLong(StoredFileDto::getSizeBytes).sum();

        ReviewRun newRun = reviewRunRepository.save(ReviewRun.builder()
                .project(project)
                .status(ReviewStatus.COMPLETED)
                .triggeredAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .parentRun(parentRun)
                .fileCount(files.size())
                .totalSizeBytes(totalSize)
                .build());

        // Persist files to DB
        for (StoredFileDto fileDto : files) {
            StoredFile sf = StoredFile.builder()
                    .reviewRun(newRun)
                    .filePath(fileDto.getFilePath())
                    .content(fileDto.getContent())
                    .sizeBytes(fileDto.getSizeBytes())
                    .build();
            storedFileRepository.save(sf);
        }

        // Write files to disk so analysis works
        Path targetDir = Path.of("data/uploads", String.valueOf(newRun.getId())).toAbsolutePath().normalize();
        try {
            Files.createDirectories(targetDir);
            for (StoredFileDto fileDto : files) {
                Path targetFile = targetDir.resolve(fileDto.getFilePath());
                Files.createDirectories(targetFile.getParent());
                Files.writeString(targetFile, fileDto.getContent());
            }
            newRun.setSourcePath(targetDir.toString());
            reviewRunRepository.save(newRun);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write files to disk");
        }

        return toDto(newRun);
    }

    private void ensureStoredFilesExist(ReviewRun reviewRun) {
        if (storedFileRepository.countByReviewRunId(reviewRun.getId()) > 0) {
            return;
        }
        if (reviewRun.getSourcePath() != null) {
            persistFilesFromDisk(reviewRun, Path.of(reviewRun.getSourcePath()));
        }
    }

    private Path requireSourcePath(ReviewRun reviewRun) {
        if (reviewRun.getSourcePath() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Für diesen ReviewRun liegt kein Quellcode vor (Status: "
                            + reviewRun.getStatus() + ").");
        }
        return Path.of(reviewRun.getSourcePath());
    }

    private ReviewRun findOwnedReviewRun(Long projectId, Long reviewRunId) {
        ReviewRun reviewRun = reviewRunRepository
                .findById(reviewRunId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "ReviewRun not found with id: " + reviewRunId));
        if (!reviewRun.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "ReviewRun not found with id: " + reviewRunId);
        }
        return reviewRun;
    }

    private ReviewRunDto toDto(ReviewRun reviewRun) {
        return ReviewRunDto.builder()
                .id(reviewRun.getId())
                .projectId(reviewRun.getProject().getId())
                .status(reviewRun.getStatus())
                .triggeredAt(reviewRun.getTriggeredAt())
                .completedAt(reviewRun.getCompletedAt())
                .fileCount(reviewRun.getFileCount())
                .totalSizeBytes(reviewRun.getTotalSizeBytes())
                .parentRunId(reviewRun.getParentRun() != null ? reviewRun.getParentRun().getId() : null)
                .build();
    }

    private StoredFileDto toStoredFileDto(StoredFile sf) {
        return StoredFileDto.builder()
                .id(sf.getId())
                .filePath(sf.getFilePath())
                .sizeBytes(sf.getSizeBytes())
                .build();
    }

    private StoredFileDto toStoredFileDtoWithContent(StoredFile sf) {
        return StoredFileDto.builder()
                .id(sf.getId())
                .filePath(sf.getFilePath())
                .sizeBytes(sf.getSizeBytes())
                .content(sf.getContent())
                .build();
    }

    private Finding toFinding(ReviewRun reviewRun, DetectedSmell smell) {
        return Finding.builder()
                .reviewRun(reviewRun)
                .filePath(smell.filePath())
                .lineNumber(smell.lineNumber())
                .category(smell.category())
                .severity(smell.severity())
                .description(smell.description())
                .suggestion(smell.suggestion())
                .source(smell.source())
                .confidence(smell.confidence())
                .build();
    }

    private FindingDto toFindingDto(Finding finding) {
        return FindingDto.builder()
                .id(finding.getId())
                .reviewRunId(finding.getReviewRun().getId())
                .filePath(finding.getFilePath())
                .lineNumber(finding.getLineNumber())
                .category(finding.getCategory())
                .severity(finding.getSeverity())
                .description(finding.getDescription())
                .suggestion(finding.getSuggestion())
                .source(finding.getSource())
                .confidence(finding.getConfidence())
                .build();
    }
}
