import { describe, expect, it, mock } from "bun:test";
import { StreamStore } from "@/hooks/useSpringAiStream";

describe("Interaction State Understanding Frontend Flow", () => {
  it("StreamStore accurately stores and notifies interaction metadata", () => {
    const store = new StreamStore();
    const listener = mock(() => {});
    const unsubscribe = store.subscribe(listener);

    expect(store.getSnapshot().interaction).toBeNull();

    const interactionMeta = {
      state: "CORRECTION_REQUIRED",
      stateLabel: "错误纠正",
      signals: ["CHALLENGES_PREVIOUS_ANSWER", "REPORTS_ERROR", "REQUESTS_CODE"],
      strategies: ["CORRECT_PREVIOUS_ANSWER", "DIRECT_ANSWER", "CODE_FIRST"],
    };

    store.updateInteraction(interactionMeta);

    expect(store.getSnapshot().interaction).toEqual(interactionMeta);
    expect(listener).toHaveBeenCalledTimes(1);

    unsubscribe();
  });
});
