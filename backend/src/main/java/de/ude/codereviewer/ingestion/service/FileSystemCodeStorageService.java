package de.ude.codereviewer.ingestion.service;

import de.ude.codereviewer.ingestion.config.IngestionProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileSystemCodeStorageService implements CodeStorageService {

    private static final String JAVA_EXTENSION = ".java";
    private static final String ZIP_EXTENSION = ".zip";

    private final IngestionProperties properties;

    public FileSystemCodeStorageService(IngestionProperties properties) {
        this.properties = properties;
    }

    @Override
    public IngestionResult store(Long reviewRunId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Datei ist leer.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dateiname fehlt.");
        }

        String lowerName = originalName.toLowerCase();
        Path targetDir = Path.of(properties.storageDir(), String.valueOf(reviewRunId))
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Speicherverzeichnis konnte nicht angelegt werden.", e);
        }

        if (lowerName.endsWith(JAVA_EXTENSION)) {
            return storeSingleFile(file, targetDir);
        }
        if (lowerName.endsWith(ZIP_EXTENSION)) {
            return storeZipArchive(file, targetDir);
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Nicht unterstütztes Dateiformat '" + originalName + "'. Erlaubt sind .java und .zip.");
    }

    private IngestionResult storeSingleFile(MultipartFile file, Path targetDir) {
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Datei überschreitet maximale Größe von " + properties.maxFileSizeBytes() + " Bytes.");
        }

        String fileName = Path.of(file.getOriginalFilename()).getFileName().toString();
        Path targetFile = targetDir.resolve(fileName);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Datei konnte nicht gespeichert werden.", e);
        }

        return new IngestionResult(targetDir.toString(), List.of(fileName), file.getSize());
    }

    private IngestionResult storeZipArchive(MultipartFile file, Path targetDir) {
        if (file.getSize() > properties.maxZipSizeBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ZIP-Datei überschreitet maximale Größe von " + properties.maxZipSizeBytes() + " Bytes.");
        }

        List<String> extractedFiles = new ArrayList<>();
        long totalExtractedBytes = 0;
        int entryCount = 0;

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > properties.maxZipEntries()) {
                    deleteQuietly(targetDir);
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "ZIP enthält zu viele Einträge (Limit: " + properties.maxZipEntries() + ").");
                }

                if (entry.isDirectory()) {
                    continue;
                }

                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(targetDir)) {
                    deleteQuietly(targetDir);
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Ungültiger ZIP-Eintrag: " + entry.getName());
                }

                if (!entry.getName().toLowerCase().endsWith(JAVA_EXTENSION)) {
                    continue;
                }

                Files.createDirectories(entryPath.getParent());
                long writtenBytes = copyBounded(zis, entryPath, properties.maxExtractedBytes() - totalExtractedBytes);
                totalExtractedBytes += writtenBytes;
                extractedFiles.add(targetDir.relativize(entryPath).toString());
            }
        } catch (ZipBombException e) {
            deleteQuietly(targetDir);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ZIP überschreitet maximale entpackte Größe von " + properties.maxExtractedBytes() + " Bytes.");
        } catch (IOException e) {
            deleteQuietly(targetDir);
            throw new UncheckedIOException("ZIP konnte nicht verarbeitet werden.", e);
        }

        if (extractedFiles.isEmpty()) {
            deleteQuietly(targetDir);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZIP enthält keine Java-Dateien.");
        }

        return new IngestionResult(targetDir.toString(), extractedFiles, totalExtractedBytes);
    }

    private long copyBounded(InputStream in, Path target, long remainingBudget) throws IOException {
        byte[] buffer = new byte[8192];
        long written = 0;
        try (OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                written += read;
                if (written > remainingBudget) {
                    throw new ZipBombException();
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }

    private void deleteQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
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

    private static final class ZipBombException extends IOException {
    }
}
