import { useCallback, useSyncExternalStore } from "react";

export type ThemePref = "light" | "dark" | "system";
const KEY = "rr.theme";

function readPref(): ThemePref {
  try {
    const v = window.localStorage.getItem(KEY);
    return v === "light" || v === "dark" ? v : "system";
  } catch {
    return "system";
  }
}

const media = typeof window !== "undefined" && window.matchMedia ? window.matchMedia("(prefers-color-scheme: dark)") : null;

let pref: ThemePref = readPref();
const listeners = new Set<() => void>();

function resolved(): "light" | "dark" {
  if (pref === "system") return media?.matches ? "dark" : "light";
  return pref;
}

function apply(): void {
  const dark = resolved() === "dark";
  document.documentElement.classList.toggle("dark", dark);
  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]:not([media])');
  if (meta) meta.content = dark ? "#0b0b14" : "#4f46e5";
  for (const l of listeners) l();
}

media?.addEventListener("change", () => {
  if (pref === "system") apply();
});

export function initTheme(): void {
  apply();
}

function subscribe(l: () => void): () => void {
  listeners.add(l);
  return () => listeners.delete(l);
}

export function useTheme(): { theme: "light" | "dark"; pref: ThemePref; setPref: (p: ThemePref) => void; toggle: () => void } {
  const theme = useSyncExternalStore(subscribe, resolved, () => "light" as const);
  const current = useSyncExternalStore(subscribe, () => pref, () => "system" as const);
  const setPref = useCallback((p: ThemePref) => {
    pref = p;
    try {
      if (p === "system") window.localStorage.removeItem(KEY);
      else window.localStorage.setItem(KEY, p);
    } catch {
      /* ignore */
    }
    apply();
  }, []);
  const toggle = useCallback(() => setPref(resolved() === "dark" ? "light" : "dark"), [setPref]);
  return { theme, pref: current, setPref, toggle };
}
