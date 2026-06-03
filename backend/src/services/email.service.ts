import { BrevoClient } from "@getbrevo/brevo";
import { config } from "../config/unifiedConfig";

export interface UnlockContext {
  timeRemaining: string;
  categories: string;
}

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
      console.error("Failed to send verification OTP via Brevo:", error);
      return false;
    }
  }

  async sendUnlockOtp(to: string, otp: string, context: UnlockContext): Promise<boolean> {
    try {
      await this.client.transactionalEmails.sendTransacEmail({
        subject: "FocusLock: Unlock Request",
        htmlContent: `
          <p>FocusLock: Someone wants to end their session early.</p>
          <p>Time remaining: ${context.timeRemaining}. Blocked: ${context.categories}.</p>
          <p>OTP to unlock: <strong>${otp}</strong>.</p>
          <p>Share only if you think it's justified.</p>
        `,
        sender: { name: config.brevo.senderName, email: config.brevo.senderEmail },
        to: [{ email: to }],
      });
      return true;
    } catch (error) {
      console.error("Failed to send unlock OTP via Brevo:", error);
      return false;
    }
  }
}

export const emailService = new EmailService();
