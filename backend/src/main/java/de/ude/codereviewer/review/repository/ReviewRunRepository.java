package de.ude.codereviewer.review.repository;

import de.ude.codereviewer.review.model.ReviewRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewRunRepository extends JpaRepository<ReviewRun, Long> {
    List<ReviewRun> findByProjectIdOrderByTriggeredAtDesc(Long projectId);
}
