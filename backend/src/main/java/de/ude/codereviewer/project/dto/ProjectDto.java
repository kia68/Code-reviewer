package de.ude.codereviewer.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectDto {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
}
