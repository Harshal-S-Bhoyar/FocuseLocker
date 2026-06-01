import { Router, Request, Response } from "express";
import { validate } from "../middleware/validate";
import { requestUnlockSchema, verifyUnlockOtpSchema } from "../schemas/request.schemas";
import { otpService } from "../services/otp.service";
import { smsService } from "../services/sms.service";
import { asyncErrorWrapper } from "../utils/asyncErrorWrapper";

const router = Router();

router.post(
  "/request-unlock",
  validate(requestUnlockSchema),
  asyncErrorWrapper(async (req: Request, res: Response) => {
    const { trustedContactNumber, blockedCategories, blockedKeywords, timeRemaining } = req.body;

    const otp = await otpService.generateAndStore(trustedContactNumber, "SESSION_UNLOCK");
    
    // Format categories/keywords for SMS
    const blockedItems = [
      ...blockedCategories,
      ...blockedKeywords
    ].join(", ");

    const categoriesStr = blockedItems.length > 0 ? blockedItems : "Custom list";

    const sent = await smsService.sendUnlockOtp(trustedContactNumber, otp, {
      timeRemaining,
      categories: categoriesStr,
    });

    if (!sent) {
      res.status(500).json({ success: false, message: "Failed to send SMS" });
      return;
    }

    res.json({ success: true });
  })
);

router.post(
  "/verify-unlock-otp",
  validate(verifyUnlockOtpSchema),
  asyncErrorWrapper(async (req: Request, res: Response) => {
    const { trustedContactNumber, otp } = req.body;

    const result = await otpService.verify(trustedContactNumber, otp, "SESSION_UNLOCK");

    if (!result.valid) {
      res.status(400).json({ valid: false, reason: result.reason });
      return;
    }

    res.json({ valid: true });
  })
);

export default router;
