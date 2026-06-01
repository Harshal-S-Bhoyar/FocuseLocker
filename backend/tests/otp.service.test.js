"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const otp_service_1 = require("../src/services/otp.service");
const otp_store_1 = require("../src/store/otp.store");
const bcrypt_1 = __importDefault(require("bcrypt"));
const unifiedConfig_1 = require("../src/config/unifiedConfig");
describe("OtpService", () => {
    const phone = "+19876543210";
    beforeEach(() => {
        // Clear store before each test
        otp_store_1.otpStore.cleanup();
        // For manually clearing all entries during tests:
        otp_store_1.otpStore.delete(phone);
    });
    it("should generate a 6-digit OTP and store its hash", async () => {
        const otp = await otp_service_1.otpService.generateAndStore(phone, "CONTACT_VERIFY");
        // OTP should be 6 digits
        expect(otp).toMatch(/^\d{6}$/);
        const entry = otp_store_1.otpStore.get(phone);
        expect(entry).toBeDefined();
        expect(entry?.type).toBe("CONTACT_VERIFY");
        // Store should have hash, not plain OTP
        expect(entry?.otpHash).not.toBe(otp);
        // Hash should match plain OTP
        const isValid = await bcrypt_1.default.compare(otp, entry.otpHash);
        expect(isValid).toBe(true);
    });
    it("should return true for valid verify", async () => {
        const otp = await otp_service_1.otpService.generateAndStore(phone, "CONTACT_VERIFY");
        const result = await otp_service_1.otpService.verify(phone, otp, "CONTACT_VERIFY");
        expect(result.valid).toBe(true);
        // Should be deleted after verify
        expect(otp_store_1.otpStore.get(phone)).toBeUndefined();
    });
    it("should return invalid for wrong OTP and increment attempts", async () => {
        await otp_service_1.otpService.generateAndStore(phone, "CONTACT_VERIFY");
        const result = await otp_service_1.otpService.verify(phone, "000000", "CONTACT_VERIFY"); // wrong otp
        expect(result.valid).toBe(false);
        expect(result.reason).toBe("invalid");
        const entry = otp_store_1.otpStore.get(phone);
        expect(entry?.attempts).toBe(1);
    });
    it("should fail and delete entry if max attempts exceeded", async () => {
        await otp_service_1.otpService.generateAndStore(phone, "CONTACT_VERIFY");
        unifiedConfig_1.config.otp.maxAttempts = 3;
        await otp_service_1.otpService.verify(phone, "000000", "CONTACT_VERIFY"); // 1
        await otp_service_1.otpService.verify(phone, "000000", "CONTACT_VERIFY"); // 2
        const res3 = await otp_service_1.otpService.verify(phone, "000000", "CONTACT_VERIFY"); // 3 -> reaches max
        expect(res3.valid).toBe(false);
        expect(res3.reason).toBe("max_attempts");
        // The entry should be deleted
        expect(otp_store_1.otpStore.get(phone)).toBeUndefined();
    });
    it("should fail if type mismatches", async () => {
        const otp = await otp_service_1.otpService.generateAndStore(phone, "CONTACT_VERIFY");
        const result = await otp_service_1.otpService.verify(phone, otp, "SESSION_UNLOCK");
        expect(result.valid).toBe(false);
        expect(result.reason).toBe("invalid");
    });
    it("should return not_found if no OTP exists", async () => {
        const result = await otp_service_1.otpService.verify("+10000000000", "123456", "CONTACT_VERIFY");
        expect(result.valid).toBe(false);
        expect(result.reason).toBe("not_found");
    });
});
