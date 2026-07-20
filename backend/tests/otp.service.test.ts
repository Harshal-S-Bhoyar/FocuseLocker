import { otpService } from "../src/services/otp.service";
import { otpStore } from "../src/store/otp.store";
import bcrypt from "bcrypt";
import { config } from "../src/config/unifiedConfig";

describe("OtpService", () => {
  const phone = "+19876543210";

  beforeEach(() => {
    // Clear store before each test
    otpStore.cleanup();
    // For manually clearing all entries during tests:
    otpStore.delete(phone);
  });

  afterAll(() => {
    otpStore.destroy();
  });

  it("should generate a 6-digit OTP and store its hash", async () => {
    const otp = await otpService.generateAndStore(phone, "CONTACT_VERIFY");
    
    // OTP should be 6 digits
    expect(otp).toMatch(/^\d{6}$/);

    const entry = otpStore.get(phone);
    expect(entry).toBeDefined();
    expect(entry?.type).toBe("CONTACT_VERIFY");
    
    // Store should have hash, not plain OTP
    expect(entry?.otpHash).not.toBe(otp);

    // Hash should match plain OTP
    const isValid = await bcrypt.compare(otp, entry!.otpHash);
    expect(isValid).toBe(true);
  });

  it("should not log OTPs or recipient identifiers", async () => {
    const logSpy = jest.spyOn(console, "log").mockImplementation();
    const otp = await otpService.generateAndStore(phone, "CONTACT_VERIFY");
    await otpService.verify(phone, otp, "CONTACT_VERIFY");

    const output = logSpy.mock.calls.flat().join(" ");
    expect(output).not.toContain(phone);
    expect(output).not.toContain(otp);
    logSpy.mockRestore();
  });
  it("should return true for valid verify", async () => {
    const otp = await otpService.generateAndStore(phone, "CONTACT_VERIFY");
    const result = await otpService.verify(phone, otp, "CONTACT_VERIFY");

    expect(result.valid).toBe(true);
    
    // Should be deleted after verify
    expect(otpStore.get(phone)).toBeUndefined();
  });

  it("should return invalid for wrong OTP and increment attempts", async () => {
    await otpService.generateAndStore(phone, "CONTACT_VERIFY");
    const result = await otpService.verify(phone, "000000", "CONTACT_VERIFY"); // wrong otp

    expect(result.valid).toBe(false);
    expect(result.reason).toBe("invalid");

    const entry = otpStore.get(phone);
    expect(entry?.attempts).toBe(1);
  });

  it("should fail and delete entry if max attempts exceeded", async () => {
    await otpService.generateAndStore(phone, "CONTACT_VERIFY");

    config.otp.maxAttempts = 3;

    await otpService.verify(phone, "000000", "CONTACT_VERIFY"); // 1
    await otpService.verify(phone, "000000", "CONTACT_VERIFY"); // 2
    const res3 = await otpService.verify(phone, "000000", "CONTACT_VERIFY"); // 3 -> reaches max

    expect(res3.valid).toBe(false);
    expect(res3.reason).toBe("max_attempts");
    
    // The entry should be deleted
    expect(otpStore.get(phone)).toBeUndefined();
  });

  it("should fail if type mismatches", async () => {
    const otp = await otpService.generateAndStore(phone, "CONTACT_VERIFY");
    const result = await otpService.verify(phone, otp, "SESSION_UNLOCK");

    expect(result.valid).toBe(false);
    expect(result.reason).toBe("invalid");
  });

  it("should return not_found if no OTP exists", async () => {
    const result = await otpService.verify("+10000000000", "123456", "CONTACT_VERIFY");
    expect(result.valid).toBe(false);
    expect(result.reason).toBe("not_found");
  });
});
