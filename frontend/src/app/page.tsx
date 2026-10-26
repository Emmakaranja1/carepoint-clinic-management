import Image from "next/image";

import { Footer } from "@/components/Footer";

const featureChips = [
  {
    title: "Expert Doctors",
    sub: "Top-rated professionals",
    icon: "M12 4a4 4 0 1 1 0 8 4 4 0 0 1 0-8Zm0 10c4.4 0 8 2.2 8 5v1H4v-1c0-2.8 3.6-5 8-5Z",
  },
  {
    title: "Easy Booking",
    sub: "In minutes, not hours",
    icon: "M7 3v3m10-3v3M5 7h14a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1Zm3 5h4m-4 4h7",
  },
  {
    title: "Secure & Private",
    sub: "Your data, our priority",
    icon: "M12 3l8 3v5c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3Zm-1.5 12l6-6-1.4-1.4-4.6 4.6-2.1-2.1L7 11.5l3.5 3.5Z",
  },
  {
    title: "24/7 Support",
    sub: "We're always here",
    icon: "M4 13a8 8 0 0 1 16 0M4 13v4a2 2 0 0 0 2 2h1v-6H6a2 2 0 0 0-2 2v-2Zm16 0v4a2 2 0 0 1-2 2h-1v-6h1a2 2 0 0 1 2 2v-2Z",
  },
] as const;

const whyChooseUs = [
  {
    title: "Top-Rated Specialists",
    description: "Access to certified doctors and healthcare professionals across various specialties.",
    icon: "M12 4a4 4 0 1 1 0 8 4 4 0 0 1 0-8Zm0 10c4.4 0 8 2.2 8 5v1H4v-1c0-2.8 3.6-5 8-5Z",
  },
  {
    title: "Easy Scheduling",
    description: "Book appointments online in just a few clicks. No more waiting on hold.",
    icon: "M7 3v3m10-3v3M5 7h14a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1Zm3 5h4m-4 4h7",
  },
  {
    title: "Secure Platform",
    description: "Your health data is protected with industry-standard encryption and privacy controls.",
    icon: "M12 3l8 3v5c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6l8-3Zm-1.5 12l6-6-1.4-1.4-4.6 4.6-2.1-2.1L7 11.5l3.5 3.5Z",
  },
  {
    title: "24/7 Support",
    description: "Our dedicated support team is available around the clock to assist you.",
    icon: "M4 13a8 8 0 0 1 16 0M4 13v4a2 2 0 0 0 2 2h1v-6H6a2 2 0 0 0-2 2v-2Zm16 0v4a2 2 0 0 1-2 2h-1v-6h1a2 2 0 0 1 2 2v-2Z",
  },
] as const;

const howItWorks = [
  {
    step: "01",
    title: "Search for a Doctor",
    description: "Find the right specialist by searching for their name, specialty, or condition.",
  },
  {
    step: "02",
    title: "Choose a Time Slot",
    description: "Select from available appointment times that work best for your schedule.",
  },
  {
    step: "03",
    title: "Book Appointment",
    description: "Confirm your booking and receive instant confirmation with appointment details.",
  },
] as const;

const testimonials = [
  {
    name: "Sarah Johnson",
    role: "Patient",
    text: "CarePoint made it so easy to book my appointment. The process was seamless and I found a great doctor in minutes.",
  },
  {
    name: "Michael Kamau",
    role: "Patient",
    text: "I love how I can compare doctors and read reviews before booking. The platform is user-friendly and efficient.",
  },
  {
    name: "Grace Wanjiku",
    role: "Patient",
    text: "The 24/7 support is amazing. I had a question and got help immediately. Highly recommend CarePoint to everyone.",
  },
] as const;

