import request from "supertest";
import app from "../src/app";
import { otpService } from "../src/services/otp.service";
import { smsService } from "../src/services/sms.service";

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
      const res = await request(app)
        .post("/api/request-unlock")
        .send({ trustedContactNumber: phone }); // Missing other fields

      expect(res.status).toBe(400);
    });

    it("should return 200 on success", async () => {
      (otpService.generateAndStore as jest.Mock).mockResolvedValue("654321");
      (smsService.sendUnlockOtp as jest.Mock).mockResolvedValue(true);

      const res = await request(app)
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
      expect(otpService.generateAndStore).toHaveBeenCalledWith(phone, "SESSION_UNLOCK");
      expect(smsService.sendUnlockOtp).toHaveBeenCalledWith(
        phone,
        "654321",
        {
          categories: "Social Media, Gaming, reddit",
          timeRemaining: "01:30:00",
        }
      );
    });
  });

  describe("POST /api/verify-unlock-otp", () => {
    it("should return 400 for invalid body", async () => {
      const res = await request(app)
        .post("/api/verify-unlock-otp")
        .send({ trustedContactNumber: "invalid", otp: "123456" });

      expect(res.status).toBe(400);
    });

    it("should return 200 when OTP is valid", async () => {
      (otpService.verify as jest.Mock).mockResolvedValue({ valid: true });

      const res = await request(app)
        .post("/api/verify-unlock-otp")
        .send({ trustedContactNumber: phone, otp: "654321" });

      expect(res.status).toBe(200);
      expect(res.body.valid).toBe(true);
      expect(otpService.verify).toHaveBeenCalledWith(phone, "654321", "SESSION_UNLOCK");
    });

    it("should return 400 when OTP is expired", async () => {
      (otpService.verify as jest.Mock).mockResolvedValue({ valid: false, reason: "expired" });

      const res = await request(app)
        .post("/api/verify-unlock-otp")
        .send({ trustedContactNumber: phone, otp: "654321" });

      expect(res.status).toBe(400);
      expect(res.body.valid).toBe(false);
      expect(res.body.reason).toBe("expired");
    });
  });
});
