import { BrevoClient } from "@getbrevo/brevo";
import { config } from "../config/unifiedConfig";

export class EmailService {
  private client: BrevoClient;

  constructor() {
    this.client = new BrevoClient({ apiKey: config.brevo.apiKey });
  }

  async sendVerificationOtp(to: string, otp: string): Promise<boolean> {
    try {
      await this.client.transactionalEmails.sendTransacEmail({
        subject: "FocusLock: Your verification code",
        htmlContent: `<p>FocusLock: Your verification code is <strong>${otp}</strong>. Do not share this with anyone.</p>`,
        sender: { name: config.brevo.senderName, email: config.brevo.senderEmail },
        to: [{ email: to }],
      });
      return true;
    } catch (error) {
      console.error("Failed to send verification OTP via Brevo");
      return false;
    }
  }

  async sendUnlockOtp(to: string, otp: string): Promise<boolean> {
    try {
      await this.client.transactionalEmails.sendTransacEmail({
        subject: "FocusLock: Unlock Request",
        htmlContent: ` 
          <p>OTP to unlock: <strong>${otp}</strong>.</p>
        `,
        sender: { name: config.brevo.senderName, email: config.brevo.senderEmail },
        to: [{ email: to }],
      });
      return true;
    } catch (error) {
      console.error("Failed to send unlock OTP via Brevo");
      return false;
    }
  }
}

export const emailService = new EmailService();
