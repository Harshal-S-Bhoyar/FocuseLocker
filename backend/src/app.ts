import express, { Request, Response, NextFunction } from "express";
import helmet from "helmet";
import cors from "cors";
import rateLimit from "express-rate-limit";
import contactRoutes from "./routes/contact.routes";
import unlockRoutes from "./routes/unlock.routes";

const app = express();

// Security headers
app.use(helmet());

// CORS config (allow any for the mobile app, or restrict if using web)
app.use(cors({ origin: false }));

// Body parser
app.use(express.json());

// Rate limiters
const generalLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  limit: 100, // Limit each IP to 100 requests per window
  standardHeaders: true,
  legacyHeaders: false,
});

const otpLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  limit: 5, // Limit each IP to 5 requests per window
  standardHeaders: true,
  legacyHeaders: false,
});

// Apply general rate limiting
app.use("/api", generalLimiter);

// Health check
app.get("/health", (req: Request, res: Response) => {
  res.json({ status: "ok" });
});

// Mount routes
app.use("/api", contactRoutes);
app.use("/api", unlockRoutes);

// Apply strict rate limit on OTP endpoints (overrides general limiter for these routes)
app.use("/api/contact/verify", otpLimiter);
app.use("/api/unlock/request", otpLimiter);

// Global error handler
app.use((err: Error, req: Request, res: Response, next: NextFunction) => {
  console.error("Unhandled error:", err);
  
  res.status(500).json({
    message: "Internal server error",
    // Do not leak stack traces in production
    ...(process.env.NODE_ENV !== "production" ? { error: err.message } : {}),
  });
});

export default app;
