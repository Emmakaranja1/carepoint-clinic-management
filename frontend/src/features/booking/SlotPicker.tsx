import type { Slot } from "@/features/discovery/api";

const dayFormatter = new Intl.DateTimeFormat("en-US", { weekday: "short", month: "short", day: "numeric" });
const timeFormatter = new Intl.DateTimeFormat("en-US", { hour: "2-digit", minute: "2-digit" });

export function isoDate(date: Date) {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function slotLabel(slot: Slot) {
  return `${dayFormatter.format(new Date(slot.start))} · ${timeFormatter.format(new Date(slot.start))}`;
}

export function groupByDay(slots: Slot[]) {
  const days = new Map<string, Slot[]>();
  for (const slot of slots) {
    const key = isoDate(new Date(slot.start));
    const bucket = days.get(key);
    if (bucket) bucket.push(slot);
    else days.set(key, [slot]);
  }
  return days;
}

export function SlotPicker({ days, day, onDay, selected, onSelect }: {
  days: Map<string, Slot[]>;
  day: string | null;
  onDay: (day: string) => void;
  selected: Slot | null;
  onSelect: (slot: Slot) => void;
}) {
  const slots = day ? days.get(day) ?? [] : [];

  return (
    <div>
      <div role="radiogroup" aria-label="Day" className="flex gap-2 overflow-x-auto pb-2">
        {[...days.entries()].map(([key, daySlots]) => (
          <button
            key={key}
            type="button"
            role="radio"
            aria-checked={day === key}
            onClick={() => onDay(key)}
            className={`shrink-0 rounded-xl border px-4 py-3 text-left transition ${day === key ? "border-brand-500 bg-brand-50 text-brand-700" : "border-brand-100 bg-white text-heading hover:bg-brand-50"}`}
          >
            <span className="block text-sm font-semibold">{dayFormatter.format(new Date(daySlots[0].start))}</span>
            <span className="block text-xs text-zinc-500">{daySlots.length} slot{daySlots.length === 1 ? "" : "s"}</span>
          </button>
        ))}
      </div>

      <div role="radiogroup" aria-label="Time" className="mt-4 grid grid-cols-3 gap-2 sm:grid-cols-4 lg:grid-cols-6">
        {slots.map((slot) => {
          const active = selected?.start === slot.start;
          return (
            <button
              key={slot.start}
              type="button"
              role="radio"
              aria-checked={active}
              onClick={() => onSelect(slot)}
              className={`rounded-lg border px-3 py-2 text-sm font-semibold transition ${active ? "border-brand-500 bg-brand-500 text-white" : "border-brand-100 bg-white text-heading hover:bg-brand-50"}`}
            >
              {timeFormatter.format(new Date(slot.start))}
            </button>
          );
        })}
      </div>
    </div>
  );
}
