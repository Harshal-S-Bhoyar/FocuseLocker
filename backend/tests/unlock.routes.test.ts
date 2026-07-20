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
        .send({}); // ponytail: minimal strict invalid request test

      expect(res.status).toBe(400);
    });

    it("should return 200 on success", async () => {
      (otpService.generateAndStore as jest.Mock).mockResolvedValue("654321");
      (emailService.sendUnlockOtp as jest.Mock).mockResolvedValue(true);

      const res = await request(app)
        .post("/api/request-unlock")
        .send({
          trustedContactEmail: email,
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(otpService.generateAndStore).toHaveBeenCalledWith(email, "SESSION_UNLOCK");
      expect(emailService.sendUnlockOtp).toHaveBeenCalledWith(email, "654321");
    });
  });

  describe("POST /api/verify-unlock-otp", () => {
    it("should return 400 for invalid body", async () => {
      const res = await request(app)
        .post("/api/verify-unlock-otp")
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
        .send({ trustedContactEmail: email, otp: "654321" });

      expect(res.status).toBe(400);
      expect(res.body.valid).toBe(false);
      expect(res.body.reason).toBe("expired");
    });
  });
});
