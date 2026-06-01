import app from "./app";
import { config } from "./config/unifiedConfig";

// Validate required environment variables
const requiredEnvVars = [
  "TWILIO_ACCOUNT_SID",
  "TWILIO_AUTH_TOKEN",
  "TWILIO_PHONE_NUMBER",
];

for (const envVar of requiredEnvVars) {
  if (!process.env[envVar]) {
    console.error(`ERROR: Missing required environment variable ${envVar}`);
    process.exit(1);
  }
}

const PORT = config.server.port;

const server = app.listen(PORT, () => {
  console.log(`FocusLock Backend listening on port ${PORT}`);
});

// Graceful shutdown handling
process.on("SIGINT", () => {
  console.log("Shutting down gracefully...");
  server.close(() => {
    console.log("Closed out remaining connections");
    process.exit(0);
  });
});
