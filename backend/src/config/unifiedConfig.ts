import dotenv from "dotenv";
import { z } from "zod";

dotenv.config();

const envSchema = z.object({
  BREVO_API_KEY: z.string().min(1, "BREVO_API_KEY is required"),
  BREVO_SENDER_EMAIL: z.string().email().default("noreply@focuslock.app"),
  BREVO_SENDER_NAME: z.string().default("FocusLock"),
  OTP_EXPIRY_MINUTES: z.coerce.number().default(10),
  OTP_MAX_ATTEMPTS: z.coerce.number().default(3),
  PORT: z.coerce.number().default(3000),
});

const envVars = envSchema.parse(process.env);

export const config = {
  brevo: {
    apiKey: envVars.BREVO_API_KEY,
    senderEmail: envVars.BREVO_SENDER_EMAIL,
    senderName: envVars.BREVO_SENDER_NAME,
  },
  otp: {
    expiryMinutes: envVars.OTP_EXPIRY_MINUTES,
    maxAttempts: envVars.OTP_MAX_ATTEMPTS,
  },
  server: {
    port: envVars.PORT,
  },
};
