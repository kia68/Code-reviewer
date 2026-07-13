package de.ude.codereviewer.analysis.llm.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.ude.codereviewer.analysis.llm.LlmProperties;
import de.ude.codereviewer.review.Finding;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * In-Memory-Cache für vollständige LLM-Review-Ergebnisse.
 * Verhindert wiederholte, kostenpflichtige Claude-API-Calls,
 * wenn identischer Code erneut analysiert wird.
 */
@Component
public class LlmReviewCache {

    private final Cache<String, List<Finding>> caffeineCache;

    public LlmReviewCache(LlmProperties properties) {
        this.caffeineCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(properties.cacheTtlHours()))
            .build();
    }

    /**
     * Erstellt einen eindeutigen Cache-Key aus Quellcode-Inhalt,
     * Prompt-Version und Modell-Version. Ändert sich der Code
     * auch nur minimal, ändert sich der Hash und damit der Key.
     */
    public String buildKey(String sourceCode, String promptVersion, String model) {
        return DigestUtils.sha256Hex(sourceCode + "::" + promptVersion + "::" + model);
    }

    /**
     * Liefert das gecachte Ergebnis für den gegebenen Key,
     * oder führt den Loader aus und cached das Ergebnis, falls
     * noch kein Eintrag existiert.
     */
    public List<Finding> get(String key, Supplier<List<Finding>> loader) {
        return caffeineCache.get(key, k -> loader.get());
    }

    /**
     * Nützlich für Tests oder manuelles Invalidieren einzelner Einträge.
     */
    public void invalidate(String key) {
        caffeineCache.invalidate(key);
    }

    /**
     * Nützlich für Tests oder Admin-Endpoints.
     */
    public long estimatedSize() {
        return caffeineCache.estimatedSize();
    }
}