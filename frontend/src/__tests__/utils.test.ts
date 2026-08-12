import { describe, expect, it } from "bun:test";
import { cn } from "../lib/utils";

describe("Classname Utility - lib/utils.ts", () => {
  it("should combine multiple class string arguments", () => {
    expect(cn("bg-red-500", "text-white", "p-4")).toBe(
      "bg-red-500 text-white p-4",
    );
  });

  it("should handle conditional class names via clsx syntax", () => {
    const isTrue = true;
    const isFalse = false;
    expect(
      cn(
        "base-class",
        isTrue && "active-class",
        isFalse && "hidden-class",
        null,
        undefined,
      ),
    ).toBe("base-class active-class");
  });

  it("should resolve conflicting Tailwind CSS classes with tailwind-merge", () => {
    expect(cn("px-2", "px-4", "p-6")).toBe("p-6");
    expect(cn("text-red-500", "text-blue-500")).toBe("text-blue-500");
  });

  it("should support array and object class inputs", () => {
    expect(
      cn(["flex", "items-center"], {
        "justify-center": true,
        "justify-between": false,
      }),
    ).toBe("flex items-center justify-center");
  });
});
