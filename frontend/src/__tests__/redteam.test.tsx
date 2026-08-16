import { describe, expect, it, mock } from "bun:test";
import { listRedTeamRuns, runRedTeamEvaluation } from "../lib/redteam-api";

describe("redteam-api client tests", () => {
  it("runRedTeamEvaluation sends POST request and returns evaluation report", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            runId: "rt_123",
            userId: "user-1",
            totalTests: 10,
            blockedCount: 9,
            bypassCount: 1,
            hitRatePct: 90.0,
            categoryBreakdown: {
              DAN_VARIANT: {
                total: 2,
                blocked: 2,
                bypassed: 0,
                blockRatePct: 100.0,
              },
            },
            testResults: [],
            createdAt: 1000,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const report = await runRedTeamEvaluation(5);
    expect(report.runId).toBe("rt_123");
    expect(report.hitRatePct).toBe(90.0);
    expect(report.categoryBreakdown.DAN_VARIANT.blockRatePct).toBe(100.0);

    globalThis.fetch = originalFetch;
  });

  it("listRedTeamRuns returns history items", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = mock(() =>
      Promise.resolve(
        new Response(
          JSON.stringify([
            {
              id: "rt_run_1",
              userId: "user-1",
              totalTests: 10,
              blockedCount: 9,
              bypassCount: 1,
              hitRatePct: 90.0,
              detailsJson: "{}",
              createdAt: 1000,
            },
          ]),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
      ),
    ) as unknown as typeof fetch;

    const runs = await listRedTeamRuns(10);
    expect(runs).toHaveLength(1);
    expect(runs[0].hitRatePct).toBe(90.0);

    globalThis.fetch = originalFetch;
  });
});
