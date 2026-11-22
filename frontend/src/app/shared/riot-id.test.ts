import { describe, expect, it } from "vitest";

import { splitRiotId } from "./riot-id";

describe(splitRiotId, () => {
  it("makes everything from the first # the tag", () => {
    expect(splitRiotId("Kestrel#EUW")).toStrictEqual({
      name: "Kestrel",
      tag: "#EUW",
    });
    expect(splitRiotId("Jett Main#FR#1")).toStrictEqual({
      name: "Jett Main",
      tag: "#FR#1",
    });
  });

  it("leaves a name without a # whole", () => {
    expect(splitRiotId("Drrako")).toStrictEqual({ name: "Drrako", tag: "" });
  });
});
