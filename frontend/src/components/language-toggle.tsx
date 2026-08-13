"use client";

import { Globe } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTransition } from "react";
import { Button } from "@/components/ui/button";

export function LanguageToggle({
  currentLocale = "zh",
}: {
  currentLocale?: string;
}) {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();

  const toggleLanguage = () => {
    const nextLocale = currentLocale === "zh" ? "en" : "zh";
    // biome-ignore lint/suspicious/noDocumentCookie: Setting locale preference cookie
    document.cookie = `NEXT_LOCALE=${nextLocale}; path=/; max-age=31536000; SameSite=Lax`;
    startTransition(() => {
      router.refresh();
    });
  };

  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label="Toggle Language"
      disabled={isPending}
      onClick={toggleLanguage}
      title={currentLocale === "zh" ? "Switch to English" : "切换为中文"}
    >
      <Globe className="size-4" />
      <span className="sr-only">
        {currentLocale === "zh" ? "English" : "中文"}
      </span>
    </Button>
  );
}
