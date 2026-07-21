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
import de.ude.codereviewer.review.model.Finding;
import de.ude.codereviewer.review.model.ReviewRun;
import de.ude.codereviewer.review.model.ReviewStatus;
import de.ude.codereviewer.review.repository.FindingRepository;
import de.ude.codereviewer.review.repository.ReviewRunRepository;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReviewRunService {

    private final ReviewRunRepository reviewRunRepository;
    private final ProjectRepository projectRepository;
    private final FindingRepository findingRepository;
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

        return toDto(reviewRun);
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
        SmellReport report = smellDetectionService.detectSmells(sourcePath);

        findingRepository.deleteByReviewRunId(reviewRunId);
        List<Finding> findings = report.smells().stream()
                .map(smell -> toFinding(reviewRun, smell))
                .toList();

        return findingRepository.saveAll(findings).stream().map(this::toFindingDto).toList();
    }

    @Transactional(readOnly = true)
    public List<FindingDto> getFindings(Long projectId, Long reviewRunId) {
        findOwnedReviewRun(projectId, reviewRunId);
        return findingRepository.findByReviewRunId(reviewRunId).stream()
                .map(this::toFindingDto)
                .toList();
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
                .build();
    }
}
