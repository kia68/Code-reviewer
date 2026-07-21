package de.ude.codereviewer.review.repository;

import de.ude.codereviewer.review.model.StoredFile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    List<StoredFile> findByReviewRunIdOrderByFilePath(Long reviewRunId);

    Optional<StoredFile> findByReviewRunIdAndFilePath(Long reviewRunId, String filePath);

    void deleteByReviewRunId(Long reviewRunId);

    long countByReviewRunId(Long reviewRunId);
}
