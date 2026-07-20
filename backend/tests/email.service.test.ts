import { EmailService } from "../src/services/email.service";

describe("EmailService", () => {
  const email = "trusted@example.com";
  const otp = "123456";

  beforeEach(() => {
    jest.clearAllMocks();
  });

  function serviceWithSender(sender: jest.Mock): EmailService {
    const service = new EmailService();
    (service as unknown as { client: { transactionalEmails: { sendTransacEmail: jest.Mock } } }).client = {
      transactionalEmails: { sendTransacEmail: sender },
    };
    return service;
  }

  it("should not log OTPs or recipient identifiers on successful sends", async () => {
    const sender = jest.fn().mockResolvedValue({});
    const logSpy = jest.spyOn(console, "log").mockImplementation();

    const service = serviceWithSender(sender);
    await service.sendVerificationOtp(email, otp);
    await service.sendUnlockOtp(email, otp);

    const output = logSpy.mock.calls.flat().join(" ");
    expect(output).not.toContain(email);
    expect(output).not.toContain(otp);
    logSpy.mockRestore();
  });

  it("should not include OTPs or recipient identifiers in error logs", async () => {
    const sender = jest.fn().mockRejectedValue(new Error(`failed for ${email} with ${otp}`));
    const errorSpy = jest.spyOn(console, "error").mockImplementation();

    const service = serviceWithSender(sender);
    await service.sendVerificationOtp(email, otp);
    await service.sendUnlockOtp(email, otp);

    const output = errorSpy.mock.calls.flat().join(" ");
    expect(output).not.toContain(email);
    expect(output).not.toContain(otp);
    errorSpy.mockRestore();
  });
  it("should send only the unlock OTP without blocked context", async () => {
    const sender = jest.fn().mockResolvedValue({});
    const service = serviceWithSender(sender);

    await service.sendUnlockOtp(email, otp);

    const payload = sender.mock.calls[0][0];
    expect(payload.htmlContent).toContain(otp);
    expect(payload.htmlContent).not.toContain("Social Media");
    expect(payload.htmlContent).not.toContain("reddit");
    expect(payload.htmlContent).not.toContain("01:00:00");
    expect(payload.htmlContent).not.toContain("Blocked");
    expect(payload.htmlContent).not.toContain("Time remaining");
  });
});