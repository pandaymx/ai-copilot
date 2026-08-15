import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterAll, beforeEach, describe, expect, it, mock } from "bun:test";
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import {
  MAX_RECORD_SECONDS,
  useVoiceRecorder,
} from "../hooks/useVoiceRecorder";

afterAll(() => {
  if (typeof document !== "undefined") {
    try {
      GlobalRegistrator.unregister();
    } catch {}
  }
});

function renderHook<T>(hookFn: () => T) {
  const result: { current: T } = { current: null as unknown as T };
  function TestComponent() {
    result.current = hookFn();
    return null;
  }
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(React.createElement(TestComponent));
  });
  return {
    result,
    rerender: () => {
      act(() => {
        root.render(React.createElement(TestComponent));
      });
    },
    unmount: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
    },
  };
}

describe("useVoiceRecorder Hook Unit Tests", () => {
  let mockStopTrack: ReturnType<typeof mock>;
  let mockGetUserMedia: ReturnType<typeof mock>;
  let mockMediaRecorderInstance: {
    start: ReturnType<typeof mock>;
    stop: ReturnType<typeof mock>;
    state: string;
    ondataavailable: ((e: { data: Blob }) => void) | null;
    onstop: (() => void) | null;
  };

  beforeEach(() => {
    mockStopTrack = mock();
    const mockStream = {
      getTracks: () => [{ stop: mockStopTrack }],
    };
    mockGetUserMedia = mock().mockResolvedValue(mockStream);

    Object.defineProperty(navigator, "mediaDevices", {
      value: { getUserMedia: mockGetUserMedia },
      writable: true,
      configurable: true,
    });

    mockMediaRecorderInstance = {
      start: mock(),
      stop: mock(() => {
        mockMediaRecorderInstance.state = "inactive";
        if (mockMediaRecorderInstance.onstop) {
          mockMediaRecorderInstance.onstop();
        }
      }),
      state: "inactive",
      ondataavailable: null,
      onstop: null,
    };

    function MockMediaRecorder(this: typeof mockMediaRecorderInstance) {
      mockMediaRecorderInstance.state = "recording";
      return mockMediaRecorderInstance;
    }
    MockMediaRecorder.isTypeSupported = (mime: string) => mime === "audio/webm";

    globalThis.MediaRecorder =
      MockMediaRecorder as unknown as typeof MediaRecorder;

    class MockFileReader {
      result: string | null = null;
      onloadend: (() => void) | null = null;
      readAsDataURL(_blob: Blob) {
        this.result = "data:audio/webm;base64,MOCK_BASE64_DATA";
        if (this.onloadend) this.onloadend();
      }
    }

    globalThis.FileReader = MockFileReader as unknown as typeof FileReader;
  });

  it("should export MAX_RECORD_SECONDS = 60", () => {
    expect(MAX_RECORD_SECONDS).toBe(60);
  });

  it("should initialize with default idle state", () => {
    const { result, unmount } = renderHook(() => useVoiceRecorder());
    expect(result.current.recording).toBeFalse();
    expect(result.current.seconds).toBe(0);
    expect(result.current.unsupported).toBeFalse();
    unmount();
  });

  it("should start recording and request audio stream", async () => {
    const { result, unmount } = renderHook(() => useVoiceRecorder());

    await act(async () => {
      await result.current.start();
    });

    expect(mockGetUserMedia).toHaveBeenCalledWith({ audio: true });
    expect(mockMediaRecorderInstance.start).toHaveBeenCalled();
    expect(result.current.recording).toBeTrue();

    unmount();
  });

  it("should stop recording and return base64 payload when stop() is called", async () => {
    const { result, unmount } = renderHook(() => useVoiceRecorder());

    await act(async () => {
      await result.current.start();
    });

    // Simulate audio data available
    if (mockMediaRecorderInstance.ondataavailable) {
      mockMediaRecorderInstance.ondataavailable({
        data: new Blob(["audio_chunk"], { type: "audio/webm" }),
      });
    }

    let stopResult: { base64: string; mimeType: string } | null = null;
    await act(async () => {
      const promise = result.current.stop();
      stopResult = await promise;
    });

    expect(stopResult).toEqual({
      base64: "MOCK_BASE64_DATA",
      mimeType: "audio/webm",
    });
    expect(result.current.recording).toBeFalse();
    expect(mockStopTrack).toHaveBeenCalled();

    unmount();
  });

  it("should cancel recording without resolving base64 data", async () => {
    const { result, unmount } = renderHook(() => useVoiceRecorder());

    await act(async () => {
      await result.current.start();
    });

    act(() => {
      result.current.cancel();
    });

    expect(result.current.recording).toBeFalse();
    expect(mockStopTrack).toHaveBeenCalled();

    unmount();
  });

  it("should mark unsupported if getUserMedia rejects or throws error", async () => {
    mockGetUserMedia.mockRejectedValueOnce(new Error("Permission denied"));
    const { result, unmount } = renderHook(() => useVoiceRecorder());

    await act(async () => {
      await result.current.start();
    });

    expect(result.current.recording).toBeFalse();
    expect(result.current.unsupported).toBeTrue();

    unmount();
  });
});
