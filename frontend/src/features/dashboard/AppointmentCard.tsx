import type { Appointment } from "@/features/appointments/api";
import type { DoctorSearchResult } from "@/features/discovery/api";
import { Avatar } from "./Avatar";

const dateFormatter = new Intl.DateTimeFormat("en-US", { month: "short", day: "2-digit", year: "numeric" });
const timeFormatter = new Intl.DateTimeFormat("en-US", { hour: "2-digit", minute: "2-digit" });

function appointmentWhen(start: string) {
  const date = new Date(start);
  return Number.isNaN(date.getTime())
    ? "Date unavailable"
    : `${dateFormatter.format(date)} • ${timeFormatter.format(date)}`;
}

const statuses: Record<string, { label: string; className: string }> = {
  BOOKED: { label: "Confirmed", className: "bg-emerald-50 text-mint-foreground" },
  COMPLETED: { label: "Completed", className: "bg-brand-50 text-brand-700" },
  CANCELLED: { label: "Cancelled", className: "bg-zinc-100 text-zinc-600" },
  NO_SHOW: { label: "No show", className: "bg-amber-50 text-amber-700" },
};

export function AppointmentCard({ appointment, doctor, onCancel, cancelling }: {
  appointment: Appointment;
  doctor?: DoctorSearchResult;
  onCancel: (id: string) => void;
  cancelling: boolean;
}) {
  const status = statuses[appointment.status] ?? { label: appointment.status, className: "bg-zinc-100 text-zinc-600" };
  const name = doctor?.doctorName ?? "Your doctor";

  return (
    <article className="flex items-center gap-5 rounded-2xl border border-brand-100/70 bg-white p-5 shadow-[0_2px_10px_rgba(1,25,77,0.04)]">
      <Avatar name={name} photoUrl={doctor?.photoUrl} size="lg" />
      <div className="min-w-0 flex-1">
        <h3 className="truncate text-lg font-semibold text-heading">{name}</h3>
        <p className="truncate text-base text-zinc-500">{doctor?.specialtyName ?? "Consultation"}</p>
        <p className="mt-2 text-sm text-zinc-500">{appointmentWhen(appointment.start)}</p>
      </div>
      <div className="flex flex-col items-end gap-2">
        <span className={`rounded-lg px-4 py-1.5 text-sm font-semibold ${status.className}`}>{status.label}</span>
        {appointment.status === "BOOKED" && (
          <button
            type="button"
            onClick={() => onCancel(appointment.id)}
            disabled={cancelling}
            className="text-sm font-medium text-zinc-400 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {cancelling ? "Cancelling…" : "Cancel"}
          </button>
        )}
      </div>
    </article>
  );
}
