import { Building2 } from "lucide-react";

export default function AuthLayout({ children }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-primary p-10 text-white lg:flex">
        <div className="flex items-center gap-2">
          <span className="flex h-9 w-9 items-center justify-center rounded-md bg-secondary">
            <Building2 className="h-5 w-5" aria-hidden="true" />
          </span>
          <span className="text-lg font-semibold">ConstructFlow</span>
        </div>
        <div className="max-w-md">
          <h2 className="text-3xl font-semibold leading-tight">Every site, every cost, every delivery — in one place.</h2>
          <p className="mt-4 text-white/70">
            Plan projects, approve expenses, track stock and receive purchase orders with a full audit trail.
          </p>
        </div>
        <ul className="grid grid-cols-3 gap-4 text-sm text-white/80">
          <li><span className="block text-2xl font-semibold text-white">Projects</span>tasks &amp; milestones</li>
          <li><span className="block text-2xl font-semibold text-white">Costs</span>approved spend</li>
          <li><span className="block text-2xl font-semibold text-white">Stock</span>ledger &amp; POs</li>
        </ul>
      </aside>
      <main className="flex items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm">{children}</div>
      </main>
    </div>
  );
}
