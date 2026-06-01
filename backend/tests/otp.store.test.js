"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const otp_store_1 = require("../src/store/otp.store");
describe("OtpStore", () => {
    let store;
    beforeEach(() => {
        store = new otp_store_1.OtpStore();
    });
    afterEach(() => {
        store.destroy();
    });
    it("should store and retrieve an entry", () => {
        const entry = {
            otpHash: "hash",
            expiresAt: Date.now() + 10000,
            type: "CONTACT_VERIFY",
            attempts: 0,
        };
        store.set("+1234567890", entry);
        const retrieved = store.get("+1234567890");
        expect(retrieved).toEqual(entry);
    });
    it("should increment attempts", () => {
        store.set("+1234567890", {
            otpHash: "hash",
            expiresAt: Date.now() + 10000,
            type: "CONTACT_VERIFY",
            attempts: 0,
        });
        const attempts = store.incrementAttempts("+1234567890");
        expect(attempts).toBe(1);
        const retrieved = store.get("+1234567890");
        expect(retrieved?.attempts).toBe(1);
    });
    it("should delete an entry", () => {
        store.set("+1234567890", {
            otpHash: "hash",
            expiresAt: Date.now() + 10000,
            type: "CONTACT_VERIFY",
            attempts: 0,
        });
        store.delete("+1234567890");
        const retrieved = store.get("+1234567890");
        expect(retrieved).toBeUndefined();
    });
    it("should cleanup expired entries", () => {
        // expired
        store.set("+111", {
            otpHash: "hash1",
            expiresAt: Date.now() - 10000, // in the past
            type: "CONTACT_VERIFY",
            attempts: 0,
        });
        // not expired
        store.set("+222", {
            otpHash: "hash2",
            expiresAt: Date.now() + 10000, // in the future
            type: "CONTACT_VERIFY",
            attempts: 0,
        });
        store.cleanup();
        expect(store.get("+111")).toBeUndefined();
        expect(store.get("+222")).toBeDefined();
    });
});
