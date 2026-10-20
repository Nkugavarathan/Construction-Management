import { forwardRef, useId } from "react";
import { cn } from "@/lib/utils/cn";
import { humanize } from "@/lib/utils/format";

const control =
  "w-full rounded-md border border-border bg-surface px-3 text-sm text-text placeholder:text-muted " +
  "focus:border-info focus:outline-none focus:ring-2 focus:ring-info/20 disabled:bg-surface-muted disabled:text-muted " +
  "aria-[invalid=true]:border-danger";

/**
 * Label + control + hint + error, wired together with ids so screen readers announce the label, the
 * error message (aria-describedby / aria-invalid) and whether the field is required (aria-required).
 * The visual asterisk is CSS-only so it never becomes part of the field's accessible name.
 */
export function FormField({ label, error, hint, required, children, className }) {
  const id = useId();
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className={cn("space-y-1.5", className)}>
      {label && (
        <label
          htmlFor={id}
          className={cn("block text-sm font-medium text-text", required && "after:ml-0.5 after:text-danger after:content-['*']")}
        >
          {label}
        </label>
      )}
      {children({ id, "aria-invalid": error ? true : undefined, "aria-required": required || undefined, "aria-describedby": describedBy })}
      {hint && !error && <p id={`${id}-hint`} className="text-xs text-muted">{hint}</p>}
      {error && <p id={`${id}-error`} role="alert" className="text-xs text-danger">{error}</p>}
    </div>
  );
}

export const Input = forwardRef(function Input({ className, ...props }, ref) {
  return <input ref={ref} className={cn(control, "h-10", className)} {...props} />;
});

export const Textarea = forwardRef(function Textarea({ className, rows = 3, ...props }, ref) {
  return <textarea ref={ref} rows={rows} className={cn(control, "py-2", className)} {...props} />;
});

/** Native select: accessible and keyboard-friendly by default. `options` are strings or {value,label}. */
export const Select = forwardRef(function Select({ className, options = [], placeholder, humanizeLabels = true, ...props }, ref) {
  return (
    <select ref={ref} className={cn(control, "h-10 pr-8", className)} {...props}>
      {placeholder !== undefined && <option value="">{placeholder}</option>}
      {options.map((option) => {
        const value = typeof option === "string" ? option : option.value;
        const label = typeof option === "string" ? (humanizeLabels ? humanize(option) : option) : option.label;
        return (
          <option key={value} value={value}>
            {label}
          </option>
        );
      })}
    </select>
  );
});

export const Checkbox = forwardRef(function Checkbox({ label, className, ...props }, ref) {
  return (
    <label className={cn("inline-flex cursor-pointer items-center gap-2 text-sm text-text", className)}>
      <input ref={ref} type="checkbox" className="h-4 w-4 rounded border-border accent-[var(--cf-primary)]" {...props} />
      {label}
    </label>
  );
});

/** Shorthand for the common case: a registered React Hook Form input with label and error. */
export function TextField({ label, error, hint, required, register, type = "text", className, ...props }) {
  return (
    <FormField label={label} error={error} hint={hint} required={required} className={className}>
      {(a11y) => <Input type={type} {...a11y} {...register} {...props} />}
    </FormField>
  );
}

export function SelectField({ label, error, hint, required, register, options, placeholder, className, ...props }) {
  return (
    <FormField label={label} error={error} hint={hint} required={required} className={className}>
      {(a11y) => <Select options={options} placeholder={placeholder} {...a11y} {...register} {...props} />}
    </FormField>
  );
}

export function TextareaField({ label, error, hint, required, register, className, ...props }) {
  return (
    <FormField label={label} error={error} hint={hint} required={required} className={className}>
      {(a11y) => <Textarea {...a11y} {...register} {...props} />}
    </FormField>
  );
}
