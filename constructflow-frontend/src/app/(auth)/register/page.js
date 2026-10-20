"use client";

import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/forms/Fields";
import { useAuth } from "@/features/auth/AuthProvider";
import { applyServerErrors, errorMessage } from "@/lib/api/client";

const schema = z
  .object({
    organizationName: z.string().trim().min(2, "Enter your company name").max(160),
    fullName: z.string().trim().min(2, "Enter your name").max(160),
    email: z.email("Enter a valid email address"),
    password: z.string().min(12, "Use at least 12 characters").max(128),
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, { path: ["confirmPassword"], message: "Passwords don't match" });

export default function RegisterPage() {
  const { register: registerOrganization } = useAuth();
  const router = useRouter();
  const [formError, setFormError] = useState(null);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(schema) });

  const onSubmit = async ({ confirmPassword: _ignored, ...values }) => {
    setFormError(null);
    try {
      await registerOrganization(values);
      toast.success("Organization created");
      router.replace("/dashboard");
    } catch (error) {
      if (!applyServerErrors(error, setError)) setFormError(errorMessage(error));
    }
  };

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Register your organization</h1>
      <p className="mb-6 mt-1 text-sm text-muted">You&apos;ll be the administrator and can invite your team next.</p>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {formError && <p role="alert" className="rounded-md border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger">{formError}</p>}
        <TextField label="Company name" register={register("organizationName")} error={errors.organizationName?.message} required />
        <TextField label="Your name" autoComplete="name" register={register("fullName")} error={errors.fullName?.message} required />
        <TextField label="Work email" type="email" autoComplete="email" register={register("email")} error={errors.email?.message} required />
        <TextField label="Password" type="password" autoComplete="new-password" hint="At least 12 characters" register={register("password")} error={errors.password?.message} required />
        <TextField label="Confirm password" type="password" autoComplete="new-password" register={register("confirmPassword")} error={errors.confirmPassword?.message} required />
        <Button type="submit" className="w-full justify-center" loading={isSubmitting}>Create organization</Button>
        <p className="text-center text-sm text-muted">
          Already registered? <Link href="/login" className="font-medium text-info hover:underline">Log in</Link>
        </p>
      </form>
    </>
  );
}
