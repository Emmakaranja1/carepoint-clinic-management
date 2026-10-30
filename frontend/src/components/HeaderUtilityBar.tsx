export function HeaderUtilityBar({ hidden }: { hidden: boolean }) {
  return (
    <div
      className={`grid bg-[#060d18] text-[0.8rem] text-white/65 transition-[grid-template-rows] duration-300 ${
        hidden ? "grid-rows-[0fr]" : "grid-rows-[1fr]"
      }`}
    >
      <div className="overflow-hidden">
        <div className="page-container flex items-center justify-between gap-6 py-2">
          <p className="flex items-center gap-2">
            <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-[#ff6b5e]" />
            <span>
              Medical emergency? Call{" "}
              <a href="tel:999" className="font-semibold text-white underline-offset-4 hover:underline">999</a>
              {" "}or{" "}
              <a href="tel:112" className="font-semibold text-white underline-offset-4 hover:underline">112</a>
            </span>
          </p>
          <ul className="hidden items-center divide-x divide-white/15 md:flex">
            <li className="pr-5">Patient support · Mon–Sat, 8:00–18:00</li>
            <li className="px-5">
              <a href="tel:+254700123456" className="transition hover:text-white">+254 700 123 456</a>
            </li>
            <li className="pl-5">
              <a href="mailto:info@carepoint.co.ke" className="transition hover:text-white">info@carepoint.co.ke</a>
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}
