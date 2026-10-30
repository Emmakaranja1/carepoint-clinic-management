export function initials(name: string) {
  return name
    .replace(/^Dr\.?\s+/i, "")
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

const sizes = {
  sm: "h-9 w-9 text-xs",
  md: "h-12 w-12 text-sm",
  lg: "h-16 w-16 text-lg",
  xl: "h-24 w-24 text-2xl",
} as const;

export function Avatar({ name, photoUrl, size = "md" }: {
  name: string;
  photoUrl?: string | null;
  size?: keyof typeof sizes;
}) {
  if (photoUrl) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img src={photoUrl} alt="" className={`${sizes[size]} shrink-0 rounded-full object-cover ring-2 ring-white`} />
    );
  }
  return (
    <span
      aria-hidden="true"
      className={`${sizes[size]} flex shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-100 to-brand-300 font-bold text-brand-700 ring-2 ring-white`}
    >
      {initials(name) || "?"}
    </span>
  );
}
