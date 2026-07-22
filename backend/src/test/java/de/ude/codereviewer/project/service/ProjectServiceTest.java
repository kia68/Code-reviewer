package de.ude.codereviewer.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.ude.codereviewer.project.dto.ProjectDto;
import de.ude.codereviewer.project.model.Project;
import de.ude.codereviewer.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createProjectTrimsNameBeforePersisting() {
        when(projectRepository.findByName("Demo")).thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(1L);
            return project;
        });

        ProjectDto dto = projectService.createProject("  Demo  ");

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Demo");
        assertThat(dto.getName()).isEqualTo("Demo");
        assertThat(dto.getCreatedAt()).isNotNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void createProjectRejectsBlankNames(String name) {
        assertThatThrownBy(() -> projectService.createProject(name))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verify(projectRepository, never()).save(any());
    }

    @Test
    void createProjectRejectsDuplicateName() {
        when(projectRepository.findByName("Demo"))
                .thenReturn(Optional.of(Project.builder().id(1L).name("Demo").build()));

        assertThatThrownBy(() -> projectService.createProject("Demo"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        verify(projectRepository, never()).save(any());
    }

    // The duplicate check has to run against the trimmed name, otherwise " Demo " would slip
    // past it and create a second project that looks identical to the user.
    @Test
    void createProjectDetectsDuplicateAfterTrimming() {
        when(projectRepository.findByName("Demo"))
                .thenReturn(Optional.of(Project.builder().id(1L).name("Demo").build()));

        assertThatThrownBy(() -> projectService.createProject("  Demo  "))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void getProjectByIdRejectsUnknownId() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getAllProjectsMapsEveryProjectToDto() {
        when(projectRepository.findAll())
                .thenReturn(List.of(
                        Project.builder().id(1L).name("A").createdAt(LocalDateTime.now()).build(),
                        Project.builder().id(2L).name("B").createdAt(LocalDateTime.now()).build()));

        List<ProjectDto> projects = projectService.getAllProjects();

        assertThat(projects).extracting(ProjectDto::getName).containsExactly("A", "B");
        assertThat(projects).extracting(ProjectDto::getId).containsExactly(1L, 2L);
    }
}
