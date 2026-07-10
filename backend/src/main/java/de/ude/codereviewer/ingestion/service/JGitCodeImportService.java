package de.ude.codereviewer.ingestion.service;

import de.ude.codereviewer.ingestion.config.IngestionProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JGitCodeImportService implements GitCodeImportService {

    private static final String JAVA_EXTENSION = IngestionFileUtils.JAVA_EXTENSION;
    private static final int CLONE_TIMEOUT_SECONDS = 30;

    private final IngestionProperties properties;

    public JGitCodeImportService(IngestionProperties properties) {
        this.properties = properties;
    }

    @Override
    public IngestionResult importFromUrl(Long reviewRunId, String repositoryUrl) {
        String url = validateUrl(repositoryUrl);

        Path targetDir = Path.of(properties.storageDir(), String.valueOf(reviewRunId))
                .toAbsolutePath()
                .normalize();
        Path cloneDir;
        try {
            Files.createDirectories(targetDir);
            cloneDir = Files.createTempDirectory("git-import-" + reviewRunId + "-");
        } catch (IOException e) {
            throw new UncheckedIOException("Verzeichnis konnte nicht angelegt werden.", e);
        }

        try {
            cloneShallow(url, cloneDir);
            return copyJavaFiles(cloneDir, targetDir);
        } catch (GitAPIException e) {
            IngestionFileUtils.deleteQuietly(targetDir);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Git-Repository konnte nicht importiert werden: " + e.getMessage());
        } finally {
            IngestionFileUtils.deleteQuietly(cloneDir);
        }
    }

    private void cloneShallow(String repositoryUrl, Path cloneDir) throws GitAPIException {
        try (Git git = Git.cloneRepository()
                .setURI(repositoryUrl)
                .setDirectory(cloneDir.toFile())
                .setDepth(1)
                .setCloneAllBranches(false)
                .setTimeout(CLONE_TIMEOUT_SECONDS)
                .call()) {
            // clone succeeded; the handle is only needed to release repository resources
        }
    }

    private IngestionResult copyJavaFiles(Path cloneDir, Path targetDir) {
        List<String> files = new ArrayList<>();
        long totalBytes = 0;
        int count = 0;

        try (Stream<Path> walk = Files.walk(cloneDir)) {
            Iterator<Path> it = walk.filter(Files::isRegularFile)
                    .filter(p -> !cloneDir.relativize(p).startsWith(".git"))
                    .filter(p -> p.toString().toLowerCase().endsWith(JAVA_EXTENSION))
                    .iterator();

            while (it.hasNext()) {
                Path source = it.next();
                count++;
                if (count > properties.maxZipEntries()) {
                    IngestionFileUtils.deleteQuietly(targetDir);
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Repository enthält zu viele Java-Dateien (Limit: " + properties.maxZipEntries() + ").");
                }

                Path relative = cloneDir.relativize(source);
                Path destination = targetDir.resolve(relative.toString()).normalize();
                if (!destination.startsWith(targetDir)) {
                    continue;
                }

                Files.createDirectories(destination.getParent());
                try (InputStream in = Files.newInputStream(source)) {
                    totalBytes +=
                            IngestionFileUtils.copyBounded(in, destination, properties.maxExtractedBytes() - totalBytes);
                }
                files.add(relative.toString());
            }
        } catch (IngestionFileUtils.SizeLimitExceededException e) {
            IngestionFileUtils.deleteQuietly(targetDir);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Repository überschreitet maximale Größe von " + properties.maxExtractedBytes() + " Bytes.");
        } catch (IOException e) {
            IngestionFileUtils.deleteQuietly(targetDir);
            throw new UncheckedIOException("Repository konnte nicht verarbeitet werden.", e);
        }

        if (files.isEmpty()) {
            IngestionFileUtils.deleteQuietly(targetDir);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository enthält keine Java-Dateien.");
        }

        return new IngestionResult(targetDir.toString(), files, totalBytes);
    }

    // Restricts imports to public https:// hosts and rejects loopback/private/link-local
    // targets to prevent the server from being used as an SSRF proxy into internal networks.
    private String validateUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository-URL fehlt.");
        }

        String trimmed = repositoryUrl.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Repository-URL.");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nur https-URLs werden unterstützt.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Repository-URL.");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isAnyLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ziel-Host nicht erlaubt.");
                }
            }
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Host konnte nicht aufgelöst werden: " + host);
        }

        return trimmed;
    }
}
