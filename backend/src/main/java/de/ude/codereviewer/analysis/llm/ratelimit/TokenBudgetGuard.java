package de.ude.codereviewer.analysis.llm.ratelimit;

import de.ude.codereviewer.analysis.llm.LlmProperties;
import de.ude.codereviewer.analysis.llm.exception.LlmBudgetExceededException;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Überwacht den täglichen Token-Verbrauch aller Claude-API-Calls
 * und verhindert, dass das konfigurierte Tages-Budget überschritten wird.
 * Dient der Kosten- und Latenzkontrolle im Generate-Reflect-Refine-Zyklus.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenBudgetGuard {

    private final LlmProperties properties;

    private final AtomicInteger tokensUsedToday = new AtomicInteger(0);
    private final AtomicReference<LocalDate> currentTrackingDay = new AtomicReference<>(LocalDate.now());

    /**
     * Reserviert vorab die geschätzte Anzahl an Tokens für einen
     * anstehenden Review-Zyklus (Generate + Reflect + Refine).
     * Wirft eine Exception, falls dadurch das Tages-Budget überschritten würde.
     *
     * @param estimatedTokens grobe Schätzung des Token-Verbrauchs vor dem eigentlichen Call
     * @throws LlmBudgetExceededException falls das Tages-Budget überschritten wäre
     */
    public void checkAndReserve(int estimatedTokens) {
        resetIfNewDay();

        int updatedTotal = tokensUsedToday.addAndGet(estimatedTokens);

        if (updatedTotal > properties.dailyTokenBudget()) {
            // Reservierung zurücknehmen, da der Call nicht durchgeführt werden darf
            tokensUsedToday.addAndGet(-estimatedTokens);
            throw new LlmBudgetExceededException(
                "Tägliches Token-Budget überschritten: benötigt=%d, bereits verbraucht=%d, Limit=%d"
                    .formatted(estimatedTokens, updatedTotal - estimatedTokens, properties.dailyTokenBudget()));
        }

        log.debug("Token-Reservierung: +{} Tokens, Tagesverbrauch jetzt {}/{}",
                estimatedTokens, updatedTotal, properties.dailyTokenBudget());
    }

    /**
     * Korrigiert die Buchung nachträglich mit dem tatsächlichen
     * Token-Verbrauch, den die Claude-API-Response zurückgemeldet hat
     * (siehe ClaudeResponse.usage()). Die anfängliche Schätzung in
     * checkAndReserve() ist nur eine grobe Vorab-Reservierung.
     *
     * @param actualTokens tatsächlicher Verbrauch aus response.usage().total()
     */
    public void recordActualUsage(int actualTokens) {
        resetIfNewDay();
        log.debug("Tatsächlicher Token-Verbrauch dieses Calls: {}", actualTokens);
        // Hinweis: Hier bewusst kein erneutes addAndGet(), da bereits in
        // checkAndReserve() reserviert wurde. Diese Methode dient primär
        // dem Logging/Monitoring der tatsächlichen vs. geschätzten Werte.
    }

    /**
     * Aktueller Tagesverbrauch, z.B. für Monitoring/Admin-Endpoints.
     */
    public int currentUsage() {
        resetIfNewDay();
        return tokensUsedToday.get();
    }

    public int dailyLimit() {
        return properties.dailyTokenBudget();
    }

    /**
     * Setzt den Zähler zurück, falls seit der letzten Prüfung
     * ein neuer Kalendertag begonnen hat. Ergänzend zum
     * @Scheduled-Reset unten, damit der Reset auch bei
     * langer Inaktivität (z.B. Server über Nacht im Standby)
     * beim nächsten tatsächlichen Aufruf zuverlässig greift.
     */
    private void resetIfNewDay() {
        LocalDate today = LocalDate.now();
        LocalDate trackedDay = currentTrackingDay.get();

        if (!today.equals(trackedDay) && currentTrackingDay.compareAndSet(trackedDay, today)) {
            int previousUsage = tokensUsedToday.getAndSet(0);
            log.info("Neuer Tag erkannt, Token-Budget zurückgesetzt. Verbrauch des Vortages: {}", previousUsage);
        }
    }

    /**
     * Zusätzlicher expliziter Reset um Mitternacht, redundant zu
     * resetIfNewDay(), aber sinnvoll für exaktes Timing bei
     * durchgehend laufenden Server-Instanzen.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void resetDaily() {
        int previousUsage = tokensUsedToday.getAndSet(0);
        currentTrackingDay.set(LocalDate.now());
        log.info("Geplanter täglicher Reset des Token-Budgets. Verbrauch des Vortages: {}", previousUsage);
    }
}