package de.ude.codereviewer.review.repository;

import de.ude.codereviewer.review.model.Finding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FindingRepository extends JpaRepository<Finding, Long> {
    List<Finding> findByReviewRunId(Long reviewRunId);

    void deleteByReviewRunId(Long reviewRunId);
}
