function Glyph({ name }: { name: string }) {
  const n = name.toLowerCase();
  if (n.includes("cardio") || n.includes("heart")) {
    return (
      <>
        <path d="M12 20s-7.5-4.6-9-9.2C1.9 7.3 4 4.5 7 4.5c2 0 3.6 1.1 5 3 1.4-1.9 3-3 5-3 3 0 5.1 2.8 4 6.3-1.5 4.6-9 9.2-9 9.2Z" />
        <path d="M6.5 12h2.8l1.4-2.4 2.2 4.4 1.4-2h3.2" />
      </>
    );
  }
  if (n.includes("derm") || n.includes("skin")) {
    return <path d="M12 3.5s-6 6.6-6 10.6a6 6 0 0 0 12 0c0-4-6-10.6-6-10.6Z" />;
  }
  if (!n.includes("ortho") && (n.includes("paed") || n.includes("pediat") || n.includes("child"))) {
    return (
      <>
        <circle cx="12" cy="12" r="8.5" />
        <path d="M9 14.5c.8.9 1.8 1.4 3 1.4s2.2-.5 3-1.4M9.5 10h.01M14.5 10h.01" />
      </>
    );
  }
  if (n.includes("general") || n.includes("family")) {
    return (
      <>
        <rect x="4" y="4" width="16" height="16" rx="4" />
        <path d="M12 8.5v7M8.5 12h7" />
      </>
    );
  }
  if (n.includes("ortho") || n.includes("bone")) {
    return (
      <>
        <path d="m9.2 11.6 3.2 3.2M11.6 9.2l3.2 3.2" />
        <circle cx="7" cy="8.4" r="2.2" />
        <circle cx="8.4" cy="7" r="2.2" />
        <circle cx="15.6" cy="17" r="2.2" />
        <circle cx="17" cy="15.6" r="2.2" />
      </>
    );
  }
  if (n.includes("gyn") || n.includes("obste") || n.includes("women")) {
    return (
      <>
        <circle cx="12" cy="9" r="5" />
        <path d="M12 14v7M9 18h6" />
      </>
    );
  }
  if (n.includes("mental") || n.includes("psych")) {
    return <path d="M12 20v-8m0 0c0-3.5-2.5-6-6-6 0 3.5 2.5 6 6 6Zm0 0c0-3.5 2.5-6 6-6 0 3.5-2.5 6-6 6Z" />;
  }
  if (n.includes("dent")) {
    return (
      <path d="M8 3.5c1.7 0 2.4 1 4 1s2.3-1 4-1c2.5 0 3.8 2.2 3.3 5-.4 2.4-1.6 3.5-2 6-.4 2.8-.9 6-2.3 6-1.6 0-1.4-4.5-3-4.5s-1.4 4.5-3 4.5c-1.4 0-1.9-3.2-2.3-6-.4-2.5-1.6-3.6-2-6-.5-2.8.8-5 3.3-5Z" />
    );
  }
  return (
    <>
      <path d="M6 3v5a4 4 0 0 0 8 0V3M10 12v3a5 5 0 0 0 10 0v-2" />
      <circle cx="20" cy="11" r="2" />
    </>
  );
}

export function SpecialtyIcon({ name, className = "h-5 w-5" }: { name: string; className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={`${className} fill-none stroke-current`}
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <Glyph name={name} />
    </svg>
  );
}
