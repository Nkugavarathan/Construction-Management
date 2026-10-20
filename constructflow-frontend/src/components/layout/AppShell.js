"use client";

import { useEffect, useRef, useState } from "react";
import { X } from "lucide-react";
import { Brand, NavLinks, Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";

const COLLAPSE_KEY = "cf.sidebar.collapsed";

function readCollapsed() {
  try {
    return window.localStorage.getItem(COLLAPSE_KEY) === "1";
  } catch {
    return false;
  }
}

export function AppShell({ children }) {
  // A per-browser layout preference only; never anything sensitive in localStorage. The shell renders
  // only in the browser (after the session check), so reading storage in the initializer is safe.
  const [collapsed, setCollapsed] = useState(() => typeof window !== "undefined" && readCollapsed());
  const [mobileOpen, setMobileOpen] = useState(false);
  const drawerRef = useRef(null);

  useEffect(() => {
    const dialog = drawerRef.current;
    if (!dialog) return;
    if (mobileOpen && !dialog.open) dialog.showModal();
    if (!mobileOpen && dialog.open) dialog.close();
  }, [mobileOpen]);

  const toggle = () => {
    setCollapsed((value) => {
      try {
        window.localStorage.setItem(COLLAPSE_KEY, value ? "0" : "1");
      } catch {
        // Storage can be unavailable (private mode); the toggle still works for this session.
      }
      return !value;
    });
  };

  return (
    <div className="flex min-h-screen">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-surface focus:px-3 focus:py-2">
        Skip to content
      </a>
      <Sidebar collapsed={collapsed} onToggle={toggle} />

      <dialog
        ref={drawerRef}
        onClose={() => setMobileOpen(false)}
        className="m-0 h-full max-h-none w-72 max-w-[85vw] bg-primary p-0 lg:hidden"
        aria-label="Navigation"
      >
        <div className="flex h-full flex-col px-3 py-4">
          <div className="flex items-center justify-between">
            <Brand />
            <button type="button" onClick={() => setMobileOpen(false)} className="rounded-md p-2 text-white/80 hover:bg-white/10" aria-label="Close navigation">
              <X className="h-5 w-5" />
            </button>
          </div>
          <nav className="mt-6 flex-1 overflow-y-auto">
            <NavLinks onNavigate={() => setMobileOpen(false)} />
          </nav>
        </div>
      </dialog>

      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar onOpenMobileNav={() => setMobileOpen(true)} />
        <main id="main" className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 lg:px-8">
          {children}
        </main>
      </div>
    </div>
  );
}
