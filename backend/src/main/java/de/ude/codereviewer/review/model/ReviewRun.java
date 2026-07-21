package de.ude.codereviewer.review.model;

import de.ude.codereviewer.project.model.Project;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "review_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReviewStatus status;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "source_path", length = 1024)
    private String sourcePath;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "total_size_bytes")
    private Long totalSizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_run_id")
    private ReviewRun parentRun;

    @Builder.Default
    @OneToMany(mappedBy = "reviewRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Finding> findings = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "reviewRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoredFile> storedFiles = new ArrayList<>();

    public void addFinding(Finding finding) {
        findings.add(finding);
        finding.setReviewRun(this);
    }

    public void removeFinding(Finding finding) {
        findings.remove(finding);
        finding.setReviewRun(null);
    }

    public void addStoredFile(StoredFile storedFile) {
        storedFiles.add(storedFile);
        storedFile.setReviewRun(this);
    }

    public void removeStoredFile(StoredFile storedFile) {
        storedFiles.remove(storedFile);
        storedFile.setReviewRun(null);
    }
}
