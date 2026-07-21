package de.ude.codereviewer.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredFileDto {
    private Long id;
    private String filePath;
    private Long sizeBytes;
    private String content;
}
