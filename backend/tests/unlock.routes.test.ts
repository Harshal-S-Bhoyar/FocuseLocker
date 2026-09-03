import request from "supertest";
import app from "../src/app";
import { otpService } from "../src/services/otp.service";
import { emailService } from "../src/services/email.service";
import { otpStore } from "../src/store/otp.store";

// Mock services
jest.mock("../src/services/otp.service");
jest.mock("../src/services/email.service");

describe("Unlock Routes", () => {
  const email = "test@example.com";

  beforeEach(() => {
    jest.clearAllMocks();
  });

  afterAll(() => {
    otpStore.destroy();
  });

  describe("POST /api/request-unlock", () => {
    it("should return 400 for missing fields", async () => {
      const res = await request(app)
                .post("/api/request-unlock")
        .set("X-Forwarded-For", "12.0.0.1")
        .send({}); // ponytail: minimal strict invalid request test

      expect(res.status).toBe(400);
    });

    it("should return 200 on success", async () => {
      (otpService.generateAndStore as jest.Mock).mockResolvedValue("654321");
      (emailService.sendUnlockOtp as jest.Mock).mockResolvedValue(true);

      const res = await request(app)
        .post("/api/request-unlock")
        .set("X-Forwarded-For", "12.0.0.2")
        .send({
          trustedContactEmail: email,
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(otpService.generateAndStore).toHaveBeenCalledWith(email, "SESSION_UNLOCK");
      expect(emailService.sendUnlockOtp).toHaveBeenCalledWith(email, "654321");
    });

    it("should forward timeRemaining and blockedCategories to emailService", async () => {
      (otpService.generateAndStore as jest.Mock).mockResolvedValue("654321");
      (emailService.sendUnlockOtp as jest.Mock).mockResolvedValue(true);

      const contextEmail = "context@example.com";
      const res = await request(app)
        .post("/api/request-unlock")
        .set("X-Forwarded-For", "12.0.0.9")
        .send({
          trustedContactEmail: contextEmail,
          timeRemaining: "15 minutes",
          blockedCategories: ["ADULT", "GAMBLING"],
        });

      expect(res.status).toBe(200);
      expect(emailService.sendUnlockOtp).toHaveBeenCalledWith(
        contextEmail,
        "654321",
        "15 minutes",
        ["ADULT", "GAMBLING"]
      );
    });

    it("should return 400 for a malformed trusted-contact email", async () => {
      const res = await request(app)
        .post("/api/request-unlock")
        .set("X-Forwarded-For", "12.0.0.3")
        .send({ trustedContactEmail: "not-an-email" });

      expect(res.status).toBe(400);
      expect(otpService.generateAndStore).not.toHaveBeenCalled();
      expect(emailService.sendUnlockOtp).not.toHaveBeenCalled();
    });

    it("should return 500 when unlock OTP delivery fails", async () => {
      (otpService.generateAndStore as jest.Mock).mockResolvedValue("654321");
      (emailService.sendUnlockOtp as jest.Mock).mockResolvedValue(false);

      const res = await request(app)
        .post("/api/request-unlock")
        .set("X-Forwarded-For", "12.0.0.4")
        .send({ trustedContactEmail: email });

      expect(res.status).toBe(500);
      expect(res.body.success).toBe(false);
      expect(res.body.message).toBe("Failed to send Email");
    });
  });

  describe("POST /api/verify-unlock-otp", () => {
    it("should return 400 for invalid body", async () => {
      const res = await request(app)
        .post("/api/verify-unlock-otp")
        .set("X-Forwarded-For", "12.0.0.5")
        .send({ trustedContactEmail: "invalid", otp: "123456" });

      expect(res.status).toBe(400);
    });

    it("should return 200 when OTP is valid", async () => {
      (otpService.verify as jest.Mock).mockResolvedValue({ valid: true });

      const res = await request(app)
        .post("/api/verify-unlock-otp")
        .send({ trustedContactEmail: email, otp: "654321" });

      expect(res.status).toBe(200);
      expect(res.body.valid).toBe(true);
      expect(otpService.verify).toHaveBeenCalledWith(email, "654321", "SESSION_UNLOCK");
    });

    it("should return 400 when OTP is expired", async () => {
      (otpService.verify as jest.Mock).mockResolvedValue({ valid: false, reason: "expired" });

      const res = await request(app)
        .post("/api/verify-unlock-otp")
        .set("X-Forwarded-For", "12.0.0.6")
        .send({ trustedContactEmail: email, otp: "654321" });

      expect(res.status).toBe(400);
      expect(res.body.valid).toBe(false);
      expect(res.body.reason).toBe("expired");
    });

    it("should return 400 when OTP is invalid", async () => {
      (otpService.verify as jest.Mock).mockResolvedValue({ valid: false, reason: "invalid" });

      const res = await request(app)
        .post("/api/verify-unlock-otp")
        .set("X-Forwarded-For", "12.0.0.7")
        .send({ trustedContactEmail: email, otp: "000000" });

      expect(res.status).toBe(400);
      expect(res.body.valid).toBe(false);
      expect(res.body.reason).toBe("invalid");
    });
  });
});
