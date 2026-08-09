/** 语音能力前端封装：TTS（文本转语音）与 STT（语音转文本）。 */

export const DEFAULT_TTS_VOICE = "alloy";

/**
 * 文本转语音：调用后端 POST /api/chat/tts，返回 mp3 二进制 Blob。
 */
export async function tts(
  text: string,
  voice?: string,
  signal?: AbortSignal,
): Promise<Blob> {
  if (!text.trim()) {
    throw new Error("语音合成内容为空");
  }
  const res = await fetch("/api/chat/tts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      text,
      ...(voice ? { voice } : {}),
    }),
    signal,
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => "");
    throw new Error(
      `语音合成失败（${res.status}）：${detail || res.statusText}`,
    );
  }
  return res.blob();
}

/**
 * 语音转文本：前端上传 base64 音频，后端复用 Gemini 多模态能力转录。
 * @param data 音频 base64（可带 data: 前缀）
 * @param mimeType 实际音频 MIME，由 useVoiceRecorder 按浏览器能力探测上报
 */
export async function transcribe(
  data: string,
  mimeType: string,
  signal?: AbortSignal,
): Promise<string> {
  const res = await fetch("/api/chat/transcribe", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ data, mimeType }),
    signal,
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => "");
    throw new Error(
      `语音识别失败（${res.status}）：${detail || res.statusText}`,
    );
  }
  return (await res.text()).trim();
}
