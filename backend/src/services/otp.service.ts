import crypto from "crypto";
import bcrypt from "bcrypt";
import { otpStore, OtpEntry } from "../store/otp.store";
import { config } from "../config/unifiedConfig";

export type OtpType = "CONTACT_VERIFY" | "SESSION_UNLOCK";

export class OtpService {
  private readonly saltRounds = 10;

  async generateAndStore(phone: string, type: OtpType): Promise<string> {
    // Generate secure 6-digit OTP
    const plainOtp = crypto.randomInt(100000, 999999).toString();
    
    // Hash OTP using bcrypt
    const otpHash = await bcrypt.hash(plainOtp, this.saltRounds);

    // Calculate expiry
    const expiresAt = Date.now() + config.otp.expiryMinutes * 60 * 1000;

    const entry: OtpEntry = {
      otpHash,
      expiresAt,
      type,
      attempts: 0,
    };

    otpStore.set(phone, entry);

    // Return the plain OTP so it can be sent via SMS
    return plainOtp;
  }

  async verify(
    phone: string,
    plainOtp: string,
    expectedType: OtpType
  ): Promise<{ valid: boolean; reason?: string }> {
    const entry = otpStore.get(phone);

    if (!entry) {
      return { valid: false, reason: "not_found" };
    }

    if (entry.type !== expectedType) {
      return { valid: false, reason: "invalid" };
    }

    if (Date.now() > entry.expiresAt) {
      otpStore.delete(phone);
      return { valid: false, reason: "expired" };
    }

    const attempts = otpStore.incrementAttempts(phone);
    if (attempts > config.otp.maxAttempts) {
      otpStore.delete(phone);
      return { valid: false, reason: "max_attempts" };
    }

    const isValid = await bcrypt.compare(plainOtp, entry.otpHash);
    
    if (isValid) {
      // Delete entry after successful verification
      otpStore.delete(phone);
      return { valid: true };
    }

    if (attempts === config.otp.maxAttempts) {
      // Reached max attempts on this check, delete it immediately
      otpStore.delete(phone);
      return { valid: false, reason: "max_attempts" };
    }

    return { valid: false, reason: "invalid" };
  }
}

export const otpService = new OtpService();
