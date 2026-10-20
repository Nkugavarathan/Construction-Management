import { Suspense } from "react";
import { LoginForm } from "@/features/auth/LoginForm";
import { LoadingSkeleton } from "@/components/feedback/States";

export const metadata = { title: "Log in" };

export default function LoginPage() {
  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Welcome back</h1>
      <p className="mb-6 mt-1 text-sm text-muted">Log in to your ConstructFlow workspace.</p>
      <Suspense fallback={<LoadingSkeleton rows={3} />}>
        <LoginForm />
      </Suspense>
      <details className="mt-8 rounded-md border border-border bg-surface p-3 text-xs text-muted">
        <summary className="cursor-pointer font-medium text-text">Demo accounts (local seed data)</summary>
        <p className="mt-2">
          Password <code className="rounded bg-neutral-soft px-1">ConstructFlow#2026</code> for admin@acme.test, pm@acme.test,
          engineer@acme.test, finance@acme.test, store@acme.test, viewer@acme.test and admin@lanka.test.
        </p>
      </details>
    </>
  );
}
