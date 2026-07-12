package de.ude.codereviewer.review.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ude.codereviewer.project.model.Project;
import de.ude.codereviewer.project.repository.ProjectRepository;
import de.ude.codereviewer.review.dto.GitImportRequest;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long createProject(String name) {
        Project project = projectRepository.save(
                Project.builder().name(name).createdAt(LocalDateTime.now()).build());
        return project.getId();
    }

    @Test
    void shouldIngestSingleJavaFile() throws Exception {
        Long projectId = createProject("Single File Project");
        MockMultipartFile file = new MockMultipartFile(
                "file", "Hello.java", "text/plain", "class Hello {}".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/projects/" + projectId + "/review-runs").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fileCount").value(1));
    }

    @Test
    void shouldIngestZipWithJavaFilesAndSkipOtherEntries() throws Exception {
        Long projectId = createProject("Zip Project");
        byte[] zipBytes = buildZip(
                new String[] {"src/A.java", "class A {}"},
                new String[] {"src/B.java", "class B {}"},
                new String[] {"README.txt", "not java"});
        MockMultipartFile zip = new MockMultipartFile("file", "project.zip", "application/zip", zipBytes);

        mockMvc.perform(multipart("/api/projects/" + projectId + "/review-runs").file(zip))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fileCount").value(2));
    }

    @Test
    void shouldRejectUnsupportedExtension() throws Exception {
        Long projectId = createProject("Bad Extension Project");
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/projects/" + projectId + "/review-runs").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectEmptyFile() throws Exception {
        Long projectId = createProject("Empty File Project");
        MockMultipartFile file = new MockMultipartFile("file", "Empty.java", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/projects/" + projectId + "/review-runs").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectZipWithPathTraversalEntry() throws Exception {
        Long projectId = createProject("Zip Slip Project");
        byte[] zipBytes = buildZip(new String[] {"../evil.java", "class Evil {}"});
        MockMultipartFile zip = new MockMultipartFile("file", "evil.zip", "application/zip", zipBytes);

        mockMvc.perform(multipart("/api/projects/" + projectId + "/review-runs").file(zip))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectZipWithoutJavaFiles() throws Exception {
        Long projectId = createProject("No Java Zip Project");
        byte[] zipBytes = buildZip(new String[] {"README.txt", "not java"});
        MockMultipartFile zip = new MockMultipartFile("file", "empty.zip", "application/zip", zipBytes);

        mockMvc.perform(multipart("/api/projects/" + projectId + "/review-runs").file(zip))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundForMissingProject() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "Hello.java", "text/plain", "class Hello {}".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/projects/999999/review-runs").file(file))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectNonHttpsGitUrl() throws Exception {
        Long projectId = createProject("Git Http Project");
        GitImportRequest request = new GitImportRequest("http://github.com/kia68/Code-reviewer.git");

        mockMvc.perform(post("/api/projects/" + projectId + "/review-runs/from-git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectBlankGitUrl() throws Exception {
        Long projectId = createProject("Git Blank Project");
        GitImportRequest request = new GitImportRequest("  ");

        mockMvc.perform(post("/api/projects/" + projectId + "/review-runs/from-git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectGitUrlPointingAtLoopbackHost() throws Exception {
        Long projectId = createProject("Git Loopback Project");
        GitImportRequest request = new GitImportRequest("https://127.0.0.1/internal.git");

        mockMvc.perform(post("/api/projects/" + projectId + "/review-runs/from-git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundForMissingProjectOnGitImport() throws Exception {
        GitImportRequest request = new GitImportRequest("https://github.com/kia68/Code-reviewer.git");

        mockMvc.perform(post("/api/projects/999999/review-runs/from-git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListAndFetchReviewRuns() throws Exception {
        Long projectId = createProject("List Project");
        MockMultipartFile file = new MockMultipartFile(
                "file", "Hello.java", "text/plain", "class Hello {}".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/projects/" + projectId + "/review-runs").file(file))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/projects/" + projectId + "/review-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void shouldReturnAstReportForIngestedJavaFile() throws Exception {
        Long projectId = createProject("Ast Project");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Greeter.java",
                "text/plain",
                "class Greeter { void greet() {} }".getBytes(StandardCharsets.UTF_8));

        String response = mockMvc.perform(multipart("/api/projects/" + projectId + "/review-runs").file(file))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long reviewRunId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/projects/" + projectId + "/review-runs/" + reviewRunId + "/ast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files", hasSize(1)))
                .andExpect(jsonPath("$.files[0].success").value(true))
                .andExpect(jsonPath("$.files[0].typeCount").value(1))
                .andExpect(jsonPath("$.files[0].methodCount").value(1));
    }

    @Test
    void shouldReturnConflictForAstOnReviewRunWithoutSource() throws Exception {
        Long projectId = createProject("Ast Failed Project");
        byte[] zipBytes = buildZip(new String[] {"README.txt", "not java"});
        MockMultipartFile zip = new MockMultipartFile("file", "empty.zip", "application/zip", zipBytes);

        mockMvc.perform(multipart("/api/projects/" + projectId + "/review-runs").file(zip))
                .andExpect(status().isBadRequest());

        String listResponse = mockMvc.perform(get("/api/projects/" + projectId + "/review-runs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long failedReviewRunId =
                objectMapper.readTree(listResponse).get(0).get("id").asLong();

        mockMvc.perform(get("/api/projects/" + projectId + "/review-runs/" + failedReviewRunId + "/ast"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundForAstOnMissingReviewRun() throws Exception {
        Long projectId = createProject("Ast Missing Run Project");

        mockMvc.perform(get("/api/projects/" + projectId + "/review-runs/999999/ast"))
                .andExpect(status().isNotFound());
    }

    private byte[] buildZip(String[]... entries) throws Exception {
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
}
