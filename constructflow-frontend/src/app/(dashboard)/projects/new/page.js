"use client";

import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, ErrorState } from "@/components/feedback/States";
import { ProjectForm } from "@/features/projects/ProjectForm";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError, api, errorMessage } from "@/lib/api/client";

export default function NewProjectPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { can } = useAuth();

  if (!can("PROJECT_CREATE")) {
    return <Card><ErrorState error={new ApiError(403, {})} /></Card>;
  }

  const create = async (values) => {
    try {
      const project = await api.post("/projects", values);
      await queryClient.invalidateQueries({ queryKey: ["projects"] });
      toast.success(`Project ${project.code} created`);
      router.push(`/projects/${project.id}`);
    } catch (error) {
      toast.error(errorMessage(error));
      throw error;
    }
  };

  return (
    <>
      <PageHeader
        title="New project"
        description="Projects start in Planning. Add your team and tasks, then start it."
        breadcrumbs={[{ label: "Projects", href: "/projects" }, { label: "New project" }]}
      />
      <Card bodyClassName="p-5">
        <ProjectForm onSubmit={create} submitLabel="Create project" onCancel={() => router.push("/projects")} />
      </Card>
    </>
  );
}
