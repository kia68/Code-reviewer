package de.ude.codereviewer.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import de.ude.codereviewer.review.model.Severity;
import de.ude.codereviewer.review.repository.FindingRepository;
import de.ude.codereviewer.review.repository.ReviewRunRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReviewRunServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long REVIEW_RUN_ID = 42L;

    @Mock
    private ReviewRunRepository reviewRunRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private FindingRepository findingRepository;

    @Mock
    private CodeStorageService codeStorageService;

    @Mock
    private GitCodeImportService gitCodeImportService;

    @Mock
    private AstParserService astParserService;

    @Mock
    private SmellDetectionService smellDetectionService;

    @InjectMocks
    private ReviewRunService reviewRunService;

    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(PROJECT_ID)
                .name("Demo")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ReviewRun persistedRun(String sourcePath, ReviewStatus status) {
        return ReviewRun.builder()
                .id(REVIEW_RUN_ID)
                .project(project)
                .status(status)
                .triggeredAt(LocalDateTime.now())
                .sourcePath(sourcePath)
                .build();
    }

    private MockMultipartFile javaFile() {
        return new MockMultipartFile("file", "Hello.java", "text/plain", "class Hello {}".getBytes());
    }

    @Test
    void ingestMarksRunCompletedAndCopiesIngestionResult() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(reviewRunRepository.save(any(ReviewRun.class))).thenAnswer(invocation -> {
            ReviewRun run = invocation.getArgument(0);
            run.setId(REVIEW_RUN_ID);
            return run;
        });
        when(codeStorageService.store(anyLong(), any()))
                .thenReturn(new IngestionResult("/data/42", List.of("Hello.java"), 123L));

        ReviewRunDto dto = reviewRunService.ingest(PROJECT_ID, javaFile());

        assertThat(dto.getStatus()).isEqualTo(ReviewStatus.COMPLETED);
        assertThat(dto.getFileCount()).isEqualTo(1);
        assertThat(dto.getTotalSizeBytes()).isEqualTo(123L);
        assertThat(dto.getCompletedAt()).isNotNull();
    }

    // The run row must survive a failed ingestion as FAILED, so a broken upload leaves an audit
    // trail instead of vanishing - this is why runIngestion() deliberately isn't @Transactional.
    @Test
    void ingestPersistsFailedRunAndRethrowsWhenStorageFails() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(reviewRunRepository.save(any(ReviewRun.class))).thenAnswer(invocation -> {
            ReviewRun run = invocation.getArgument(0);
            run.setId(REVIEW_RUN_ID);
            return run;
        });
        when(codeStorageService.store(anyLong(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nicht unterstütztes Dateiformat"));

        assertThatThrownBy(() -> reviewRunService.ingest(PROJECT_ID, javaFile()))
                .isInstanceOf(ResponseStatusException.class);

        ArgumentCaptor<ReviewRun> captor = ArgumentCaptor.forClass(ReviewRun.class);
        verify(reviewRunRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        ReviewRun lastSaved = captor.getValue();
        assertThat(lastSaved.getStatus()).isEqualTo(ReviewStatus.FAILED);
        assertThat(lastSaved.getCompletedAt()).isNotNull();
    }

    @Test
    void ingestFromGitDelegatesToGitImportService() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(reviewRunRepository.save(any(ReviewRun.class))).thenAnswer(invocation -> {
            ReviewRun run = invocation.getArgument(0);
            run.setId(REVIEW_RUN_ID);
            return run;
        });
        when(gitCodeImportService.importFromUrl(REVIEW_RUN_ID, "https://github.com/example/repo.git"))
                .thenReturn(new IngestionResult("/data/42", List.of("A.java", "B.java"), 200L));

        ReviewRunDto dto = reviewRunService.ingestFromGit(PROJECT_ID, "https://github.com/example/repo.git");

        assertThat(dto.getFileCount()).isEqualTo(2);
        verify(codeStorageService, never()).store(anyLong(), any());
    }

    @Test
    void ingestRejectsUnknownProject() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewRunService.ingest(PROJECT_ID, javaFile()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(reviewRunRepository, never()).save(any());
    }

    // A run belonging to another project must not be readable through this project's path,
    // otherwise project ids would be a trivial enumeration vector across tenants.
    @Test
    void getByIdRejectsRunOwnedByAnotherProject() {
        Project otherProject = Project.builder().id(999L).name("Other").build();
        ReviewRun foreignRun = ReviewRun.builder()
                .id(REVIEW_RUN_ID)
                .project(otherProject)
                .status(ReviewStatus.COMPLETED)
                .build();
        when(reviewRunRepository.findById(REVIEW_RUN_ID)).thenReturn(Optional.of(foreignRun));

        assertThatThrownBy(() -> reviewRunService.getById(PROJECT_ID, REVIEW_RUN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getSmellReportRejectsRunWithoutStoredSource() {
        when(reviewRunRepository.findById(REVIEW_RUN_ID)).thenReturn(Optional.of(persistedRun(null, ReviewStatus.FAILED)));

        assertThatThrownBy(() -> reviewRunService.getSmellReport(PROJECT_ID, REVIEW_RUN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        verify(smellDetectionService, never()).detectSmells(any());
    }

    // Re-running analysis must replace the previous batch, not append to it.
    @Test
    void analyzeFindingsClearsPreviousFindingsBeforeSaving() {
        ReviewRun run = persistedRun("/data/42", ReviewStatus.COMPLETED);
        when(reviewRunRepository.findById(REVIEW_RUN_ID)).thenReturn(Optional.of(run));
        when(smellDetectionService.detectSmells(any()))
                .thenReturn(new SmellReport(List.of(new DetectedSmell(
                        "Hello.java", 3, "UNUSED_VARIABLE", Severity.INFO, "unused", "remove it"))));
        when(findingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<FindingDto> findings = reviewRunService.analyzeFindings(PROJECT_ID, REVIEW_RUN_ID);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(findingRepository);
        inOrder.verify(findingRepository).deleteByReviewRunId(REVIEW_RUN_ID);
        inOrder.verify(findingRepository).saveAll(any());

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getCategory()).isEqualTo("UNUSED_VARIABLE");
        assertThat(findings.get(0).getSeverity()).isEqualTo(Severity.INFO);
    }

    @Test
    void analyzeFindingsMapsSmellFieldsOntoFindingEntity() {
        ReviewRun run = persistedRun("/data/42", ReviewStatus.COMPLETED);
        when(reviewRunRepository.findById(REVIEW_RUN_ID)).thenReturn(Optional.of(run));
        when(smellDetectionService.detectSmells(any()))
                .thenReturn(new SmellReport(List.of(new DetectedSmell(
                        "Deep.java", 12, "DEEP_NESTING", Severity.WARNING, "too deep", "extract method"))));
        when(findingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        reviewRunService.analyzeFindings(PROJECT_ID, REVIEW_RUN_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Finding>> captor = ArgumentCaptor.forClass(List.class);
        verify(findingRepository).saveAll(captor.capture());
        Finding saved = captor.getValue().get(0);

        assertThat(saved.getReviewRun()).isSameAs(run);
        assertThat(saved.getFilePath()).isEqualTo("Deep.java");
        assertThat(saved.getLineNumber()).isEqualTo(12);
        assertThat(saved.getCategory()).isEqualTo("DEEP_NESTING");
        assertThat(saved.getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(saved.getDescription()).isEqualTo("too deep");
        assertThat(saved.getSuggestion()).isEqualTo("extract method");
    }

    @Test
    void getAllForProjectRejectsUnknownProject() {
        when(projectRepository.existsById(PROJECT_ID)).thenReturn(false);

        assertThatThrownBy(() -> reviewRunService.getAllForProject(PROJECT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
