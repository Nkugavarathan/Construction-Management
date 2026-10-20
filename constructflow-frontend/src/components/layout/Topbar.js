"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, Building, Check, ChevronDown, KeyRound, LogOut, Menu as MenuIcon } from "lucide-react";
import { toast } from "sonner";
import { Menu, MenuItem } from "@/components/ui/Menu";
import { useAuth } from "@/features/auth/AuthProvider";
import { api, errorMessage } from "@/lib/api/client";
import { formatRelative, humanize, initials } from "@/lib/utils/format";
import { cn } from "@/lib/utils/cn";

function OrganizationSwitcher() {
  const { me, switchOrganization } = useAuth();
  const router = useRouter();
  const organizations = me?.organizations ?? [];
  const mutation = useMutation({
    mutationFn: switchOrganization,
    onSuccess: () => {
      toast.success("Switched organization");
      router.push("/dashboard");
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (organizations.length <= 1) {
    return (
      <div className="hidden items-center gap-2 text-sm font-medium sm:flex">
        <Building className="h-4 w-4 text-muted" aria-hidden="true" />
        {me?.activeOrganization?.name}
      </div>
    );
  }
  return (
    <Menu
      align="left"
      trigger={(props) => (
        <button {...props} className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm font-medium hover:bg-neutral-soft">
          <Building className="h-4 w-4 text-muted" aria-hidden="true" />
          <span className="max-w-40 truncate">{me?.activeOrganization?.name}</span>
          <ChevronDown className="h-4 w-4 text-muted" aria-hidden="true" />
        </button>
      )}
    >
      <p className="px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-muted">Switch organization</p>
      {organizations.map((org) => (
        <MenuItem key={org.organizationId} onClick={() => org.organizationId !== me.activeOrganization.id && mutation.mutate(org.organizationId)}>
          <span className="flex-1">
            {org.organizationName}
            <span className="block text-xs text-muted">{humanize(org.role)}</span>
          </span>
          {org.organizationId === me.activeOrganization.id && <Check className="h-4 w-4 text-secondary" aria-label="Current" />}
        </MenuItem>
      ))}
    </Menu>
  );
}

function NotificationBell() {
  const queryClient = useQueryClient();
  const router = useRouter();
  // Polling is enough for in-app notifications at this scale; no websocket infrastructure needed.
  const { data: count } = useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: () => api.get("/notifications/unread-count").then((r) => r.count),
    refetchInterval: 60_000,
  });
  const { data: recent } = useQuery({
    queryKey: ["notifications", "recent"],
    queryFn: () => api.get("/notifications", { size: 8 }),
    refetchInterval: 60_000,
  });
  const markRead = useMutation({
    mutationFn: (id) => api.post(`/notifications/${id}/read`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });

  return (
    <Menu
      trigger={(props) => (
        <button {...props} className="relative rounded-md p-2 text-muted hover:bg-neutral-soft hover:text-text" aria-label={`Notifications${count ? `, ${count} unread` : ""}`}>
          <Bell className="h-5 w-5" />
          {count > 0 && (
            <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold text-white">
              {count > 99 ? "99+" : count}
            </span>
          )}
        </button>
      )}
      className="w-80"
    >
      <div className="flex items-center justify-between px-3 py-2">
        <p className="text-sm font-semibold">Notifications</p>
        <Link href="/notifications" className="text-xs text-info hover:underline" data-menu-close>View all</Link>
      </div>
      {recent?.content?.length ? (
        recent.content.map((n) => (
          <MenuItem
            key={n.id}
            onClick={() => {
              if (!n.read) markRead.mutate(n.id);
              if (n.link) router.push(n.link);
            }}
            className="items-start"
          >
            <span className={cn("mt-1.5 h-2 w-2 shrink-0 rounded-full", n.read ? "bg-transparent" : "bg-info")} aria-hidden="true" />
            <span className="min-w-0 flex-1">
              <span className={cn("block truncate", !n.read && "font-medium")}>{n.title}</span>
              {n.message && <span className="block truncate text-xs text-muted">{n.message}</span>}
              <span className="block text-xs text-muted">{formatRelative(n.createdAt)}</span>
            </span>
          </MenuItem>
        ))
      ) : (
        <p className="px-3 py-6 text-center text-sm text-muted">You&apos;re all caught up.</p>
      )}
    </Menu>
  );
}

function UserMenu() {
  const { me, logout } = useAuth();
  const router = useRouter();
  return (
    <Menu
      trigger={(props) => (
        <button {...props} className="flex items-center gap-2 rounded-md p-1 hover:bg-neutral-soft" aria-label="Account menu">
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-xs font-semibold text-white">
            {initials(me?.user?.fullName)}
          </span>
          <span className="hidden text-left md:block">
            <span className="block text-sm font-medium leading-tight">{me?.user?.fullName}</span>
            <span className="block text-xs leading-tight text-muted">{humanize(me?.role)}</span>
          </span>
          <ChevronDown className="hidden h-4 w-4 text-muted md:block" aria-hidden="true" />
        </button>
      )}
    >
      <div className="border-b border-border px-3 py-2">
        <p className="text-sm font-medium">{me?.user?.fullName}</p>
        <p className="truncate text-xs text-muted">{me?.user?.email}</p>
      </div>
      <MenuItem icon={KeyRound} onClick={() => router.push("/settings#password")}>Change password</MenuItem>
      <MenuItem
        icon={LogOut}
        onClick={async () => {
          await logout();
          router.replace("/login");
        }}
      >
        Log out
      </MenuItem>
    </Menu>
  );
}

export function Topbar({ onOpenMobileNav }) {
  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border bg-surface/95 px-4 backdrop-blur lg:px-6">
      <button type="button" onClick={onOpenMobileNav} className="rounded-md p-2 text-muted hover:bg-neutral-soft lg:hidden" aria-label="Open navigation">
        <MenuIcon className="h-5 w-5" />
      </button>
      <OrganizationSwitcher />
      <div className="ml-auto flex items-center gap-1">
        <NotificationBell />
        <UserMenu />
      </div>
    </header>
  );
}
