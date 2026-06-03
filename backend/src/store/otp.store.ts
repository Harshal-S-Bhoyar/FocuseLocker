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
    this.cleanupInterval.unref();
  }

  set(email: string, entry: OtpEntry): void {
    this.store.set(email, entry);
  }

  get(email: string): OtpEntry | undefined {
    return this.store.get(email);
  }

  incrementAttempts(email: string): number {
    const entry = this.store.get(email);
    if (!entry) return 0;
    
    entry.attempts += 1;
    this.store.set(email, entry);
    return entry.attempts;
  }

  delete(email: string): void {
    this.store.delete(email);
  }

  cleanup(): void {
    const now = Date.now();
    for (const [email, entry] of this.store.entries()) {
      if (now >= entry.expiresAt) {
        this.store.delete(email);
      }
    }
  }

  // To be called when shutting down the server
  destroy(): void {
    clearInterval(this.cleanupInterval);
  }
}

export const otpStore = new OtpStore();
