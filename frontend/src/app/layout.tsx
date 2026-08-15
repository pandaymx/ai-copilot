import type { Metadata, Viewport } from "next";
import Script from "next/script";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getMessages } from "next-intl/server";
import { Toaster } from "sonner";
import "./globals.css";
import { PwaRegister } from "@/components/pwa-register";
import { ThemeProvider } from "@/components/theme-provider";
import { cn } from "@/lib/utils";

// 防闪烁主题脚本：必须在 hydration 前于 <head> 同步执行（beforeInteractive）。
// 参数需与下方 <ThemeProvider> 的 props 保持一致。
const THEME_ANTI_FLASH = `(function(){try{var m=window.matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light";var s="theme";var st="system";var e=true;var v=null;var a=localStorage.getItem(s)||st;var t=(e&&a==="system")?m:a;var val=v&&v[t]?v[t]:t;var el=document.documentElement;var attrs=["class"];for(var i=0;i<attrs.length;i++){var at=attrs[i];if(at==="class"){el.classList.remove("light","dark");if(val)el.classList.add(val);}else if(at.indexOf("data-")===0){if(val)el.setAttribute(at,val);else el.removeAttribute(at);}}el.style.colorScheme=t;}})();`;

const inter = { variable: "font-sans" };
const geistMono = { variable: "--font-geist-mono" };

export const metadata: Metadata = {
  title: "AI Copilot Pro - 智能 AI 助手",
  description: "基于 Spring AI 与 Next.js 的高可用个人 AI 助手与智能 Copilot",
  manifest: "/manifest.json",
  icons: {
    icon: "/icons/icon.svg",
    apple: "/icons/icon.svg",
  },
  appleWebApp: {
    capable: true,
    statusBarStyle: "black-translucent",
    title: "AI Copilot Pro",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#09090b" },
  ],
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const locale = await getLocale();
  const messages = await getMessages();

  return (
    <html
      lang={locale === "en" ? "en" : "zh-CN"}
      suppressHydrationWarning
      className={cn(
        "h-full",
        "antialiased",
        geistMono.variable,
        "font-sans",
        inter.variable,
      )}
    >
      <head>
        <Script
          id="theme-anti-flash"
          strategy="beforeInteractive"
          // biome-ignore lint/security/noDangerouslySetInnerHtml: controlled, constant anti-flash theme script (no user input)
          dangerouslySetInnerHTML={{ __html: THEME_ANTI_FLASH }}
        />
      </head>
      <body className="min-h-full flex flex-col">
        <NextIntlClientProvider messages={messages}>
          <ThemeProvider
            attribute="class"
            defaultTheme="system"
            enableSystem
            disableTransitionOnChange
          >
            {children}
            <PwaRegister />
            <Toaster position="top-right" richColors />
          </ThemeProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
