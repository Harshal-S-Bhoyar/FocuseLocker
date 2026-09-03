import request from "supertest";
import app from "../src/app";
import { otpService } from "../src/services/otp.service";
import { emailService } from "../src/services/email.service";
import { otpStore } from "../src/store/otp.store";

jest.mock("../src/services/otp.service");
jest.mock("../src/services/email.service");

describe("Rate Limiting", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  afterAll(() => {
    otpStore.destroy();
  });

  it("should rate limit by IP after 5 requests", async () => {
    (otpService.generateAndStore as jest.Mock).mockResolvedValue("123456");
    (emailService.sendVerificationOtp as jest.Mock).mockResolvedValue(true);

    for (let i = 0; i < 5; i++) {
      const res = await request(app)
        .post("/api/verify-contact")
        .set("X-Forwarded-For", "1.2.3.4")
        .send({ email: `test${i}@example.com` }); // different emails, same IP
      expect(res.status).not.toBe(429);
    }

    const res = await request(app)
      .post("/api/verify-contact")
      .set("X-Forwarded-For", "1.2.3.4")
      .send({ email: "test6@example.com" });
    
    expect(res.status).toBe(429); // IP blocked
  });

  it("should rate limit by Email after 5 requests", async () => {
    (otpService.generateAndStore as jest.Mock).mockResolvedValue("123456");
    (emailService.sendVerificationOtp as jest.Mock).mockResolvedValue(true);

    for (let i = 0; i < 5; i++) {
      const res = await request(app)
        .post("/api/verify-contact")
        .set("X-Forwarded-For", `10.0.0.${i}`) // different IPs
        .send({ email: "target@example.com" }); // same email
      expect(res.status).not.toBe(429);
    }

    const res = await request(app)
      .post("/api/verify-contact")
      .set("X-Forwarded-For", "10.0.0.6")
      .send({ email: "target@example.com" });
    
    expect(res.status).toBe(429); // Email blocked
  });

  it("should allow a different IP after one IP is blocked", async () => {
    (otpService.generateAndStore as jest.Mock).mockResolvedValue("123456");
    (emailService.sendVerificationOtp as jest.Mock).mockResolvedValue(true);
    for (let i = 0; i < 5; i++) {
      await request(app).post("/api/verify-contact").set("X-Forwarded-For", "2.2.2.2").send({ email: `ip${i}@example.com` });
    }
    const blocked = await request(app).post("/api/verify-contact").set("X-Forwarded-For", "2.2.2.2").send({ email: "ip-blocked@example.com" });
    const allowed = await request(app).post("/api/verify-contact").set("X-Forwarded-For", "2.2.2.3").send({ email: "ip0@example.com" });
    expect(blocked.status).toBe(429);
    expect(allowed.status).not.toBe(429);
  });

  it("should allow a different email after one email is blocked", async () => {
    (otpService.generateAndStore as jest.Mock).mockResolvedValue("123456");
    (emailService.sendVerificationOtp as jest.Mock).mockResolvedValue(true);
    for (let i = 0; i < 5; i++) {
      await request(app).post("/api/verify-contact").set("X-Forwarded-For", `3.3.3.${i}`).send({ email: "blocked@example.com" });
    }
    const blocked = await request(app).post("/api/verify-contact").set("X-Forwarded-For", "3.3.3.6").send({ email: "blocked@example.com" });
    const allowed = await request(app).post("/api/verify-contact").set("X-Forwarded-For", "3.3.3.0").send({ email: "other@example.com" });
    expect(blocked.status).toBe(429);
    expect(allowed.status).not.toBe(429);
  });

  it("should reject malformed input before calling the OTP service", async () => {
    const res = await request(app)
      .post("/api/verify-contact")
      .set("X-Forwarded-For", "4.4.4.4")
      .send({ email: "not-an-email" });
    expect(res.status).toBe(400);
    expect(otpService.generateAndStore).not.toHaveBeenCalled();
  });

  it("should return a server error when email delivery fails", async () => {
    (otpService.generateAndStore as jest.Mock).mockResolvedValue("123456");
    (emailService.sendVerificationOtp as jest.Mock).mockResolvedValue(false);
    const res = await request(app)
      .post("/api/verify-contact")
      .set("X-Forwarded-For", "5.5.5.5")
      .send({ email: "delivery-failure@example.com" });
    expect(res.status).toBe(500);
    expect(res.body.success).toBe(false);
  });

  it("should apply the same strict IP limit to unlock requests", async () => {
    (otpService.generateAndStore as jest.Mock).mockResolvedValue("123456");
    (emailService.sendUnlockOtp as jest.Mock).mockResolvedValue(true);
    for (let i = 0; i < 5; i++) {
      const res = await request(app)
        .post("/api/request-unlock")
        .set("X-Forwarded-For", "6.6.6.6")
        .send({ trustedContactEmail: `unlock${i}@example.com` });
      expect(res.status).not.toBe(429);
    }
    const res = await request(app)
      .post("/api/request-unlock")
      .set("X-Forwarded-For", "6.6.6.6")
      .send({ trustedContactEmail: "unlock6@example.com" });
    expect(res.status).toBe(429);
  });
});
