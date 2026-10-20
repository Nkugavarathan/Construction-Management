"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/forms/Fields";
import { useAuth } from "./AuthProvider";
import { errorMessage } from "@/lib/api/client";

export const loginSchema = z.object({
  email: z.email("Enter a valid email address"),
  password: z.string().min(1, "Enter your password"),
});

/** Only same-site relative paths are accepted as a post-login destination (no open redirects). */
export function safeNext(next) {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/dashboard";
}

export function LoginForm() {
  const { login, status } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get("next"));
  const [formError, setFormError] = useState(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  useEffect(() => {
    if (status === "authenticated") router.replace(next);
  }, [status, router, next]);

  const onSubmit = async (values) => {
    setFormError(null);
    try {
      const me = await login(values);
      router.replace(me.mustChangePassword ? "/change-password" : next);
    } catch (error) {
      setFormError(errorMessage(error));
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
      {formError && (
        <p role="alert" className="rounded-md border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger">
          {formError}
        </p>
      )}
      <TextField label="Email" type="email" autoComplete="email" register={register("email")} error={errors.email?.message} required />
      <TextField label="Password" type="password" autoComplete="current-password" register={register("password")} error={errors.password?.message} required />
      <Button type="submit" className="w-full justify-center" loading={isSubmitting}>Log in</Button>
      <p className="text-center text-sm text-muted">
        New company? <Link href="/register" className="font-medium text-info hover:underline">Register your organization</Link>
      </p>
    </form>
  );
}
