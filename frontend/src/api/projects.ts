import type { Project } from "../types/api";
import { apiClient } from "./client";

export async function createProject(name: string): Promise<Project> {
  return apiClient.post<Project>("/projects", { name });
}

export async function getProjects(): Promise<Project[]> {
  return apiClient.get<Project[]>("/projects");
}

export async function getProject(id: number): Promise<Project> {
  return apiClient.get<Project>(`/projects/${id}`);
}
