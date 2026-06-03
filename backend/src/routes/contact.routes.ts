import { Router, Request, Response } from "express";
import { validate } from "../middleware/validate";
import { verifyContactSchema, confirmContactSchema } from "../schemas/request.schemas";
import { otpService } from "../services/otp.service";
import { emailService } from "../services/email.service";
import { asyncErrorWrapper } from "../utils/asyncErrorWrapper";

const router = Router();

router.post(
  "/verify-contact",
  validate(verifyContactSchema),
  asyncErrorWrapper(async (req: Request, res: Response) => {
    const { email } = req.body;

    const otp = await otpService.generateAndStore(email, "CONTACT_VERIFY");
    const sent = await emailService.sendVerificationOtp(email, otp);

    if (!sent) {
      res.status(500).json({ success: false, message: "Failed to send email" });
      return;
    }

    res.json({ success: true });
  })
);

router.post(
  "/confirm-contact",
  validate(confirmContactSchema),
  asyncErrorWrapper(async (req: Request, res: Response) => {
    const { email, otp } = req.body;

    const result = await otpService.verify(email, otp, "CONTACT_VERIFY");

    if (!result.valid) {
      res.status(400).json({ verified: false, reason: result.reason });
      return;
    }

    res.json({ verified: true });
  })
);

export default router;
