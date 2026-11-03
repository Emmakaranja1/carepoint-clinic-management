"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { ApiError } from "@/lib/api";

export interface Loaded<T> {
  data: T | null;
  error: string;
  status: number;
  loading: boolean;
  reload: () => void;
}

export function useLoad<T>(load: () => Promise<T>, deps: unknown[]): Loaded<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState("");
  const [status, setStatus] = useState(0);
  const [loading, setLoading] = useState(true);
  const seq = useRef(0);
  // eslint-disable-next-line react-hooks/exhaustive-deps -- caller controls deps
  const run = useCallback(load, deps);

  const reload = useCallback(() => {
    const id = ++seq.current;
    setLoading(true);
    setError("");
    run()
      .then((d) => id === seq.current && setData(d))
      .catch((e: unknown) => {
        if (id !== seq.current) return;
        setStatus(e instanceof ApiError ? e.status : 0);
        setError(e instanceof ApiError ? e.message : "Something went wrong");
      })
      .finally(() => id === seq.current && setLoading(false));
  }, [run]);

  useEffect(reload, [reload]);

  return { data, error, status, loading, reload };
}
