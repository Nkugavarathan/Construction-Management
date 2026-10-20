"use client";

import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils/cn";

/**
 * Small dropdown: toggled by a button, closes on outside click and Escape, and returns focus to the
 * trigger. `trigger` receives the props to spread on the trigger button.
 */
export function Menu({ trigger, children, align = "right", className }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);
  const buttonRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onPointer = (event) => {
      if (!rootRef.current?.contains(event.target)) setOpen(false);
    };
    const onKey = (event) => {
      if (event.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener("mousedown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      {trigger({
        ref: buttonRef,
        onClick: () => setOpen((value) => !value),
        "aria-haspopup": "menu",
        "aria-expanded": open,
      })}
      {open && (
        <div
          role="menu"
          className={cn(
            "absolute z-40 mt-2 min-w-56 rounded-lg border border-border bg-surface py-1 shadow-lg",
            align === "right" ? "right-0" : "left-0",
            className,
          )}
          onClick={(event) => {
            if (event.target.closest("[data-menu-close]")) setOpen(false);
          }}
        >
          {children}
        </div>
      )}
    </div>
  );
}

export function MenuItem({ children, onClick, icon: Icon, className, ...props }) {
  return (
    <button
      type="button"
      role="menuitem"
      data-menu-close
      onClick={onClick}
      className={cn("flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-text hover:bg-surface-muted", className)}
      {...props}
    >
      {Icon && <Icon className="h-4 w-4 text-muted" aria-hidden="true" />}
      {children}
    </button>
  );
}
