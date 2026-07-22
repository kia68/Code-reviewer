import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createProject, deleteProject, getProjects } from "../api/projects";
import type { Project } from "../types/api";
import { ApiClientError } from "../api/client";
import "./ProjectSelector.css";

interface ProjectSelectorProps {
  onSelect: (project: Project) => void;
}

export function ProjectSelector({ onSelect }: ProjectSelectorProps) {
  const [newName, setNewName] = useState("");
  const queryClient = useQueryClient();

  const {
    data: projects,
    isLoading,
    error,
  } = useQuery({
    queryKey: ["projects"],
    queryFn: getProjects,
  });

  const createMutation = useMutation({
    mutationFn: createProject,
    onSuccess: (project) => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      setNewName("");
      onSelect(project);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteProject,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
    },
  });

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = newName.trim();
    if (!trimmed) return;
    createMutation.mutate(trimmed);
  };

  return (
    <div className="project-selector">
      <form className="project-selector-create" onSubmit={handleCreate}>
        <input
          type="text"
          className="project-selector-input"
          placeholder="New project name..."
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
        />
        <button
          type="submit"
          className="project-selector-button"
          disabled={!newName.trim() || createMutation.isPending}
        >
          {createMutation.isPending ? "Creating..." : "Create"}
        </button>
      </form>

      {createMutation.error && (
        <p className="project-selector-error">
          {(createMutation.error as ApiClientError).message}
        </p>
      )}

      {isLoading && <p className="project-selector-hint">Loading projects...</p>}

      {error && (
        <p className="project-selector-error">
          {(error as ApiClientError).message ?? "Failed to load projects."}
        </p>
      )}

      {projects && projects.length > 0 && (
        <div className="project-selector-list">
          <p className="project-selector-label">Existing projects</p>
          {projects.map((project) => (
            <div
              key={project.id}
              className="project-selector-item"
              onClick={() => onSelect(project)}
            >
              <div className="project-selector-info">
                <span className="project-selector-name">{project.name}</span>
                <span className="project-selector-date">
                  {new Date(project.createdAt).toLocaleDateString()}
                </span>
              </div>
              <button
                className="project-selector-delete"
                onClick={(e) => {
                  e.stopPropagation();
                  if (window.confirm(`Delete project "${project.name}" and all its review runs?`)) {
                    deleteMutation.mutate(project.id);
                  }
                }}
              >
                &times;
              </button>
            </div>
          ))}
        </div>
      )}

      {projects && projects.length === 0 && (
        <p className="project-selector-hint">
          No projects yet. Create one above to get started.
        </p>
      )}
    </div>
  );
}
