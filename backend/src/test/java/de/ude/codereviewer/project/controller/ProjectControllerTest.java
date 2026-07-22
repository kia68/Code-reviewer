package de.ude.codereviewer.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ude.codereviewer.project.model.Project;
import de.ude.codereviewer.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        projectRepository.deleteAll();
    }

    @Test
    void shouldCreateProjectSuccessfully() throws Exception {
        ProjectController.CreateProjectRequest request = new ProjectController.CreateProjectRequest();
        request.setName("New Project");

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("New Project"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void shouldReturnBadRequestWhenProjectNameIsEmpty() throws Exception {
        ProjectController.CreateProjectRequest request = new ProjectController.CreateProjectRequest();
        request.setName("   ");

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnConflictWhenProjectNameExists() throws Exception {
        Project existingProject = Project.builder()
                .name("Existing Project")
                .createdAt(LocalDateTime.now())
                .build();
        projectRepository.save(existingProject);

        ProjectController.CreateProjectRequest request = new ProjectController.CreateProjectRequest();
        request.setName("Existing Project");

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnAllProjects() throws Exception {
        Project p1 = Project.builder().name("Proj 1").createdAt(LocalDateTime.now()).build();
        Project p2 = Project.builder().name("Proj 2").createdAt(LocalDateTime.now()).build();
        projectRepository.save(p1);
        projectRepository.save(p2);

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Proj 1"))
                .andExpect(jsonPath("$[1].name").value("Proj 2"));
    }

    @Test
    void shouldReturnProjectById() throws Exception {
        Project p = Project.builder().name("Specific Proj").createdAt(LocalDateTime.now()).build();
        Project saved = projectRepository.save(p);

        mockMvc.perform(get("/api/projects/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Specific Proj"));
    }

    @Test
    void shouldReturnNotFoundForInvalidId() throws Exception {
        mockMvc.perform(get("/api/projects/999999"))
                .andExpect(status().isNotFound());
    }
}
