"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { standardSchemaResolver } from "@hookform/resolvers/standard-schema";

import { useSession, type SessionUser } from "@/features/auth/session";
import { Field, applyApiErrors, inputClass } from "@/features/auth/ui";
import { cardClass, Notice, primaryButtonClass } from "@/features/dashboard/ui";
import { updateProfile } from "./api";
import { profileSchema, type ProfileValues } from "./schemas";

export function ProfileForm({ user }: { user: SessionUser }) {
  const setUser = useSession((s) => s.setUser);
  const [formError, setFormError] = useState("");
  const [saved, setSaved] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileValues>({
    resolver: standardSchemaResolver(profileSchema),
    defaultValues: { fullName: user.fullName, phone: user.phone ?? "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError("");
    setSaved(false);
    try {
      const updated = await updateProfile(values);
      setUser(updated);
      reset({ fullName: updated.fullName, phone: updated.phone ?? "" });
      setSaved(true);
    } catch (e) {
      applyApiErrors(e, setError, setFormError);
    }
  });

  return (
    <section className={cardClass} aria-labelledby="profile-heading">
      <h2 id="profile-heading" className="text-lg font-bold text-heading">Profile</h2>
      <p className="mt-1 text-sm text-zinc-500">This is how clinics see you when you book.</p>
      <form onSubmit={onSubmit} className="mt-5 space-y-4" noValidate>
        {formError && <Notice tone="error">{formError}</Notice>}
        {saved && <Notice>Profile updated.</Notice>}
        <Field label="Full name" error={errors.fullName?.message}>
          <input className={inputClass} autoComplete="name" {...register("fullName")} />
        </Field>
        <Field label="Email">
          <input className={`${inputClass} cursor-not-allowed bg-zinc-50 text-zinc-500`} value={user.email} readOnly />
        </Field>
        <Field label="Phone (optional)" error={errors.phone?.message}>
          <input className={inputClass} type="tel" autoComplete="tel" placeholder="+254712345678" {...register("phone")} />
        </Field>
        <button type="submit" className={primaryButtonClass} disabled={isSubmitting || !isDirty}>
          {isSubmitting ? "Saving…" : "Save changes"}
        </button>
      </form>
    </section>
  );
}
