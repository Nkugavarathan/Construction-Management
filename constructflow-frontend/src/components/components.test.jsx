import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ErrorState } from "@/components/feedback/States";
import { DataTable } from "@/components/ui/DataTable";
import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/Badge";
import { ApiError } from "@/lib/api/client";

vi.mock("next/navigation", () => ({ usePathname: () => "/projects" }));

const authState = { can: () => true };
vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => authState }));

const { NavLinks } = await import("@/components/layout/Sidebar");

describe("shared components", () => {
  it("shows a no-access message for 403 instead of a generic failure", () => {
    render(<ErrorState error={new ApiError(403, { detail: "Forbidden" })} />);
    expect(screen.getByText("You don't have access to this")).toBeInTheDocument();
  });

  it("offers a retry for unexpected errors", async () => {
    const retry = vi.fn();
    render(<ErrorState error={new ApiError(500, { detail: "Something went wrong." })} onRetry={retry} />);
    expect(screen.getByRole("alert")).toHaveTextContent("Something went wrong.");
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));
    expect(retry).toHaveBeenCalledOnce();
  });

  it("renders loading, empty and data states of the table", () => {
    const columns = [{ header: "Name", cell: ({ row }) => row.original.name }];
    const { rerender } = render(<DataTable columns={columns} data={[]} loading />);
    expect(screen.getByRole("status", { name: "Loading" })).toBeInTheDocument();

    rerender(<DataTable columns={columns} data={[]} empty={<p>No projects yet</p>} />);
    expect(screen.getByText("No projects yet")).toBeInTheDocument();

    rerender(<DataTable columns={columns} data={[{ id: "1", name: "Kandy block" }]} />);
    expect(screen.getByRole("cell", { name: "Kandy block" })).toBeInTheDocument();
  });

  it("disables a loading button so a form cannot be submitted twice", () => {
    render(<Button loading>Save</Button>);
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("always shows status text, never colour alone", () => {
    render(<StatusBadge status="ON_HOLD" />);
    expect(screen.getByText("On hold")).toBeInTheDocument();
  });

  it("hides navigation entries the role cannot use", () => {
    authState.can = (permission) => ["DASHBOARD_READ", "PROJECT_READ"].includes(permission);
    render(<NavLinks />);
    expect(screen.getByRole("link", { name: "Projects" })).toHaveAttribute("aria-current", "page");
    expect(screen.queryByRole("link", { name: "Audit log" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Expenses" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Settings" })).toBeInTheDocument();
  });

  it("shows admin-only entries to administrators", () => {
    authState.can = () => true;
    render(<NavLinks />);
    expect(screen.getByRole("link", { name: "Audit log" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Team" })).toBeInTheDocument();
  });
});
