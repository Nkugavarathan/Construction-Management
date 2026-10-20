"use client";

import { useQuery } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/forms/Fields";
import { useAuth } from "@/features/auth/AuthProvider";
import { ChangePasswordForm } from "@/features/auth/ChangePasswordForm";
import { api, applyServerErrors } from "@/lib/api/client";
import { useApiMutation } from "@/lib/api/hooks";
import { humanize } from "@/lib/utils/format";

const orgSchema = z.object({
  name: z.string().trim().min(1, "Required").max(160),
  currencyCode: z.string().trim().regex(/^[A-Z]{3}$/, "3-letter ISO code, e.g. LKR"),
  timezone: z.string().trim().min(1, "Required").max(64),
  expenseApprovalThreshold: z.string().trim().regex(/^\d{1,13}(\.\d{1,2})?$/, "Amount with up to 2 decimals"),
});

export default function SettingsPage() {
  const { me, can } = useAuth();
  return (
    <>
      <PageHeader title="Settings" />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Your profile" bodyClassName="space-y-2 p-4 text-sm">
          <p><span className="text-muted">Name:</span> {me.user.fullName}</p>
          <p><span className="text-muted">Email:</span> {me.user.email}</p>
          <p><span className="text-muted">Role in {me.activeOrganization.name}:</span> <Badge>{humanize(me.role)}</Badge></p>
          <details className="pt-2">
            <summary className="cursor-pointer text-xs text-muted">What can I do? ({me.permissions.length} permissions)</summary>
            <ul className="mt-2 flex flex-wrap gap-1">{[...me.permissions].sort().map((p) => <li key={p}><Badge>{humanize(p)}</Badge></li>)}</ul>
          </details>
        </Card>
        <Card title="Change password" description="Changing it signs you out everywhere else." bodyClassName="p-4">
          <div id="password"><ChangePasswordForm /></div>
        </Card>
        {can("ORG_SETTINGS_MANAGE") && <OrganizationSettings />}
      </div>
    </>
  );
}

function OrganizationSettings() {
  const org = useQuery({ queryKey: ["organization"], queryFn: () => api.get("/organization") });
  if (org.error) return <Card title="Organization"><ErrorState error={org.error} onRetry={org.refetch} /></Card>;
  if (org.isPending) return <Card title="Organization"><LoadingSkeleton rows={3} /></Card>;
  return <OrganizationForm organization={org.data} />;
}

function OrganizationForm({ organization }) {
  const { register, handleSubmit, setError, formState: { errors, isSubmitting, isDirty } } = useForm({
    resolver: zodResolver(orgSchema),
    defaultValues: {
      name: organization.name, currencyCode: organization.currencyCode, timezone: organization.timezone,
      expenseApprovalThreshold: organization.expenseApprovalThreshold,
    },
  });
  const save = useApiMutation((body) => api.patch("/organization", { ...body, version: organization.version }),
    { invalidate: [["organization"]], success: "Organization settings saved. Sign in again to refresh the header." });
  return (
    <Card title="Organization" description="Administrators only. Changes are audited." className="lg:col-span-2" bodyClassName="p-4">
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={handleSubmit(async (v) => {
        try { await save.mutateAsync(v); } catch (e) { applyServerErrors(e, setError); }
      })}>
        <TextField label="Organization name" register={register("name")} error={errors.name?.message} required />
        <TextField label="Currency" register={register("currencyCode")} error={errors.currencyCode?.message} required />
        <TextField label="Time zone" hint="IANA name, e.g. Asia/Colombo. Used for due dates and 'overdue'." register={register("timezone")} error={errors.timezone?.message} required />
        <TextField label="PM approval limit" hint="Project managers can approve expenses up to this amount." inputMode="decimal" register={register("expenseApprovalThreshold")} error={errors.expenseApprovalThreshold?.message} required />
        <div className="flex justify-end sm:col-span-2"><Button type="submit" loading={isSubmitting} disabled={!isDirty}>Save settings</Button></div>
      </form>
    </Card>
  );
}
