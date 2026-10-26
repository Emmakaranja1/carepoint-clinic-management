"use client";

import { useEffect, useRef, useState } from "react";
import Script from "next/script";

import { ApiError } from "@/lib/api";

import { googleLogin } from "./api";
import type { SessionUser } from "./session";

/**
 * Google Identity Services button. Renders only when
 * NEXT_PUBLIC_GOOGLE_CLIENT_ID is set at build time — hidden otherwise,
 * so local dev without a Google app still works.
 */
declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize(cfg: { client_id: string; callback: (r: { credential: string }) => void }): void;
          renderButton(el: HTMLElement, cfg: Record<string, unknown>): void;
        };
      };
    };
  }
}

export function GoogleButton({
  onSuccess,
  onError,
}: {
  onSuccess: (user: SessionUser) => void;
  onError: (message: string) => void;
}) {
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
  const mount = useRef<HTMLDivElement>(null);
  const [gisReady, setGisReady] = useState(false);

  useEffect(() => {
    if (!clientId || !gisReady || !window.google || !mount.current) return;
    mount.current.replaceChildren();
    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: async ({ credential }) => {
        try {
          onSuccess(await googleLogin(credential));
        } catch (e) {
          onError(e instanceof ApiError ? e.message : "Google sign-in failed");
        }
      },
    });
    window.google.accounts.id.renderButton(mount.current, {
      type: "standard",
      theme: "outline",
      size: "large",
      width: mount.current.offsetWidth,
    });
  }, [clientId, gisReady, onSuccess, onError]);

  if (!clientId) return null;

  return (
    <>
      <Script
        src="https://accounts.google.com/gsi/client"
        strategy="afterInteractive"
        onLoad={() => setGisReady(true)}
      />
      <div ref={mount} className="flex justify-center" />
    </>
  );
}
