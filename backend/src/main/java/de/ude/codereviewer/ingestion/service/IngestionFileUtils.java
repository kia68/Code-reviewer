package de.ude.codereviewer.ingestion.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

final class IngestionFileUtils {

    static final String JAVA_EXTENSION = ".java";

    private IngestionFileUtils() { }

    static long copyBounded(InputStream in, Path target, long remainingBudget) throws IOException {
        byte[] buffer = new byte[8192];
        long written = 0;
        try (OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                written += read;
                if (written > remainingBudget) {
                    throw new SizeLimitExceededException();
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }

    static void deleteQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    static final class SizeLimitExceededException extends IOException { }
}
