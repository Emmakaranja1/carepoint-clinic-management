export function SectionHeading({
  eyebrow,
  title,
  description,
  id,
}: {
  eyebrow: string;
  title: string;
  description?: string;
  id?: string;
}) {
  return (
    <div className="max-w-2xl">
      <p className="flex items-center gap-3 text-[0.7rem] font-semibold uppercase tracking-[0.22em] text-brass">
        <span aria-hidden="true" className="h-px w-8 bg-brass/70" />
        {eyebrow}
      </p>
      <h2
        id={id}
        className="mt-3 font-display text-[1.85rem] font-semibold leading-[1.15] tracking-tight text-heading sm:text-4xl 2xl:text-[2.75rem]"
      >
        {title}
      </h2>
      {description && (
        <p className="mt-4 max-w-xl text-base leading-relaxed text-ink/80 sm:text-lg">
          {description}
        </p>
      )}
    </div>
  );
}
