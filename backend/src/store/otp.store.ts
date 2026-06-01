export interface OtpEntry {
  otpHash: string;
  expiresAt: number;
  type: "CONTACT_VERIFY" | "SESSION_UNLOCK";
  attempts: number;
}

export class OtpStore {
  private store: Map<string, OtpEntry> = new Map();
  private cleanupInterval: NodeJS.Timeout;

  constructor() {
    // Run cleanup every 60 seconds
    this.cleanupInterval = setInterval(() => this.cleanup(), 60 * 1000);
  }

  set(phone: string, entry: OtpEntry): void {
    this.store.set(phone, entry);
  }

  get(phone: string): OtpEntry | undefined {
    return this.store.get(phone);
  }

  incrementAttempts(phone: string): number {
    const entry = this.store.get(phone);
    if (!entry) return 0;
    
    entry.attempts += 1;
    this.store.set(phone, entry);
    return entry.attempts;
  }

  delete(phone: string): void {
    this.store.delete(phone);
  }

  cleanup(): void {
    const now = Date.now();
    for (const [phone, entry] of this.store.entries()) {
      if (entry.expiresAt <= now) {
        this.store.delete(phone);
      }
    }
  }

  // To be called when shutting down the server
  destroy(): void {
    clearInterval(this.cleanupInterval);
  }
}

export const otpStore = new OtpStore();
