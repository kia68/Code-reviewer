package de.ude.codereviewer.review.controller;

import de.ude.codereviewer.analysis.ast.AstParseReport;
import de.ude.codereviewer.analysis.smell.SmellReport;
import de.ude.codereviewer.review.dto.FindingDto;
import de.ude.codereviewer.review.dto.GitImportRequest;
import de.ude.codereviewer.review.dto.ReviewRunDto;
import de.ude.codereviewer.review.service.ReviewRunService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/review-runs")
public class ReviewRunController {

    private final ReviewRunService reviewRunService;

    public ReviewRunController(ReviewRunService reviewRunService) {
        this.reviewRunService = reviewRunService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewRunDto createReviewRun(@PathVariable Long projectId, @RequestParam("file") MultipartFile file) {
        return reviewRunService.ingest(projectId, file);
    }

    @PostMapping(value = "/from-git", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewRunDto createReviewRunFromGit(@PathVariable Long projectId, @RequestBody GitImportRequest request) {
        return reviewRunService.ingestFromGit(projectId, request.repositoryUrl());
    }

    @GetMapping
    public List<ReviewRunDto> getReviewRuns(@PathVariable Long projectId) {
        return reviewRunService.getAllForProject(projectId);
    }

    @GetMapping("/{id}")
    public ReviewRunDto getReviewRunById(@PathVariable Long projectId, @PathVariable Long id) {
        return reviewRunService.getById(projectId, id);
    }

    @GetMapping("/{id}/ast")
    public AstParseReport getAst(@PathVariable Long projectId, @PathVariable Long id) {
        return reviewRunService.getAstReport(projectId, id);
    }

    @GetMapping("/{id}/smells")
    public SmellReport getSmells(@PathVariable Long projectId, @PathVariable Long id) {
        return reviewRunService.getSmellReport(projectId, id);
    }

    @PostMapping("/{id}/findings")
    public List<FindingDto> analyzeFindings(@PathVariable Long projectId, @PathVariable Long id) {
        return reviewRunService.analyzeFindings(projectId, id);
    }

    @GetMapping("/{id}/findings")
    public List<FindingDto> getFindings(@PathVariable Long projectId, @PathVariable Long id) {
        return reviewRunService.getFindings(projectId, id);
    }
}
