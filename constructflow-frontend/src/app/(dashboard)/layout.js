"use client";

import { Suspense, useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { AppShell } from "@/components/layout/AppShell";
import { LoadingSkeleton } from "@/components/feedback/States";
import { useAuth } from "@/features/auth/AuthProvider";

/**
 * Client-side guard for the signed-in area. It only decides what to render; real protection is the
 * API, which rejects every request without a valid token and membership.
 */
export default function DashboardLayout({ children }) {
  const { status, me } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (status === "anonymous") {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    } else if (status === "authenticated" && me?.mustChangePassword) {
      router.replace("/change-password");
    }
  }, [status, me, router, pathname]);

  if (status !== "authenticated" || me?.mustChangePassword) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="w-full max-w-md">
          <LoadingSkeleton rows={3} />
        </div>
      </div>
    );
  }

  return (
    <AppShell>
      <Suspense fallback={<LoadingSkeleton rows={6} />}>{children}</Suspense>
    </AppShell>
  );
}
