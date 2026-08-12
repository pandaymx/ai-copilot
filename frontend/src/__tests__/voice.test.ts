import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import { DEFAULT_TTS_VOICE, transcribe, tts } from "../lib/voice";

let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;

beforeEach(() => {
  mockFetch = mock();
  globalThis.fetch = mockFetch as unknown as typeof fetch;
});

afterEach(() => {
  globalThis.fetch = originalFetch;
});

describe("Voice API Wrapper - lib/voice.ts", () => {
  describe("TTS (Text-to-Speech)", () => {
    it("should export DEFAULT_TTS_VOICE constant", () => {
      expect(DEFAULT_TTS_VOICE).toBe("alloy");
    });

    it("should throw error when text is empty or whitespace only", async () => {
      await expect(tts("   ")).rejects.toThrow("语音合成内容为空");
    });

    it("should send POST request to /api/chat/tts and return Blob on success", async () => {
      const mockBlob = new Blob(["audio data"], { type: "audio/mp3" });
      mockFetch.mockResolvedValueOnce(
        new Response(mockBlob, {
          status: 200,
          headers: { "Content-Type": "audio/mp3" },
        }),
      );

      const result = await tts("Hello world", "nova");
      expect(result).toBeInstanceOf(Blob);
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/chat/tts",
        expect.objectContaining({
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ text: "Hello world", voice: "nova" }),
        }),
      );
    });

    it("should format error message when HTTP response is not ok", async () => {
      mockFetch.mockResolvedValueOnce(
        new Response("Quota Exceeded", {
          status: 429,
          statusText: "Too Many Requests",
        }),
      );

      await expect(tts("Hello")).rejects.toThrow(
        "语音合成失败（429）：Quota Exceeded",
      );
    });
  });

  describe("Transcribe (Speech-to-Text)", () => {
    it("should send POST request to /api/chat/transcribe and return trimmed text", async () => {
      mockFetch.mockResolvedValueOnce(
        new Response("  Hello AI  \n", { status: 200 }),
      );

      const text = await transcribe("data:audio/webm;base64,ABC", "audio/webm");
      expect(text).toBe("Hello AI");
      expect(mockFetch).toHaveBeenCalledWith(
        "/api/chat/transcribe",
        expect.objectContaining({
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            data: "data:audio/webm;base64,ABC",
            mimeType: "audio/webm",
          }),
        }),
      );
    });

    it("should format error message when HTTP response is not ok", async () => {
      mockFetch.mockResolvedValueOnce(
        new Response("Invalid Audio Stream", {
          status: 400,
          statusText: "Bad Request",
        }),
      );

      await expect(transcribe("invalid_base64", "audio/mp4")).rejects.toThrow(
        "语音识别失败（400）：Invalid Audio Stream",
      );
    });
  });
});
