import { OtpStore, otpStore } from "../src/store/otp.store";

describe("OtpStore", () => {
  let store: OtpStore;

  beforeEach(() => {
    store = new OtpStore();
  });

  afterEach(() => {
    store.destroy();
  });

  afterAll(() => {
    otpStore.destroy();
  });

  it("should store and retrieve an entry", () => {
    const entry = {
      otpHash: "hash",
      expiresAt: Date.now() + 10000,
      type: "CONTACT_VERIFY" as const,
      attempts: 0,
    };
    store.set("+1234567890", entry);
    expect(store.get("+1234567890")).toEqual(entry);
  });

  it("should increment attempts", () => {
    store.set("+1234567890", {
      otpHash: "hash",
      expiresAt: Date.now() + 10000,
      type: "CONTACT_VERIFY",
      attempts: 0,
    });
    const attempts = store.incrementAttempts("+1234567890");
    expect(attempts).toBe(1);
    expect(store.get("+1234567890")?.attempts).toBe(1);
  });

  it("should delete an entry", () => {
    store.set("+1234567890", {
      otpHash: "hash",
      expiresAt: Date.now() + 10000,
      type: "CONTACT_VERIFY",
      attempts: 0,
    });
    store.delete("+1234567890");
    expect(store.get("+1234567890")).toBeUndefined();
  });

  it("should cleanup expired entries", () => {
    store.set("+111", {
      otpHash: "hash1",
      expiresAt: Date.now() - 10000,
      type: "CONTACT_VERIFY",
      attempts: 0,
    });
    store.set("+222", {
      otpHash: "hash2",
      expiresAt: Date.now() + 10000,
      type: "CONTACT_VERIFY",
      attempts: 0,
    });
    store.cleanup();
    expect(store.get("+111")).toBeUndefined();
    expect(store.get("+222")).toBeDefined();
  });

  it("should return zero when incrementing a missing entry", () => {
    expect(store.incrementAttempts("missing@example.com")).toBe(0);
  });

  it("should preserve entries when the cleanup timer is destroyed", () => {
    store.set("timer@example.com", {
      otpHash: "hash",
      expiresAt: Date.now() + 10000,
      type: "CONTACT_VERIFY",
      attempts: 0,
    });
    store.destroy();
    expect(store.get("timer@example.com")).toBeDefined();
  });
});
