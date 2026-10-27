import { describe, expect, it } from "vitest";
import { formatElapsed } from "./historyPresentation";

describe("history elapsed-time display", () => {
  it.each([
    [null, "Unknown"],
    [846_000, "14 min 6 s"],
    [3_000, "3 s"],
    [999, "999 ms"],
    [9_691_000, "2 h 41 min 31 s"],
  ])("formats %s milliseconds as %s", (milliseconds, expected) => {
    expect(formatElapsed(milliseconds)).toBe(expected);
  });
});
