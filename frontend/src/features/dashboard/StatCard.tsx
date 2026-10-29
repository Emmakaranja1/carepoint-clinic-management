import type { ComponentType } from "react";

const tones = {
  blue: "bg-brand-50 text-brand-500",
  indigo: "bg-indigo-50 text-indigo-500",
  teal: "bg-emerald-50 text-mint-foreground",
} as const;

export function StatCard({ label, value, icon: Icon, badge: Badge, tone }: {
  label: string;
  value: number | null;
  icon: ComponentType<{ className?: string }>;
  badge: ComponentType<{ className?: string }>;
  tone: keyof typeof tones;
}) {
  return (
    <div className="relative flex items-center gap-5 rounded-2xl border border-brand-100/70 bg-white p-6 min-h-32 shadow-[0_2px_10px_rgba(1,25,77,0.04)]">
      <span className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-xl ${tones[tone]}`}>
        <Icon className="h-7 w-7" />
      </span>
      <div>
        <p className="text-base font-medium text-zinc-500">{label}</p>
        <p className="mt-1 text-4xl font-bold text-heading">{value ?? "—"}</p>
      </div>
      <span className={`absolute bottom-5 right-5 flex h-9 w-9 items-center justify-center rounded-lg ${tones[tone]}`}>
        <Badge className="h-5 w-5" />
      </span>
    </div>
  );
}
