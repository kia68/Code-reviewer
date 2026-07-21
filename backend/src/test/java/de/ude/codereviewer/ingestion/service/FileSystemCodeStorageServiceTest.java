package de.ude.codereviewer.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.ude.codereviewer.ingestion.config.IngestionProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class FileSystemCodeStorageServiceTest {

    private static final Long REVIEW_RUN_ID = 7L;

    private FileSystemCodeStorageService serviceFor(Path storageDir) {
        return new FileSystemCodeStorageService(
                new IngestionProperties(storageDir.toString(), 2_000_000L, 20_000_000L, 2000, 50_000_000L));
    }

    private FileSystemCodeStorageService serviceWithLimits(
            Path storageDir, long maxFileSize, int maxEntries, long maxExtracted) {
        return new FileSystemCodeStorageService(
                new IngestionProperties(storageDir.toString(), maxFileSize, 20_000_000L, maxEntries, maxExtracted));
    }

    private byte[] zipOf(String[]... entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String[] entry : entries) {
                zos.putNextEntry(new ZipEntry(entry[0]));
                zos.write(entry[1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    @Test
    void storesSingleJavaFileOnDisk(@TempDir Path storageDir) {
        MockMultipartFile file =
                new MockMultipartFile("file", "Hello.java", "text/plain", "class Hello {}".getBytes(StandardCharsets.UTF_8));

        IngestionResult result = serviceFor(storageDir).store(REVIEW_RUN_ID, file);

        assertThat(result.relativeFilePaths()).containsExactly("Hello.java");
        assertThat(result.fileCount()).isEqualTo(1);
        assertThat(Path.of(result.sourcePath()).resolve("Hello.java")).exists();
    }

    // A crafted upload name like "../../evil.java" must not be able to write outside the
    // per-run storage directory.
    @Test
    void singleFileUploadStripsDirectoryTraversalFromFileName(@TempDir Path storageDir) throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../evil.java", "text/plain", "class Evil {}".getBytes(StandardCharsets.UTF_8));

        IngestionResult result = serviceFor(storageDir).store(REVIEW_RUN_ID, file);

        Path written = Path.of(result.sourcePath()).resolve("evil.java");
        assertThat(written).exists();
        assertThat(written.toRealPath()).startsWith(storageDir.toRealPath());
    }

    @Test
    void extractsOnlyJavaFilesFromZip(@TempDir Path storageDir) throws IOException {
        byte[] zip = zipOf(
                new String[] {"src/A.java", "class A {}"},
                new String[] {"src/B.java", "class B {}"},
                new String[] {"README.md", "# not java"});
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", zip);

        IngestionResult result = serviceFor(storageDir).store(REVIEW_RUN_ID, file);

        assertThat(result.fileCount()).isEqualTo(2);
        assertThat(Path.of(result.sourcePath()).resolve("README.md")).doesNotExist();
    }

    // Zip-slip: an entry escaping the target directory must be rejected outright rather than
    // silently written next to (or over) unrelated files.
    @Test
    void rejectsZipEntryEscapingTargetDirectory(@TempDir Path storageDir) throws IOException {
        byte[] zip = zipOf(new String[] {"../evil.java", "class Evil {}"});
        MockMultipartFile file = new MockMultipartFile("file", "evil.zip", "application/zip", zip);

        assertThatThrownBy(() -> serviceFor(storageDir).store(REVIEW_RUN_ID, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        assertThat(storageDir.resolve("evil.java")).doesNotExist();
    }

    // Zip-bomb guard: a small archive that expands past the budget must fail instead of
    // filling the disk.
    @Test
    void rejectsZipExceedingExtractedSizeBudget(@TempDir Path storageDir) throws IOException {
        String big = "class Big { /* " + "x".repeat(5000) + " */ }";
        byte[] zip = zipOf(new String[] {"Big.java", big});
        MockMultipartFile file = new MockMultipartFile("file", "bomb.zip", "application/zip", zip);

        assertThatThrownBy(() -> serviceWithLimits(storageDir, 2_000_000L, 2000, 100L).store(REVIEW_RUN_ID, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void rejectsZipWithTooManyEntries(@TempDir Path storageDir) throws IOException {
        byte[] zip = zipOf(
                new String[] {"A.java", "class A {}"},
                new String[] {"B.java", "class B {}"},
                new String[] {"C.java", "class C {}"});
        MockMultipartFile file = new MockMultipartFile("file", "many.zip", "application/zip", zip);

        assertThatThrownBy(() -> serviceWithLimits(storageDir, 2_000_000L, 2, 50_000_000L).store(REVIEW_RUN_ID, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void rejectsZipWithoutJavaFiles(@TempDir Path storageDir) throws IOException {
        byte[] zip = zipOf(new String[] {"README.md", "# nothing here"});
        MockMultipartFile file = new MockMultipartFile("file", "docs.zip", "application/zip", zip);

        assertThatThrownBy(() -> serviceFor(storageDir).store(REVIEW_RUN_ID, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void rejectsFileExceedingMaxSize(@TempDir Path storageDir) {
        MockMultipartFile file = new MockMultipartFile(
                "file", "Big.java", "text/plain", "class Big {}".repeat(100).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> serviceWithLimits(storageDir, 10L, 2000, 50_000_000L).store(REVIEW_RUN_ID, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void rejectsUnsupportedExtension(@TempDir Path storageDir) {
        MockMultipartFile file =
                new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> serviceFor(storageDir).store(REVIEW_RUN_ID, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void rejectsEmptyUpload(@TempDir Path storageDir) {
        MockMultipartFile file = new MockMultipartFile("file", "Empty.java", "text/plain", new byte[0]);

        assertThatThrownBy(() -> serviceFor(storageDir).store(REVIEW_RUN_ID, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void storesEachReviewRunInItsOwnDirectory(@TempDir Path storageDir) throws IOException {
        FileSystemCodeStorageService service = serviceFor(storageDir);
        MockMultipartFile file =
                new MockMultipartFile("file", "Hello.java", "text/plain", "class Hello {}".getBytes(StandardCharsets.UTF_8));

        IngestionResult first = service.store(1L, file);
        IngestionResult second = service.store(2L, file);

        assertThat(first.sourcePath()).isNotEqualTo(second.sourcePath());
        assertThat(Files.exists(Path.of(first.sourcePath()).resolve("Hello.java"))).isTrue();
        assertThat(Files.exists(Path.of(second.sourcePath()).resolve("Hello.java"))).isTrue();
    }
}
