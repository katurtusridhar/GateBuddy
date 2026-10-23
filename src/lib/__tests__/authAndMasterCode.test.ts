import { describe, it, expect } from "vitest";
import { DEFAULT_CREDENTIALS } from "../types";

describe("User Authentication & Master Security Code", () => {
  it("has default credentials pre-configured for katurtusridhar", () => {
    expect(DEFAULT_CREDENTIALS.username).toBe("katurtusridhar");
    expect(DEFAULT_CREDENTIALS.password).toBe("Sridhar@758");
    expect(DEFAULT_CREDENTIALS.masterCode).toBe("oA-THL,UXyh6");
  });

  it("verifies master code matches exactly to authorize changes", () => {
    const validMasterCode = "oA-THL,UXyh6";
    const invalidMasterCode = "wrongCode123";

    expect(validMasterCode === DEFAULT_CREDENTIALS.masterCode).toBe(true);
    expect(invalidMasterCode === DEFAULT_CREDENTIALS.masterCode).toBe(false);
  });
});
