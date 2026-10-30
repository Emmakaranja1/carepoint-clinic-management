const facts = [
  {
    kicker: "Calendars",
    title: "Real availability",
    body: "Slots come from each clinic’s diary — schedules, time-off and bookings — not a request form.",
  },
  {
    kicker: "Records",
    title: "Notes stay at the clinic",
    body: "CarePoint stores appointments. Diagnoses, prescriptions and clinical notes stay on clinic systems.",
  },
  {
    kicker: "Place",
    title: "Built for independent practice",
    body: "Scoped for small clinics in Kenya: discovery, booking, a receptionist desk, and basic reporting.",
  },
] as const;

export function TrustBar() {
  return (
    <section aria-label="Why CarePoint exists" className="border-y border-navy/10 bg-white">
      <div className="page-container grid gap-10 py-12 md:grid-cols-3 md:gap-0 lg:py-16">
        {facts.map((item, index) => (
          <article
            key={item.title}
            className={`md:px-6 lg:px-10 xl:px-14 ${index > 0 ? "md:border-l md:border-navy/10" : "md:pl-0 lg:pl-0 xl:pl-0"}`}
          >
            <p className="text-[0.68rem] font-semibold uppercase tracking-[0.18em] text-brass">
              {item.kicker}
            </p>
            <h2 className="mt-2 font-display text-2xl font-semibold tracking-tight text-heading">
              {item.title}
            </h2>
            <p className="mt-2 max-w-md text-sm leading-relaxed text-ink/75 xl:text-[0.95rem]">{item.body}</p>
          </article>
        ))}
      </div>
    </section>
  );
}
