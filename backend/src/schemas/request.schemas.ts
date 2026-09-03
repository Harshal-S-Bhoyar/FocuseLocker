import { z } from "zod";

const otpRegex = /^\d{6}$/;

export const verifyContactSchema = z.object({
  body: z.object({
    email: z.string().email("Invalid email address"),
  }),
});

export const confirmContactSchema = z.object({
  body: z.object({
    email: z.string().email("Invalid email address"),
    otp: z.string().regex(otpRegex, "OTP must be exactly 6 digits"),
  }),
});

export const requestUnlockSchema = z.object({
  body: z.object({
    trustedContactEmail: z.string().email("Invalid email address"),
    timeRemaining: z.union([z.string(), z.number()]).optional(),
    blockedCategories: z.array(z.string()).optional(),
  }),
});

export const verifyUnlockOtpSchema = z.object({
  body: z.object({
    trustedContactEmail: z.string().email("Invalid email address"),
    otp: z.string().regex(otpRegex, "OTP must be exactly 6 digits"),
  }),
});
