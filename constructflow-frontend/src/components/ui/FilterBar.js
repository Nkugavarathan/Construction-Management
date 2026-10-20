"use client";

import { useEffect, useState } from "react";
import { Search } from "lucide-react";
import { Input } from "@/components/forms/Fields";

/** Search box that reports changes after the user pauses typing, so each keystroke is not a request. */
export function SearchField({ value, onChange, placeholder = "Search…", label = "Search", delay = 300 }) {
  const [text, setText] = useState(value ?? "");
  useEffect(() => {
    const id = setTimeout(() => {
      if (text !== (value ?? "")) onChange(text);
    }, delay);
    return () => clearTimeout(id);
  }, [text, value, onChange, delay]);
  return (
    <div className="relative w-full sm:w-64">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" aria-hidden="true" />
      <Input aria-label={label} value={text} onChange={(e) => setText(e.target.value)} placeholder={placeholder} className="pl-9" />
    </div>
  );
}

export function FilterBar({ children }) {
  return <div className="flex flex-wrap items-center gap-2 border-b border-border px-4 py-3">{children}</div>;
}
