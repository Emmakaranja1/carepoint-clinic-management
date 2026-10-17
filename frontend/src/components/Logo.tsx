import Link from "next/link";

/** Heart-pulse mark + CarePoint wordmark, per the brand reference. */
export function Logo({ href = "/" }: { href?: string }) {
  return (
    <Link href={href} className="inline-flex items-center gap-2">
      <svg
        viewBox="0 0 24 24"
        className="h-7 w-7 fill-brand-500"
        aria-hidden="true"
      >
        <path d="M12 21s-7.5-4.9-9.7-9C.7 9 1.8 5.4 5 4.2c1.9-.7 4 0 5.2 1.4L12 7l1.8-1.4C15 4.2 17.1 3.5 19 4.2c3.2 1.2 4.3 4.8 2.7 7.8-2.2 4.1-9.7 9-9.7 9Z" />
        <path
          d="M4 12h4l1.5-3 3 6 1.5-3h6"
          fill="none"
          stroke="white"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      <span className="text-xl font-bold tracking-tight">
        <span className="text-brand-500">Care</span>
        <span className="text-zinc-900 dark:text-zinc-50">Point</span>
      </span>
    </Link>
  );
}
