import twilio from "twilio";
import { config } from "../config/unifiedConfig";

export interface UnlockContext {
  timeRemaining: string;
  categories: string;
}

export class SmsService {
  private client: twilio.Twilio;

  constructor() {
    this.client = twilio(config.twilio.accountSid, config.twilio.authToken);
  }

  async sendVerificationOtp(to: string, otp: string): Promise<boolean> {
    try {
      const message = `FocusLock: Your verification code is ${otp}. Do not share this with anyone.`;
      
      await this.client.messages.create({
        body: message,
        from: config.twilio.phoneNumber,
        to,
      });
      return true;
    } catch (error) {
      console.error("Failed to send verification OTP via Twilio:", error);
      return false;
    }
  }

  async sendUnlockOtp(to: string, otp: string, context: UnlockContext): Promise<boolean> {
    try {
      const message = `FocusLock: Someone wants to end their session early.\nTime remaining: ${context.timeRemaining}. Blocked: ${context.categories}.\nOTP to unlock: ${otp}. Share only if you think it's justified.`;
      
      await this.client.messages.create({
        body: message,
        from: config.twilio.phoneNumber,
        to,
      });
      return true;
    } catch (error) {
      console.error("Failed to send unlock OTP via Twilio:", error);
      return false;
    }
  }
}

export const smsService = new SmsService();
