"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/** 单次录音最长时长，防止 Base64 膨胀与内存压力（前端 STT 以 base64 JSON 上传） */
export const MAX_RECORD_SECONDS = 60;

/** 按浏览器能力探测最优录音 MIME：Chrome/Firefox 偏好 webm/opus，iOS Safari 偏好 mp4/aac */
function detectMimeType(): string {
  if (typeof MediaRecorder === "undefined") return "audio/webm";
  const candidates = [
    "audio/webm;codecs=opus",
    "audio/webm",
    "audio/mp4",
    "audio/aac",
    "audio/mpeg",
  ];
  for (const type of candidates) {
    if (MediaRecorder.isTypeSupported(type)) return type;
  }
  return "audio/webm";
}

export interface VoiceRecorderState {
  /** 是否正在录音 */
  recording: boolean;
  /** 已录音秒数（用于 UI 倒计时） */
  seconds: number;
  /** 开始录音 */
  start: () => Promise<void>;
  /** 停止录音并返回 { base64, mimeType }；未录到内容返回 null */
  stop: () => Promise<{ base64: string; mimeType: string } | null>;
  /** 取消录音（丢弃音频） */
  cancel: () => void;
  /** 浏览器不支持录音时的降级提示 */
  unsupported: boolean;
}

export function useVoiceRecorder(): VoiceRecorderState {
  const [recording, setRecording] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [unsupported, setUnsupported] = useState(false);

  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const hardStopRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mimeRef = useRef<string>("audio/webm");
  const resolveRef = useRef<
    ((v: { base64: string; mimeType: string } | null) => void) | null
  >(null);

  useEffect(() => {
    if (
      typeof window === "undefined" ||
      typeof MediaRecorder === "undefined" ||
      !navigator.mediaDevices?.getUserMedia
    ) {
      setUnsupported(true);
    }
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      if (hardStopRef.current) clearTimeout(hardStopRef.current);
      streamRef.current?.getTracks().forEach((t) => {
        t.stop();
      });
    };
  }, []);

  const cleanup = useCallback(() => {
    if (timerRef.current) clearInterval(timerRef.current);
    if (hardStopRef.current) clearTimeout(hardStopRef.current);
    timerRef.current = null;
    hardStopRef.current = null;
    streamRef.current?.getTracks().forEach((t) => {
      t.stop();
    });
    streamRef.current = null;
    recorderRef.current = null;
    setRecording(false);
    setSeconds(0);
  }, []);

  const start = useCallback(async () => {
    if (recording || unsupported) return;
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;
      const mime = detectMimeType();
      mimeRef.current = mime;
      const recorder = new MediaRecorder(stream, { mimeType: mime });
      recorderRef.current = recorder;
      chunksRef.current = [];

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };

      recorder.onstop = () => {
        const blobs = chunksRef.current;
        cleanup();
        if (blobs.length === 0) {
          resolveRef.current?.(null);
          resolveRef.current = null;
          return;
        }
        const blob = new Blob(blobs, { type: mimeRef.current });
        const reader = new FileReader();
        reader.onloadend = () => {
          const result = reader.result as string;
          const base64 = result.includes(",") ? result.split(",")[1] : result;
          resolveRef.current?.({ base64, mimeType: mimeRef.current });
          resolveRef.current = null;
        };
        reader.readAsDataURL(blob);
      };

      recorder.start();
      setRecording(true);
      setSeconds(0);
      timerRef.current = setInterval(() => {
        setSeconds((s) => {
          if (s + 1 >= MAX_RECORD_SECONDS) {
            recorderRef.current?.stop();
            return MAX_RECORD_SECONDS;
          }
          return s + 1;
        });
      }, 1000);
      // 硬上限兜底：到达 60s 强制停止
      hardStopRef.current = setTimeout(() => {
        recorderRef.current?.stop();
      }, MAX_RECORD_SECONDS * 1000);
    } catch {
      cleanup();
      setUnsupported(true);
    }
  }, [recording, unsupported, cleanup]);

  const stop = useCallback((): Promise<{
    base64: string;
    mimeType: string;
  } | null> => {
    return new Promise((resolve) => {
      if (!recorderRef.current || recorderRef.current.state === "inactive") {
        resolve(null);
        return;
      }
      resolveRef.current = resolve;
      recorderRef.current.stop();
    });
  }, []);

  const cancel = useCallback(() => {
    resolveRef.current = null;
    if (recorderRef.current && recorderRef.current.state !== "inactive") {
      recorderRef.current.stop();
    } else {
      cleanup();
    }
  }, [cleanup]);

  return {
    recording,
    seconds,
    start,
    stop,
    cancel,
    unsupported,
  };
}
