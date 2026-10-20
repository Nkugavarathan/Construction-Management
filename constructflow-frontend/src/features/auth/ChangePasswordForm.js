"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/forms/Fields";
import { useAuth } from "./AuthProvider";
import { applyServerErrors, errorMessage } from "@/lib/api/client";

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Enter your current password"),
    newPassword: z.string().min(12, "Use at least 12 characters").max(128),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ["confirmPassword"], message: "Passwords don't match" })
  .refine((v) => v.newPassword !== v.currentPassword, { path: ["newPassword"], message: "Choose a different password" });

export function ChangePasswordForm({ onDone }) {
  const { changePassword } = useAuth();
  const { register, handleSubmit, setError, reset, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(changePasswordSchema),
  });

  const onSubmit = async ({ currentPassword, newPassword }) => {
    try {
      await changePassword({ currentPassword, newPassword });
      toast.success("Password changed. Other sessions were signed out.");
      reset();
      onDone?.();
    } catch (error) {
      if (!applyServerErrors(error, setError)) toast.error(errorMessage(error));
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
      <TextField label="Current password" type="password" autoComplete="current-password" register={register("currentPassword")} error={errors.currentPassword?.message} required />
      <TextField label="New password" type="password" autoComplete="new-password" hint="At least 12 characters" register={register("newPassword")} error={errors.newPassword?.message} required />
      <TextField label="Confirm new password" type="password" autoComplete="new-password" register={register("confirmPassword")} error={errors.confirmPassword?.message} required />
      <Button type="submit" loading={isSubmitting}>Change password</Button>
    </form>
  );
}
