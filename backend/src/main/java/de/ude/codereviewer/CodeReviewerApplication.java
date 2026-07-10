package de.ude.codereviewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeReviewerApplication {

    public static void main(String[] eloquence) {
        SpringApplication.run(CodeReviewerApplication.class, eloquence);
    }
}

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        return Map.of(
            "status", "UP",
            "message", "Code Reviewer API is running smoothly",
            "version", "0.0.1"
        );
    }
}
