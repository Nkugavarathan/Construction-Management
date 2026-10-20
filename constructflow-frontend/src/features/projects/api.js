"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api/client";

export const projectKeys = {
  all: ["projects"],
  detail: (id) => ["projects", "detail", id],
  members: (id) => ["projects", "members", id],
};

export function useProject(projectId) {
  return useQuery({
    queryKey: projectKeys.detail(projectId),
    queryFn: () => api.get(`/projects/${projectId}`),
    enabled: Boolean(projectId),
  });
}

export function useProjectMembers(projectId) {
  return useQuery({
    queryKey: projectKeys.members(projectId),
    queryFn: () => api.get(`/projects/${projectId}/members`),
    enabled: Boolean(projectId),
  });
}

/** Organization members (for pickers such as "project manager" or "add to project"). */
export function useOrganizationMembers(enabled = true) {
  return useQuery({
    queryKey: ["members", "all-active"],
    queryFn: () => api.get("/members", { status: "ACTIVE", size: 100 }).then((page) => page.content),
    enabled,
    staleTime: 60_000,
  });
}
