import Link from "next/link";

import { Logo } from "./Logo";

const currentYear = new Date().getFullYear();

const quickLinks = [
  ["Home", "/"],
  ["Find a Doctor", "/doctors"],
  ["Services", "/#services"],
  ["About Us", "/#about"],
  ["Contact", "/#contact"],
] as const;

const supportLinks = [
  ["Help Center", "#"],
  ["Terms of Service", "#"],
  ["Privacy Policy", "#"],
  ["FAQ", "#"],
] as const;

const socials = [
  {
    label: "Facebook",
    path: "M13.5 9H16V5.5h-2.5A3.5 3.5 0 0 0 10 9v2H7.5v3.5H10V21h3.5v-6.5h2.3l.7-3.5h-3V9Z",
  },
  {
    label: "X",
    path: "M4 4l7.2 9.3L4.4 20h2.4l5.4-5.4L16.8 20H20l-7.5-9.6L19.3 4h-2.4l-4.9 4.9L8.2 4H4Z",
  },
  {
    label: "Instagram",
    path: "M12 4c2.7 0 3 0 4 .1 1 .1 1.5.2 1.9.4a3 3 0 0 1 1.1.7 3 3 0 0 1 .7 1.1c.2.4.3.9.4 1.9.1 1 .1 1.3.1 4s0 3-.1 4c-.1 1-.2 1.5-.4 1.9a3 3 0 0 1-.7 1.1 3 3 0 0 1-1.1.7c-.4.2-.9.3-1.9.4-1 .1-1.3.1-4 .1s-3 0-4-.1c-1-.1-1.5-.2-1.9-.4a3 3 0 0 1-1.1-.7 3 3 0 0 1-.7-1.1c-.2-.4-.3-.9-.4-1.9-.1-1-.1-1.3-.1-4s0-3 .1-4c.1-1 .2-1.5.4-1.9a3 3 0 0 1 .7-1.1A3 3 0 0 1 6.1 4.5c.4-.2.9-.3 1.9-.4 1-.1 1.3-.1 4-.1Zm0 1.8c-2.6 0-2.9 0-3.9.1-.9 0-1.4.2-1.7.3-.4.2-.7.4-1 .7-.3.3-.5.6-.7 1-.1.3-.3.8-.3 1.7-.1 1-.1 1.3-.1 3.9s0 2.9.1 3.9c0 .9.2 1.4.3 1.7.2.4.4.7.7 1 .3.3.6.5 1 .7.3.1.8.3 1.7.3 1 .1 1.3.1 3.9.1s2.9 0 3.9-.1c.9 0 1.4-.2 1.7-.3.4-.2.7-.4 1-.7.3-.3.5-.6.7-1 .1-.3.3-.8.3-1.7.1-1 .1-1.3.1-3.9s0-2.9-.1-3.9c0-.9-.2-1.4-.3-1.7a2.6 2.6 0 0 0-1.7-1.7c-.3-.1-.8-.3-1.7-.3-1-.1-1.3-.1-3.9-.1Zm0 3a5.2 5.2 0 1 1 0 10.4 5.2 5.2 0 0 1 0-10.4Zm0 1.8a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 0 0 0-6.8Zm5.4-3.2a1.2 1.2 0 1 1 0 2.4 1.2 1.2 0 0 1 0-2.4Z",
  },
  {
    label: "LinkedIn",
    path: "M5 4a2 2 0 1 1 0 4 2 2 0 0 1 0-4Zm.5 5.5h3v11h-3v-11Zm5.5 0h2.9v1.6h.1a3.2 3.2 0 0 1 2.9-1.8c3.1 0 3.6 2 3.6 4.6v6.2h-3v-5.8c0-1.4 0-3.2-2-3.2s-2.3 1.5-2.3 3v5.8h-3v-11Z",
  },
  {
    label: "YouTube",
    path: "M12 5.5s-5 0-6.4.4A3 3 0 0 0 3.4 8 30 30 0 0 0 3 12a30 30 0 0 0 .4 4 3 3 0 0 0 2.2 2.1c1.4.4 6.4.4 6.4.4s5 0 6.4-.4a3 3 0 0 0 2.2-2.1 30 30 0 0 0 .4-4 30 30 0 0 0-.4-4A3 3 0 0 0 18.4 5.9c-1.4-.4-6.4-.4-6.4-.4ZM10 9l5 3-5 3V9Z",
  },
] as const;

export function Footer() {
  return (
    <footer className="bg-navy text-zinc-300">
      <div className="mx-auto grid max-w-6xl gap-10 px-6 py-16 sm:grid-cols-2 lg:grid-cols-4 lg:px-10">
        <div id="about">
          <Logo light />
          <p className="mt-4 text-lg font-semibold text-white">
            Better Care. Healthier Tomorrow.
          </p>
          <p className="mt-3 text-base leading-7">
            We are committed to providing accessible, high-quality healthcare to
            everyone, everywhere.
          </p>
          <div className="mt-6 flex gap-3">
            {socials.map((s) => (
              <a
                key={s.label}
                href="#"
                aria-label={s.label}
                className="flex h-9 w-9 items-center justify-center rounded-full bg-white/10 hover:bg-brand-500"
              >
                <svg viewBox="0 0 24 24" className="h-4 w-4 fill-white" aria-hidden="true">
                  <path d={s.path} />
                </svg>
              </a>
            ))}
          </div>
        </div>

        <nav>
          <h3 className="text-base font-semibold text-white">Quick Links</h3>
          <ul className="mt-4 space-y-3 text-base">
            {quickLinks.map(([label, href]) => (
              <li key={label}>
                <Link href={href} className="hover:text-white">
                  {label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <nav>
          <h3 className="text-base font-semibold text-white">Support</h3>
          <ul className="mt-4 space-y-3 text-base">
            {supportLinks.map(([label, href]) => (
              <li key={label}>
                <Link href={href} className="hover:text-white">
                  {label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <div id="contact">
          <h3 className="text-base font-semibold text-white">Contact Us</h3>
          <ul className="mt-4 space-y-3 text-base">
            <li>+254 700 123 456</li>
            <li>info@carepoint.co.ke</li>
            <li>Nairobi, Kenya</li>
          </ul>
        </div>
      </div>
      <div className="border-t border-white/10">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-2 px-6 py-6 text-sm sm:flex-row lg:px-10">
          <p>© {currentYear} CarePoint. All rights reserved.</p>
          <p className="flex gap-4">
            <Link href="#">Privacy</Link>
            <Link href="#">Terms</Link>
            <Link href="#">Cookies</Link>
          </p>
        </div>
      </div>
    </footer>
  );
}
