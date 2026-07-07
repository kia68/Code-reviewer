package de.ude.codereviewer.project.service;

import de.ude.codereviewer.project.dto.ProjectDto;
import de.ude.codereviewer.project.model.Project;
import de.ude.codereviewer.project.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Autowired
    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectDto createProject(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project name cannot be empty");
        }

        String trimmedName = name.trim();
        if (projectRepository.findByName(trimmedName).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project with name '" + trimmedName + "' already exists");
        }

        Project project = Project.builder()
                .name(trimmedName)
                .createdAt(LocalDateTime.now())
                .build();

        Project savedProject = projectRepository.save(project);
        return mapToDto(savedProject);
    }

    public List<ProjectDto> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ProjectDto getProjectById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found with id: " + id));
        return mapToDto(project);
    }

    private ProjectDto mapToDto(Project project) {
        return ProjectDto.builder()
                .id(project.getId())
                .name(project.getName())
                .createdAt(project.getCreatedAt())
                .build();
    }
}
