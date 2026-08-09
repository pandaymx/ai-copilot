"use client";

import { useEffect } from "react";

/**
 * PWA Service Worker 客户端注册组件。
 * 在浏览器端自动注册 /sw.js 并支持离线缓存更新。
 */
export function PwaRegister() {
  useEffect(() => {
    if (typeof window === "undefined" || !("serviceWorker" in navigator)) {
      return;
    }

    const registerSw = async () => {
      try {
        const registration = await navigator.serviceWorker.register("/sw.js", {
          scope: "/",
        });

        registration.addEventListener("updatefound", () => {
          const installingWorker = registration.installing;
          if (installingWorker) {
            installingWorker.addEventListener("statechange", () => {
              if (
                installingWorker.state === "installed" &&
                navigator.serviceWorker.controller
              ) {
                // 有静默更新完成
                console.log("[PWA] 静态资源应用版本已准备就绪。");
              }
            });
          }
        });
      } catch (error) {
        console.warn("[PWA] Service Worker 注册警告:", error);
      }
    };

    if (document.readyState === "complete") {
      if ("requestIdleCallback" in window) {
        window.requestIdleCallback(() => registerSw());
      } else {
        setTimeout(registerSw, 1);
      }
    } else {
      window.addEventListener("load", registerSw);
      return () => {
        window.removeEventListener("load", registerSw);
      };
    }
  }, []);

  return null;
}
