"use client";

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import { applyApiErrors } from "@/features/auth/ui";
import { inviteStaff, type StaffMember } from "./api";
import { ArrowLeftIcon, MailIcon, ShieldIcon } from "./icons";
import { addStaffSchema, type AddStaffValues } from "./schemas";
import { Notice, PageTitle, fieldClass, panelClass, primaryButton, secondaryButton } from "./ui";
import { useAdminWorkspace } from "./workspace";

export function AddStaffPage() {
  const { clinic } = useAdminWorkspace();
  const [created, setCreated] = useState<StaffMember | null>(null);
  const [formError, setFormError] = useState("");
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AddStaffValues>({
    resolver: standardSchemaResolver(addStaffSchema),
    defaultValues: { role: "RECEPTIONIST", fullName: "", email: "", phone: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    try {
      setCreated(await inviteStaff(clinic.clinicId, { ...values, phone: values.phone || undefined }));
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    }
  });

  return (
    <div className="space-y-6">
      <Link href="/clinic-admin/staff" className="inline-flex items-center gap-1.5 text-sm font-medium text-zinc-500 hover:text-heading">
        <ArrowLeftIcon className="h-4 w-4" />
        Staff
      </Link>
      <PageTitle
        eyebrow="Add staff"
        title="Invite a receptionist"
        description="They'll receive a secure link by email to set their own password. You never see or share their password."
      />

      {created ? (
        <InvitationSent
          member={created}
          onAnother={() => {
            reset();
            setCreated(null);
          }}
        />
      ) : (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <form onSubmit={onSubmit} noValidate className={`${panelClass} divide-y divide-zinc-100`}>
            {formError && <div className="p-5"><Notice tone="error">{formError}</Notice></div>}

            <FormSection title="Staff details" description="Use the work email they'll sign in with.">
              <TextField id="fullName" label="Full name" error={errors.fullName?.message} autoComplete="off" {...register("fullName")} />
              <TextField id="email" label="Work email" type="email" error={errors.email?.message} autoComplete="off" {...register("email")} />
              <TextField
                id="phone"
                label="Phone"
                optional
                type="tel"
                inputMode="tel"
                placeholder="+254712345678"
                error={errors.phone?.message}
                {...register("phone")}
              />
            </FormSection>

            <FormSection title="Role and clinic" description="Access is limited to the clinic you're managing.">
              <fieldset>
                <legend className="mb-2 text-sm font-medium text-heading">Role</legend>
                <label className="flex cursor-pointer gap-3 rounded-xl border-2 border-brand-500 bg-brand-50/50 p-4">
                  <input type="radio" value="RECEPTIONIST" className="mt-1 h-4 w-4 accent-brand-500" {...register("role")} />
                  <span>
                    <span className="block text-sm font-semibold text-heading">Receptionist</span>
                    <span className="mt-0.5 block text-sm text-zinc-600">
                      Runs the front desk: the clinic agenda, booking on behalf of patients, rescheduling and visit status.
                      Cannot manage staff or clinic settings.
                    </span>
                  </span>
                </label>
              </fieldset>
              <div>
                <p className="mb-1.5 text-sm font-medium text-heading">Assigned clinic</p>
                <p className="flex items-center gap-2 rounded-lg border border-zinc-200 bg-zinc-50 px-3.5 py-2.5 text-sm text-zinc-700">
                  <ShieldIcon className="h-4 w-4 text-zinc-400" />
                  {clinic.clinicName}
                </p>
              </div>
            </FormSection>

            <div className="flex flex-col-reverse gap-3 p-5 sm:flex-row sm:justify-end">
              <Link href="/clinic-admin/staff" className={secondaryButton}>Cancel</Link>
              <button type="submit" className={primaryButton} disabled={isSubmitting}>
                <MailIcon className="h-4 w-4" />
                {isSubmitting ? "Sending invitation…" : "Create account & send invitation"}
              </button>
            </div>
          </form>

          <aside className="h-fit rounded-2xl bg-navy p-6 text-white">
            <h2 className="text-sm font-semibold">What happens next</h2>
            <ol className="mt-4 space-y-4 text-sm text-white/75">
              {[
                "We create their account for this clinic with the receptionist role.",
                "They get an email with a single-use link, valid for 72 hours.",
                "They choose their own password — the account activates immediately.",
                "You can resend the link or deactivate access at any time.",
              ].map((step, i) => (
                <li key={step} className="flex gap-3">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-white/10 text-xs font-semibold text-white">{i + 1}</span>
                  {step}
                </li>
              ))}
            </ol>
          </aside>
        </div>
      )}
    </div>
  );
}

function InvitationSent({ member, onAnother }: { member: StaffMember; onAnother: () => void }) {
  return (
    <div className={`${panelClass} p-6 sm:p-8`}>
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-700">
        <MailIcon className="h-6 w-6" />
      </span>
      <h2 className="mt-4 text-xl font-semibold text-heading">Invitation sent to {member.fullName}</h2>
      <p className="mt-1 text-sm text-zinc-600">
        We emailed <span className="font-medium text-heading">{member.email}</span> a link to set their password.
        Their account becomes active as soon as they do.
      </p>
      <div className="mt-6 flex flex-col gap-3 sm:flex-row">
        <Link href={`/clinic-admin/staff/${member.membershipId}`} className={primaryButton}>View staff member</Link>
        <button type="button" className={secondaryButton} onClick={onAnother}>Invite another receptionist</button>
      </div>
    </div>
  );
}

function FormSection({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <section className="grid gap-4 p-5 md:grid-cols-[12rem_minmax(0,1fr)] md:gap-8">
      <div>
        <h2 className="text-sm font-semibold text-heading">{title}</h2>
        <p className="mt-1 text-sm text-zinc-500">{description}</p>
      </div>
      <div className="space-y-4">{children}</div>
    </section>
  );
}

type TextFieldProps = React.InputHTMLAttributes<HTMLInputElement> & {
  id: string;
  label: string;
  error?: string;
  optional?: boolean;
};

function TextField({ id, label, error, optional, ref, ...rest }: TextFieldProps & { ref?: React.Ref<HTMLInputElement> }) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 flex items-baseline justify-between text-sm font-medium text-heading">
        {label}
        {optional && <span className="text-xs font-normal text-zinc-400">Optional</span>}
      </label>
      <input
        id={id}
        ref={ref}
        className={fieldClass}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        {...rest}
      />
      {error && <p id={`${id}-error`} className="mt-1.5 text-sm text-red-600">{error}</p>}
    </div>
  );
}
