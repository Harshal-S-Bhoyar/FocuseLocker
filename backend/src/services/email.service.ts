import { BrevoClient } from "@getbrevo/brevo";
import { config } from "../config/unifiedConfig";

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
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
      console.error("Failed to send verification OTP via Brevo");
      return false;
    }
  }

  async sendUnlockOtp(
    to: string,
    otp: string,
    timeRemaining?: string | number,
    blockedCategories?: string[]
  ): Promise<boolean> {
    try {
      // ponytail: escape user-derived values to prevent HTML injection while preserving content
      const safeTimeRemaining = timeRemaining !== undefined ? escapeHtml(String(timeRemaining)) : "";
      const safeCategories = blockedCategories && blockedCategories.length > 0
        ? blockedCategories.map((c) => escapeHtml(String(c))).join(", ")
        : "";

      const details = [
        safeTimeRemaining ? `<p>Time remaining: <strong>${safeTimeRemaining}</strong></p>` : "",
        safeCategories ? `<p>Blocked categories: <strong>${safeCategories}</strong></p>` : "",
      ].filter(Boolean).join("");

      await this.client.transactionalEmails.sendTransacEmail({
        subject: "FocusLock: Unlock Request",
        htmlContent: ` 
          <p>A user is requesting an early unlock from FocusLock.</p>
          <p>OTP to unlock: <strong>${otp}</strong>.</p>
          ${details}
          <p>Only share this code if you have verified their reason for ending the session early.</p>
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
