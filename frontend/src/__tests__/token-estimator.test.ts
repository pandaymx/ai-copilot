import { describe, expect, it } from "bun:test";
import {
  estimatePromptCostRmb,
  estimatePromptTokens,
} from "../lib/token-estimator";

describe("token-estimator utility", () => {
  it("should return 0 for empty or whitespace text", () => {
    expect(estimatePromptTokens("")).toBe(0);
    expect(estimatePromptTokens("   \n\t  ")).toBe(0);
  });

  it("should estimate CJK characters correctly", () => {
    const text = "你好，请帮我分析这段代码并给出重构建议。";
    const tokens = estimatePromptTokens(text);
    // 20 characters -> should be around 18-22 tokens
    expect(tokens).toBeGreaterThanOrEqual(15);
    expect(tokens).toBeLessThanOrEqual(25);
  });

  it("should estimate English words and code correctly", () => {
    const text =
      "function calculateTotal(items: number[]): number { return items.reduce((a, b) => a + b, 0); }";
    const tokens = estimatePromptTokens(text);
    expect(tokens).toBeGreaterThan(15);
    expect(tokens).toBeLessThan(45);
  });

  it("should calculate prompt cost RMB accurately", () => {
    // 10,000 tokens with default 0.001 / k -> 0.01 RMB
    expect(estimatePromptCostRmb(10000, 0.001)).toBe(0.01);
    // 2,500 tokens with 0.02 / k -> 0.05 RMB
    expect(estimatePromptCostRmb(2500, 0.02)).toBe(0.05);
    // 0 tokens -> 0 RMB
    expect(estimatePromptCostRmb(0, 0.01)).toBe(0);
  });
});
