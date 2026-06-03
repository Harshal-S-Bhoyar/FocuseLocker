import * as functions from "firebase-functions/v1";
import app from "./app";

// Export the Express app as a Firebase Cloud Function in the asia-south1 region
export const api = functions.region("asia-south1").https.onRequest(app);
