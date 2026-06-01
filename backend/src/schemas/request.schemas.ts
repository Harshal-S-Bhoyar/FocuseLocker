import { z } from "zod";

const e164PhoneRegex = /^\+[1-9]\d{1,14}$/;
const otpRegex = /^\d{6}$/;

export const verifyContactSchema = z.object({
  body: z.object({
    phoneNumber: z.string().regex(e164PhoneRegex, "Invalid E.164 phone number"),
  }),
});

export const confirmContactSchema = z.object({
  body: z.object({
    phoneNumber: z.string().regex(e164PhoneRegex, "Invalid E.164 phone number"),
    otp: z.string().regex(otpRegex, "OTP must be exactly 6 digits"),
  }),
});

export const requestUnlockSchema = z.object({
  body: z.object({
    trustedContactNumber: z.string().regex(e164PhoneRegex, "Invalid E.164 phone number"),
    blockedCategories: z.array(z.string()),
    blockedKeywords: z.array(z.string()),
    timeRemaining: z.string(),
    requestTimestamp: z.number().int().positive(),
  }),
});

export const verifyUnlockOtpSchema = z.object({
  body: z.object({
    trustedContactNumber: z.string().regex(e164PhoneRegex, "Invalid E.164 phone number"),
    otp: z.string().regex(otpRegex, "OTP must be exactly 6 digits"),
  }),
});
