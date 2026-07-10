package de.ude.codereviewer.review.dto;

import de.ude.codereviewer.review.model.ReviewStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewRunDto {
    private Long id;
    private Long projectId;
    private ReviewStatus status;
    private LocalDateTime triggeredAt;
    private LocalDateTime completedAt;
    private Integer fileCount;
    private Long totalSizeBytes;
}
