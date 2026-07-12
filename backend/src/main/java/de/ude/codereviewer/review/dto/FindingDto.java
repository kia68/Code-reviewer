package de.ude.codereviewer.review.dto;

import de.ude.codereviewer.review.model.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindingDto {
    private Long id;
    private Long reviewRunId;
    private String filePath;
    private Integer lineNumber;
    private String category;
    private Severity severity;
    private String description;
    private String suggestion;
}
