import express, { Request, Response, NextFunction } from "express";
import helmet from "helmet";
import cors from "cors";
import contactRoutes from "./routes/contact.routes";
import unlockRoutes from "./routes/unlock.routes";

const app = express();

// Security headers
app.use(helmet());

// CORS config (allow any for the mobile app, or restrict if using web)
app.use(cors());

// Body parser
app.use(express.json());

// Health check
app.get("/health", (req: Request, res: Response) => {
  res.json({ status: "ok" });
});

// Mount routes
app.use("/api", contactRoutes);
app.use("/api", unlockRoutes);

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
