"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const supertest_1 = __importDefault(require("supertest"));
const app_1 = __importDefault(require("../src/app"));
const otp_service_1 = require("../src/services/otp.service");
const sms_service_1 = require("../src/services/sms.service");
// Mock services
jest.mock("../src/services/otp.service");
jest.mock("../src/services/sms.service");
describe("Contact Routes", () => {
    const phone = "+12345678901";
    beforeEach(() => {
        jest.clearAllMocks();
    });
    describe("POST /api/verify-contact", () => {
        it("should return 400 for invalid phone number", async () => {
            const res = await (0, supertest_1.default)(app_1.default)
                .post("/api/verify-contact")
                .send({ phoneNumber: "123" }); // Invalid E.164
            expect(res.status).toBe(400);
            expect(res.body.message).toBe("Validation failed");
        });
        it("should return 200 on success", async () => {
            otp_service_1.otpService.generateAndStore.mockResolvedValue("123456");
            sms_service_1.smsService.sendVerificationOtp.mockResolvedValue(true);
            const res = await (0, supertest_1.default)(app_1.default)
                .post("/api/verify-contact")
                .send({ phoneNumber: phone });
            expect(res.status).toBe(200);
            expect(res.body.success).toBe(true);
            expect(otp_service_1.otpService.generateAndStore).toHaveBeenCalledWith(phone, "CONTACT_VERIFY");
            expect(sms_service_1.smsService.sendVerificationOtp).toHaveBeenCalledWith(phone, "123456");
        });
        it("should return 500 if SMS fails", async () => {
            otp_service_1.otpService.generateAndStore.mockResolvedValue("123456");
            sms_service_1.smsService.sendVerificationOtp.mockResolvedValue(false); // Fails
            const res = await (0, supertest_1.default)(app_1.default)
                .post("/api/verify-contact")
                .send({ phoneNumber: phone });
            expect(res.status).toBe(500);
            expect(res.body.success).toBe(false);
        });
    });
    describe("POST /api/confirm-contact", () => {
        it("should return 400 for invalid OTP length", async () => {
            const res = await (0, supertest_1.default)(app_1.default)
                .post("/api/confirm-contact")
                .send({ phoneNumber: phone, otp: "123" });
            expect(res.status).toBe(400);
        });
        it("should return 200 when OTP is valid", async () => {
            otp_service_1.otpService.verify.mockResolvedValue({ valid: true });
            const res = await (0, supertest_1.default)(app_1.default)
                .post("/api/confirm-contact")
                .send({ phoneNumber: phone, otp: "123456" });
            expect(res.status).toBe(200);
            expect(res.body.verified).toBe(true);
            expect(otp_service_1.otpService.verify).toHaveBeenCalledWith(phone, "123456", "CONTACT_VERIFY");
        });
        it("should return 400 when OTP is invalid", async () => {
            otp_service_1.otpService.verify.mockResolvedValue({ valid: false, reason: "invalid" });
            const res = await (0, supertest_1.default)(app_1.default)
                .post("/api/confirm-contact")
                .send({ phoneNumber: phone, otp: "123456" });
            expect(res.status).toBe(400);
            expect(res.body.verified).toBe(false);
            expect(res.body.reason).toBe("invalid");
        });
    });
});
