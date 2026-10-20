/**
 * One place that decides what each status looks like. Green = positive/progress, amber = attention,
 * red = problem/critical, blue = informational, grey = neutral or finished.
 */
export const STATUS_TONES = {
  // Projects
  PLANNING: "info",
  ACTIVE: "success",
  ON_HOLD: "warning",
  COMPLETED: "neutral",
  CANCELLED: "danger",
  // Tasks
  TODO: "neutral",
  IN_PROGRESS: "info",
  BLOCKED: "warning",
  DONE: "success",
  // Expenses
  DRAFT: "neutral",
  SUBMITTED: "warning",
  APPROVED: "success",
  REJECTED: "danger",
  // Purchase orders
  PARTIALLY_RECEIVED: "info",
  RECEIVED: "success",
  CLOSED: "neutral",
  // Risks / incidents
  OPEN: "warning",
  MITIGATING: "info",
  INVESTIGATING: "info",
  LOW: "neutral",
  MEDIUM: "warning",
  HIGH: "danger",
  CRITICAL: "danger",
  NEAR_MISS: "info",
  MINOR: "warning",
  MAJOR: "danger",
  // Milestones / members
  PENDING: "neutral",
  OVERDUE: "danger",
  DISABLED: "danger",
};

export const PROJECT_STATUSES = ["PLANNING", "ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED"];
export const TASK_STATUSES = ["TODO", "IN_PROGRESS", "BLOCKED", "DONE", "CANCELLED"];
export const TASK_PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
export const EXPENSE_STATUSES = ["DRAFT", "SUBMITTED", "APPROVED", "REJECTED"];
export const PO_STATUSES = ["DRAFT", "SUBMITTED", "APPROVED", "PARTIALLY_RECEIVED", "RECEIVED", "CANCELLED", "CLOSED"];
export const ROLES = ["ADMIN", "PROJECT_MANAGER", "SITE_ENGINEER", "FINANCE_OFFICER", "STOREKEEPER", "VIEWER"];
export const MATERIAL_UNITS = ["BAG", "KG", "TONNE", "M", "M2", "M3", "LITRE", "PIECE", "BOX", "ROLL", "SHEET"];
export const DOCUMENT_CATEGORIES = ["DRAWING", "CONTRACT", "PERMIT", "SPECIFICATION", "REPORT", "PHOTO", "INVOICE", "OTHER"];
export const INCIDENT_SEVERITIES = ["NEAR_MISS", "MINOR", "MAJOR", "CRITICAL"];
