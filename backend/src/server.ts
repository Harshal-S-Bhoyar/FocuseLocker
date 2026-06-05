import app from "./app";
import dotenv from "dotenv";

dotenv.config();

const PORT = process.env.PORT || 3000;

app.listen(PORT as number, "0.0.0.0", () => {
  console.log(`Local development server listening on port ${PORT} at 0.0.0.0`);
});
