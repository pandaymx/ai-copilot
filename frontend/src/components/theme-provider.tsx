"use client";

import * as React from "react";

type Theme = string;
type Attribute = string | string[];
type ValueObject = Record<string, string>;

interface ThemeProviderProps {
  children: React.ReactNode;
  attribute?: Attribute;
  defaultTheme?: string;
  enableSystem?: boolean;
  enableColorScheme?: boolean;
  disableTransitionOnChange?: boolean;
  storageKey?: string;
  themes?: string[];
  value?: ValueObject;
  systemTheme?: string;
}

interface ThemeContextValue {
  theme: Theme | undefined;
  setTheme: (theme: Theme) => void;
  forcedTheme: Theme | undefined;
  resolvedTheme: Theme | undefined;
  themes: string[];
  systemTheme: Theme | undefined;
}

const ThemeContext = React.createContext<ThemeContextValue | undefined>(
  undefined,
);

const DEFAULT_STORAGE_KEY = "theme";
const DEFAULT_THEMES = ["light", "dark"];
const MEDIA = "(prefers-color-scheme: dark)";

function getSystemTheme(): "dark" | "light" {
  if (typeof window === "undefined") return "light";
  return window.matchMedia(MEDIA).matches ? "dark" : "light";
}

function applyThemeClasses(
  attribute: Attribute,
  value: string | undefined,
  valueObject?: ValueObject,
): void {
  const el = document.documentElement;
  const values = valueObject ? Object.values(valueObject) : null;
  const list = (attr: string) => {
    const isClass = attr === "class";
    const resolved = valueObject && value ? valueObject[value] || value : value;
    if (isClass) {
      const classes = values ?? [value];
      for (const c of classes ?? []) {
        if (c) el.classList.remove(c);
      }
      if (resolved) el.classList.add(resolved);
    } else if (attr.startsWith("data-")) {
      if (resolved) el.setAttribute(attr, resolved);
      else el.removeAttribute(attr);
    }
  };
  if (Array.isArray(attribute)) attribute.forEach(list);
  else list(attribute);
}

function setColorScheme(theme: string, enableColorScheme: boolean): void {
  if (!enableColorScheme) return;
  const el = document.documentElement;
  const scheme = ["light", "dark"].includes(theme) ? theme : null;
  if (scheme) el.style.colorScheme = scheme;
}

export function ThemeProvider({
  children,
  attribute = "class",
  defaultTheme = "system",
  enableSystem = true,
  enableColorScheme = true,
  disableTransitionOnChange = false,
  storageKey = DEFAULT_STORAGE_KEY,
  themes = DEFAULT_THEMES,
  value,
}: ThemeProviderProps) {
  const [theme, setThemeState] = React.useState<string | undefined>(() => {
    if (typeof window === "undefined") return defaultTheme;
    const stored = (() => {
      try {
        return localStorage.getItem(storageKey) || undefined;
      } catch {
        return undefined;
      }
    })();
    return stored || defaultTheme;
  });

  const [systemTheme, setSystemTheme] = React.useState<string>(() =>
    getSystemTheme(),
  );

  const applyTheme = React.useCallback(
    (next: string) => {
      if (disableTransitionOnChange) {
        const style = document.createElement("style");
        style.appendChild(
          document.createTextNode(
            "*,*::before,*::after{-webkit-transition:none!important;transition:none!important;}",
          ),
        );
        document.head.appendChild(style);
        window.getComputedStyle(document.body);
        setTimeout(() => {
          if (style.parentNode) style.parentNode.removeChild(style);
        }, 1);
      }
      applyThemeClasses(attribute, next, value);
      setColorScheme(next, enableColorScheme);
    },
    [attribute, value, disableTransitionOnChange, enableColorScheme],
  );

  const setTheme = React.useCallback(
    (next: string) => {
      setThemeState(next);
      try {
        localStorage.setItem(storageKey, next);
      } catch {
        /* ignore */
      }
      applyTheme(next === "system" ? systemTheme : next);
    },
    [storageKey, applyTheme, systemTheme],
  );

  // 系统主题变化时同步（仅当当前主题为 system）
  React.useEffect(() => {
    if (!enableSystem) return;
    const mq = window.matchMedia(MEDIA);
    const handler = (e: MediaQueryListEvent) => {
      const sys = e.matches ? "dark" : "light";
      setSystemTheme(sys);
      if (theme === "system") applyTheme(sys);
    };
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, [enableSystem, theme, applyTheme]);

  // storage 事件：多标签页同步
  React.useEffect(() => {
    const handler = (e: StorageEvent) => {
      if (e.key === storageKey) {
        const next = e.newValue || defaultTheme;
        setThemeState(next);
        applyTheme(next === "system" ? systemTheme : next);
      }
    };
    window.addEventListener("storage", handler);
    return () => window.removeEventListener("storage", handler);
  }, [storageKey, applyTheme, defaultTheme, systemTheme]);

  // 初始 & theme 变化应用
  React.useEffect(() => {
    const current = theme ?? defaultTheme;
    applyTheme(current === "system" ? systemTheme : current);
  }, [theme, systemTheme, defaultTheme, applyTheme]);

  const allThemes = React.useMemo(
    () => (enableSystem ? [...themes, "system"] : themes),
    [enableSystem, themes],
  );

  const contextValue = React.useMemo<ThemeContextValue>(
    () => ({
      theme,
      setTheme,
      forcedTheme: undefined,
      resolvedTheme: theme === "system" ? systemTheme : theme,
      themes: allThemes,
      systemTheme,
    }),
    [theme, setTheme, systemTheme, allThemes],
  );

  return (
    <ThemeContext.Provider value={contextValue}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  const ctx = React.useContext(ThemeContext);
  if (!ctx) {
    return {
      theme: undefined,
      setTheme: () => {},
      forcedTheme: undefined,
      resolvedTheme: undefined,
      themes: DEFAULT_THEMES,
      systemTheme: "light",
    };
  }
  return ctx;
}