export default function Home() {
  return (
    <>
      <main className="flex-1">
        {/* hero */}
        <section className="relative overflow-hidden bg-gradient-to-br from-canvas via-white to-brand-50">
          <div className="absolute inset-0 bg-[url('/hero-doctor.png')] bg-cover bg-center opacity-5" />
          <div className="relative mx-auto grid max-w-7xl items-center gap-12 px-6 py-20 lg:grid-cols-2 lg:gap-16 lg:px-10 lg:py-24">
            <div className="space-y-8">
              <div className="space-y-6">
                <h1 className="text-6xl font-bold leading-[1.1] tracking-tight text-heading sm:text-7xl lg:text-8xl">
                  Your Health.
                  <br />
                  <span className="text-brand-500">Our Priority.</span>
                </h1>
                <p className="max-w-lg text-xl text-zinc-600 leading-relaxed">
                  Book appointments, consult with trusted doctors, and manage your
                  health — all in one place.
                </p>
              </div>

              <div className="flex flex-wrap gap-4">
                <button className="rounded-lg bg-brand-500 px-8 py-4 text-base font-semibold text-white shadow-lg shadow-brand-500/25 transition-all hover:bg-brand-600 hover:shadow-xl">
                  Find a Doctor
                </button>
                <button className="rounded-lg border-2 border-brand-500 px-8 py-4 text-base font-semibold text-brand-500 transition-all hover:bg-brand-50">
                  Learn More
                </button>
              </div>
            </div>

            <div className="relative">
              <div className="absolute -inset-4 rounded-3xl bg-gradient-to-r from-brand-400 to-brand-600 opacity-20 blur-2xl" />
              <Image
                src="/hero-doctor.png"
                alt="CarePoint doctor"
                width={600}
                height={600}
                priority
                className="relative mx-auto w-full max-w-lg rounded-3xl object-cover shadow-2xl"
              />
            </div>
          </div>
        </section>

        {/* feature chips */}
        <section
          id="services"
          className="mx-auto grid max-w-7xl grid-cols-2 gap-4 px-6 py-20 lg:grid-cols-4 lg:px-10"
        >
          {featureChips.map((f) => (
            <div
              key={f.title}
              className="group flex items-center gap-4 rounded-2xl bg-white px-6 py-6 shadow-lg shadow-zinc-200/50 transition-all hover:shadow-xl hover:shadow-brand-500/10 hover:-translate-y-1"
            >
              <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-brand-50 to-brand-100 transition-all group-hover:scale-110">
                <svg
                  viewBox="0 0 24 24"
                  className="h-6 w-6 fill-brand-500"
                  aria-hidden="true"
                >
                  <path d={f.icon} />
                </svg>
              </span>
              <span>
                <span className="block text-base font-semibold text-heading">
                  {f.title}
                </span>
                <span className="block text-sm text-zinc-500">{f.sub}</span>
              </span>
            </div>
          ))}
        </section>

        {/* Why Choose CarePoint */}
        <section className="bg-gradient-to-b from-white to-canvas">
          <div className="mx-auto max-w-7xl px-6 py-28 lg:px-10">
            <div className="mx-auto max-w-2xl text-center">
              <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-brand-50 px-5 py-2 text-base font-medium text-brand-700">
                Why Us
              </div>
              <h2 className="text-5xl font-bold text-heading sm:text-6xl">
                Why Choose CarePoint?
              </h2>
              <p className="mt-6 text-xl text-zinc-600 leading-relaxed">
                Experience healthcare made simple, accessible, and reliable.
              </p>
            </div>
            <div className="mt-20 grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
              {whyChooseUs.map((item) => (
                <div
                  key={item.title}
                  className="group rounded-3xl bg-white p-8 shadow-lg shadow-zinc-200/50 transition-all hover:shadow-xl hover:shadow-brand-500/10 hover:-translate-y-1"
                >
                  <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-50 to-brand-100 transition-all group-hover:scale-110">
                    <svg
                      viewBox="0 0 24 24"
                      className="h-7 w-7 fill-brand-500"
                      aria-hidden="true"
                    >
                      <path d={item.icon} />
                    </svg>
                  </span>
                  <h3 className="mt-6 text-2xl font-semibold text-heading">
                    {item.title}
                  </h3>
                  <p className="mt-3 text-base leading-relaxed text-zinc-600">{item.description}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* How It Works */}
        <section id="about" className="bg-canvas">
          <div className="mx-auto max-w-7xl px-6 py-28 lg:px-10">
            <div className="mx-auto max-w-2xl text-center">
              <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-brand-50 px-5 py-2 text-base font-medium text-brand-700">
                Process
              </div>
              <h2 className="text-5xl font-bold text-heading sm:text-6xl">
                How It Works
              </h2>
              <p className="mt-6 text-xl text-zinc-600 leading-relaxed">
                Book your appointment in three simple steps.
              </p>
            </div>
            <div className="mt-20 grid gap-8 sm:grid-cols-3">
              {howItWorks.map((item, index) => (
                <div
                  key={item.step}
                  className="group relative text-center"
                >
                  <div className="absolute left-1/2 top-8 -z-10 hidden h-[calc(100%-2rem)] w-0.5 -translate-x-1/2 bg-gradient-to-b from-brand-200 to-transparent sm:block" />
                  <span className="relative inline-flex h-20 w-20 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-600 text-2xl font-bold text-white shadow-lg shadow-brand-500/25 transition-all group-hover:scale-110 group-hover:shadow-xl">
                    {item.step}
                  </span>
                  <h3 className="mt-8 text-2xl font-semibold text-heading">
                    {item.title}
                  </h3>
                  <p className="mt-3 text-base leading-relaxed text-zinc-600">{item.description}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Testimonials */}
        <section className="bg-gradient-to-b from-canvas to-white">
          <div className="mx-auto max-w-7xl px-6 py-28 lg:px-10">
            <div className="mx-auto max-w-2xl text-center">
              <div className="mb-4 inline-flex items-center gap-2 rounded-full bg-brand-50 px-5 py-2 text-base font-medium text-brand-700">
                Testimonials
              </div>
              <h2 className="text-5xl font-bold text-heading sm:text-6xl">
                What Our Patients Say
              </h2>
              <p className="mt-6 text-xl text-zinc-600 leading-relaxed">
                Real stories from real patients who have experienced the CarePoint difference.
              </p>
            </div>
            <div className="mt-20 grid gap-8 sm:grid-cols-3">
              {testimonials.map((item) => (
                <div
                  key={item.name}
                  className="group rounded-3xl bg-white p-8 shadow-lg shadow-zinc-200/50 transition-all hover:shadow-xl hover:shadow-brand-500/10 hover:-translate-y-1"
                >
                  <div className="mb-4 flex gap-1">
                    {[...Array(5)].map((_, i) => (
                      <svg
                        key={i}
                        className="h-5 w-5 fill-brand-400"
                        viewBox="0 0 20 20"
                      >
                        <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                      </svg>
                    ))}
                  </div>
                  <p className="text-lg leading-relaxed text-zinc-600">&ldquo;{item.text}&rdquo;</p>
                  <div className="mt-6 flex items-center gap-4">
                    <span className="flex h-12 w-12 items-center justify-center rounded-full bg-gradient-to-br from-brand-100 to-brand-200 text-brand-600 font-semibold text-lg">
                      {item.name.charAt(0)}
                    </span>
                    <div>
                      <p className="text-base font-semibold text-heading">{item.name}</p>
                      <p className="text-sm text-zinc-500">{item.role}</p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* CTA Section */}
        <section className="bg-brand-500">
          <div className="mx-auto max-w-7xl px-6 py-28 text-center lg:px-10">
            <h2 className="text-4xl font-bold text-white sm:text-5xl">
              Ready to Take Control of Your Health?
            </h2>
            <p className="mt-6 text-xl text-brand-100 leading-relaxed">
              Join thousands of patients who trust CarePoint for their healthcare needs.
            </p>
            <div className="mt-10 flex flex-col gap-4 sm:flex-row sm:justify-center">
              <button className="rounded-lg bg-white px-10 py-4 text-base font-semibold text-brand-600 shadow-lg transition-all hover:bg-brand-50 hover:shadow-xl">
                Get Started Free
              </button>
              <button className="rounded-lg border-2 border-white px-10 py-4 text-base font-semibold text-white transition-all hover:bg-white/10">
                Learn More
              </button>
            </div>
          </div>
        </section>
      </main>
      <Footer />
    </>
  );
}
