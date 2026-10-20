"use client";

import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/layout/PageHeader";
import { Card, ErrorState } from "@/components/feedback/States";
import { PurchaseOrderForm } from "@/features/procurement/PurchaseOrderForm";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError, api, errorMessage } from "@/lib/api/client";

export default function NewPurchaseOrderPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { can } = useAuth();
  if (!can("PO_CREATE")) return <Card><ErrorState error={new ApiError(403, {})} /></Card>;

  return (
    <>
      <PageHeader title="New purchase order" description="Saved as a draft. Submit it for approval when it's ready."
        breadcrumbs={[{ label: "Procurement", href: "/procurement" }, { label: "New purchase order" }]} />
      <Card bodyClassName="p-5">
        <PurchaseOrderForm
          onCancel={() => router.push("/procurement")}
          onSubmit={async (body) => {
            try {
              const order = await api.post("/purchase-orders", body);
              await queryClient.invalidateQueries({ queryKey: ["purchase-orders"] });
              toast.success(`${order.number} saved as draft`);
              router.push(`/procurement/${order.id}`);
            } catch (error) {
              toast.error(errorMessage(error));
              throw error;
            }
          }}
        />
      </Card>
    </>
  );
}
