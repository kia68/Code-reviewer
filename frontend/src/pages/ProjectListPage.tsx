import { useNavigate } from "react-router";
import { ProjectSelector } from "../components/ProjectSelector";
import type { Project } from "../types/api";

export function ProjectListPage() {
  const navigate = useNavigate();

  const handleSelect = (project: Project) => {
    navigate(`/projects/${project.id}`);
  };

  return (
    <div>
      <h1>Projects</h1>
      <p style={{ marginTop: 0, marginBottom: 24 }}>
        Select an existing project or create a new one to start a review.
      </p>
      <ProjectSelector onSelect={handleSelect} />
    </div>
  );
}
