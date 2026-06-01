import request from "supertest";
import app from "../src/app";
import { otpService } from "../src/services/otp.service";
import { smsService } from "../src/services/sms.service";

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
      const res = await request(app)
        .post("/api/verify-contact")
        .send({ phoneNumber: "123" }); // Invalid E.164

      expect(res.status).toBe(400);
      expect(res.body.message).toBe("Validation failed");
    });

    it("should return 200 on success", async () => {
      (otpService.generateAndStore as jest.Mock).mockResolvedValue("123456");
      (smsService.sendVerificationOtp as jest.Mock).mockResolvedValue(true);

      const res = await request(app)
        .post("/api/verify-contact")
        .send({ phoneNumber: phone });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(otpService.generateAndStore).toHaveBeenCalledWith(phone, "CONTACT_VERIFY");
      expect(smsService.sendVerificationOtp).toHaveBeenCalledWith(phone, "123456");
    });

    it("should return 500 if SMS fails", async () => {
      (otpService.generateAndStore as jest.Mock).mockResolvedValue("123456");
      (smsService.sendVerificationOtp as jest.Mock).mockResolvedValue(false); // Fails

      const res = await request(app)
        .post("/api/verify-contact")
        .send({ phoneNumber: phone });

      expect(res.status).toBe(500);
      expect(res.body.success).toBe(false);
    });
  });

  describe("POST /api/confirm-contact", () => {
    it("should return 400 for invalid OTP length", async () => {
      const res = await request(app)
        .post("/api/confirm-contact")
        .send({ phoneNumber: phone, otp: "123" });

      expect(res.status).toBe(400);
    });

    it("should return 200 when OTP is valid", async () => {
      (otpService.verify as jest.Mock).mockResolvedValue({ valid: true });

      const res = await request(app)
        .post("/api/confirm-contact")
        .send({ phoneNumber: phone, otp: "123456" });

      expect(res.status).toBe(200);
      expect(res.body.verified).toBe(true);
      expect(otpService.verify).toHaveBeenCalledWith(phone, "123456", "CONTACT_VERIFY");
    });

    it("should return 400 when OTP is invalid", async () => {
      (otpService.verify as jest.Mock).mockResolvedValue({ valid: false, reason: "invalid" });

      const res = await request(app)
        .post("/api/confirm-contact")
        .send({ phoneNumber: phone, otp: "123456" });

      expect(res.status).toBe(400);
      expect(res.body.verified).toBe(false);
      expect(res.body.reason).toBe("invalid");
    });
  });
});
