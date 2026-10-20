import {
  ClipboardList,
  FolderKanban,
  LayoutDashboard,
  Package,
  ReceiptText,
  ScrollText,
  Settings,
  ShoppingCart,
  Users,
} from "lucide-react";

/**
 * Sidebar entries and the permission each needs. Hiding an entry is only a convenience: the API
 * enforces the same permissions on every request.
 */
export const NAV_ITEMS = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard, permission: "DASHBOARD_READ" },
  { href: "/projects", label: "Projects", icon: FolderKanban, permission: "PROJECT_READ" },
  { href: "/tasks", label: "My tasks", icon: ClipboardList },
  { href: "/finance/expenses", label: "Expenses", icon: ReceiptText, permission: "FINANCE_READ" },
  { href: "/inventory", label: "Inventory", icon: Package, permission: "INVENTORY_READ" },
  { href: "/procurement", label: "Procurement", icon: ShoppingCart, permission: "PROCUREMENT_READ" },
  { section: "Organization" },
  { href: "/team", label: "Team", icon: Users, permission: "MEMBER_READ" },
  { href: "/audit", label: "Audit log", icon: ScrollText, permission: "AUDIT_READ" },
  { href: "/settings", label: "Settings", icon: Settings },
];
