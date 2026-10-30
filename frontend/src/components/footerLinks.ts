type LinkGroup = { id: string; title: string; links: readonly (readonly [string, string])[] };

const specialty = (name: string) => [name, `/dashboard/doctors?q=${encodeURIComponent(name)}`] as const;

export const footerGroups: readonly LinkGroup[] = [
  {
    id: "footer-patients",
    title: "For patients",
    links: [
      ["Find a doctor", "/dashboard/doctors"],
      ["Browse specialties", "/#services"],
      ["How booking works", "/#how-it-works"],
      ["Create a free account", "/register"],
    ],
  },
  {
    id: "footer-specialties",
    title: "Specialties",
    links: [
      specialty("Cardiology"),
      specialty("Dermatology"),
      specialty("Paediatrics"),
      specialty("General Practice"),
      specialty("Mental Health"),
      specialty("Dentistry"),
      ["All specialties", "/#services"],
    ],
  },
  {
    id: "footer-account",
    title: "Your account",
    links: [
      ["Log in", "/login"],
      ["My appointments", "/dashboard/appointments"],
      ["Visit history", "/dashboard/health"],
      ["Messages", "/dashboard/messages"],
      ["Profile & settings", "/dashboard/settings"],
      ["Reset password", "/forgot-password"],
    ],
  },
];

export const supportHours = [
  ["Mon – Fri", "8:00 – 18:00"],
  ["Saturday", "9:00 – 14:00"],
  ["Sun & holidays", "Closed"],
] as const;

export const commitments = [
  {
    title: "Clinical notes stay with your clinic",
    body: "CarePoint holds appointments only. Diagnoses and prescriptions never leave clinic systems.",
  },
  {
    title: "Verified sign-in",
    body: "Accounts are confirmed with a one-time code sent to your email before any booking is made.",
  },
  {
    title: "Each clinic sees only its own patients",
    body: "Clinic data is separated on the server, so one practice can never open another’s diary.",
  },
] as const;
