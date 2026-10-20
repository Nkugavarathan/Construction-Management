"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { ChangePasswordForm } from "@/features/auth/ChangePasswordForm";
import { useAuth } from "@/features/auth/AuthProvider";
import { LoadingSkeleton } from "@/components/feedback/States";

export default function ChangePasswordPage() {
  const { status } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (status === "anonymous") router.replace("/login");
  }, [status, router]);

  if (status !== "authenticated") return <LoadingSkeleton rows={3} />;

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Set your password</h1>
      <p className="mb-6 mt-1 text-sm text-muted">Your administrator gave you a temporary password. Choose your own to continue.</p>
      <ChangePasswordForm onDone={() => router.replace("/dashboard")} />
    </>
  );
}
