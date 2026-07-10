package de.ude.codereviewer.review.service;

import de.ude.codereviewer.ingestion.service.CodeStorageService;
import de.ude.codereviewer.ingestion.service.IngestionResult;
import de.ude.codereviewer.project.model.Project;
import de.ude.codereviewer.project.repository.ProjectRepository;
import de.ude.codereviewer.review.dto.ReviewRunDto;
import de.ude.codereviewer.review.model.ReviewRun;
import de.ude.codereviewer.review.model.ReviewStatus;
import de.ude.codereviewer.review.repository.ReviewRunRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewRunService {

    private final ReviewRunRepository reviewRunRepository;
    private final ProjectRepository projectRepository;
    private final CodeStorageService codeStorageService;

    public ReviewRunService(
            ReviewRunRepository reviewRunRepository,
            ProjectRepository projectRepository,
            CodeStorageService codeStorageService) {
        this.reviewRunRepository = reviewRunRepository;
        this.projectRepository = projectRepository;
        this.codeStorageService = codeStorageService;
    }

    // Intentionally not @Transactional: the IN_PROGRESS row must be committed before storage
    // starts, and a storage failure must still leave a committed FAILED row for the audit trail.
    public ReviewRunDto ingest(Long projectId, MultipartFile file) {
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
            result = codeStorageService.store(reviewRun.getId(), file);
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
}
