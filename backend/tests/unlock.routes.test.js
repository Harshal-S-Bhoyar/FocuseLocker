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
describe("Unlock Routes", () => {
    const phone = "+19876543210";
    beforeEach(() => {
        jest.clearAllMocks();
    });
    describe("POST /api/request-unlock", () => {
        it("should return 400 for missing fields", async () => {
            const res = await (0, supertest_1.default)(app_1.default)
                .post("/api/request-unlock")
                .send({ trustedContactNumber: phone }); // Missing other fields
            expect(res.status).toBe(400);
        });
        it("should return 200 on success", async () => {
            otp_service_1.otpService.generateAndStore.mockResolvedValue("654321");
            sms_service_1.smsService.sendUnlockOtp.mockResolvedValue(true);
            const res = await (0, supertest_1.default)(app_1.default)
                .post("/api/request-unlock")
                .send({
                trustedContactNumber: phone,
                blockedCategories: ["Social Media", "Gaming"],
                blockedKeywords: ["reddit"],
                timeRemaining: "01:30:00",
                requestTimestamp: Date.now(),
            });
            expect(res.status).toBe(200);
            expect(res.body.success).toBe(true);
            expect(otp_service_1.otpService.generateAndStore).toHaveBeenCalledWith(phone, "SESSION_UNLOCK");
            expect(sms_service_1.smsService.sendUnlockOtp).toHaveBeenCalledWith(phone, "654321", {
                categories: "Social Media, Gaming, reddit",
                timeRemaining: "01:30:00",
            });
        });
    });
    describe("POST /api/verify-unlock-otp", () => {
        it("should return 400 for invalid body", async () => {
            const res = await (0, supertest_1.default)(app_1.default)
                .post("/api/verify-unlock-otp")
                .send({ trustedContactNumber: "invalid", otp: "123456" });
            expect(res.status).toBe(400);
        });
        it("should return 200 when OTP is valid", async () => {
            otp_service_1.otpService.verify.mockResolvedValue({ valid: true });
            const res = await (0, supertest_1.default)(app_1.default)
                .post("/api/verify-unlock-otp")
                .send({ trustedContactNumber: phone, otp: "654321" });
            expect(res.status).toBe(200);
            expect(res.body.valid).toBe(true);
            expect(otp_service_1.otpService.verify).toHaveBeenCalledWith(phone, "654321", "SESSION_UNLOCK");
        });
        it("should return 400 when OTP is expired", async () => {
            otp_service_1.otpService.verify.mockResolvedValue({ valid: false, reason: "expired" });
            const res = await (0, supertest_1.default)(app_1.default)
                .post("/api/verify-unlock-otp")
                .send({ trustedContactNumber: phone, otp: "654321" });
            expect(res.status).toBe(400);
            expect(res.body.valid).toBe(false);
            expect(res.body.reason).toBe("expired");
        });
    });
});
