"use client";

import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatMoney } from "@/lib/utils/format";

const BAR_COLOR = "var(--cf-secondary)";

/**
 * Approved spend per budget category, from GET /dashboard/summary (never hard-coded).
 * Single series, so one hue and no legend; values are labelled at the bar tips in text colour,
 * and a visually hidden table gives screen readers the same numbers.
 */
export function SpendByCategoryChart({ rows, currency }) {
  const data = rows.map((r) => ({ name: r.categoryName, value: Number(r.approved) }));
  return (
    <figure>
      <div style={{ height: Math.max(160, data.length * 44) }} aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} layout="vertical" margin={{ top: 4, right: 88, bottom: 4, left: 8 }}>
            <CartesianGrid horizontal={false} stroke="var(--cf-border)" />
            <XAxis type="number" hide />
            <YAxis type="category" dataKey="name" width={110} tickLine={false} axisLine={false} tick={{ fill: "var(--cf-muted)", fontSize: 12 }} />
            <Tooltip
              cursor={{ fill: "var(--cf-neutral-soft)" }}
              formatter={(value) => [formatMoney(value, currency), "Approved"]}
              contentStyle={{ borderRadius: 8, borderColor: "var(--cf-border)", fontSize: 12 }}
            />
            <Bar dataKey="value" fill={BAR_COLOR} barSize={20} radius={[0, 4, 4, 0]}>
              <LabelList dataKey="value" position="right" formatter={(v) => formatMoney(v, currency, { compact: true })} style={{ fill: "var(--cf-text)", fontSize: 12 }} />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <figcaption className="sr-only">Approved spend by budget category</figcaption>
      <table className="sr-only">
        <thead><tr><th>Category</th><th>Approved spend</th></tr></thead>
        <tbody>
          {data.map((d) => (
            <tr key={d.name}><td>{d.name}</td><td>{formatMoney(d.value, currency)}</td></tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}
