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
});
