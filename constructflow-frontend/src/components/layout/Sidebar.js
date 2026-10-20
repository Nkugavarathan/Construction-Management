"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Building2, ChevronsLeft, ChevronsRight } from "lucide-react";
import { NAV_ITEMS } from "@/lib/constants/navigation";
import { useAuth } from "@/features/auth/AuthProvider";
import { cn } from "@/lib/utils/cn";

export function NavLinks({ collapsed = false, onNavigate }) {
  const pathname = usePathname();
  const { can } = useAuth();
  const visible = NAV_ITEMS.filter((item) => item.section || !item.permission || can(item.permission));

  return (
    <ul className="space-y-0.5">
      {visible.map((item, index) => {
        if (item.section) {
          return collapsed ? (
            <li key={item.section} className="my-2 border-t border-white/10" aria-hidden="true" />
          ) : (
            <li key={item.section} className="px-3 pb-1 pt-4 text-[11px] font-semibold uppercase tracking-wider text-white/50">
              {item.section}
            </li>
          );
        }
        const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
        const Icon = item.icon;
        return (
          <li key={`${item.href}-${index}`}>
            <Link
              href={item.href}
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              title={collapsed ? item.label : undefined}
              className={cn(
                "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                active ? "bg-white/15 text-white" : "text-white/75 hover:bg-white/10 hover:text-white",
                collapsed && "justify-center px-2",
              )}
            >
              <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
              {!collapsed && <span>{item.label}</span>}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

export function Brand({ collapsed }) {
  return (
    <Link href="/dashboard" className="flex items-center gap-2 px-2 text-white">
      <span className="flex h-8 w-8 items-center justify-center rounded-md bg-secondary">
        <Building2 className="h-5 w-5" aria-hidden="true" />
      </span>
      {!collapsed && <span className="text-base font-semibold tracking-tight">ConstructFlow</span>}
    </Link>
  );
}

export function Sidebar({ collapsed, onToggle }) {
  return (
    <aside
      className={cn(
        "sticky top-0 hidden h-screen shrink-0 flex-col bg-primary px-3 py-4 transition-[width] lg:flex",
        collapsed ? "w-[68px]" : "w-60",
      )}
      aria-label="Main navigation"
    >
      <Brand collapsed={collapsed} />
      <nav className="mt-6 flex-1 overflow-y-auto">
        <NavLinks collapsed={collapsed} />
      </nav>
      <button
        type="button"
        onClick={onToggle}
        className="mt-2 flex items-center justify-center gap-2 rounded-md px-3 py-2 text-xs text-white/70 hover:bg-white/10 hover:text-white"
        aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
      >
        {collapsed ? <ChevronsRight className="h-4 w-4" /> : <><ChevronsLeft className="h-4 w-4" /> Collapse</>}
      </button>
    </aside>
  );
}
