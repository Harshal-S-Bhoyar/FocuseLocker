# Cooldown Fix — Execution Plan

## Root Cause Analysis

### Bug 1: No cooldown timer after pressing back
**Trace**: User presses "Return to Active Session" on `UnlockCooldownScreen` → navigates to `ActiveSessionScreen` → **no cooldown timer visible** because `ActiveSessionScreen` only shows the session timer and permission status. The cooldown `endsAt` data exists in the DB but nothing reads it on this screen.

### Bug 2: Session not killed after cooldown expires
**Trace**: `CooldownTimerWorker` is scheduled via WorkManager with `setInitialDelay(endsAt - now)`. When it fires, it calls `DeactivateSessionUseCase` → `sessionRepository.endSession()` + `stopRuntime()`. This **does work** in the background. The issue is the **UI** — `ActiveSessionScreen` observes `remainingTimeMillis` (session timer), NOT the cooldown. When the session ends, the flow emits `null` session, but `ActiveSessionScreen.LaunchedEffect(remainingTimeMillis)` only fires on `0L`, not when session disappears. The Onboarding check `hasActiveSession` would eventually route correctly on fresh launch, but NOT while the user is already on `ActiveSessionScreen`.

### Bug 3: Auto-navigation loop (my previous change)
The `LaunchedEffect(unlockStatus)` I added pushes users from `ActiveSessionScreen` → `UnlockFlow` whenever status is COOLDOWN. But if user presses back from UnlockCooldown, they land on ActiveSession, which immediately pushes them back — **infinite loop**. This must be removed.

---

## Plan (4 Tasks)

### Task 1: Remove auto-navigation loop from ActiveSessionScreen
**File**: `ActiveSessionScreen.kt` (lines 58-66)  
**Action**: Delete the `LaunchedEffect(unlockStatus)` block that auto-navigates to UnlockFlow  
**Why**: It creates an infinite navigation loop when user presses back from cooldown  
**Verify**: User can press back from cooldown and stay on ActiveSessionScreen  

### Task 2: Expose `cooldownEndsAt` from ActiveSessionViewModel  
**File**: `ActiveSessionViewModel.kt`  
**Action**: Change `activeUnlockStatus` from `StateFlow<UnlockRequestStatus?>` to a richer `StateFlow<ActiveUnlockInfo?>` data class that includes both `status` and `cooldownEndsAt`  
```kotlin
data class ActiveUnlockInfo(
    val status: UnlockRequestStatus,
    val cooldownEndsAt: Long?
)
```
**Map**: `unlockRepository.getLatestRequest(session.id).map { req -> req?.let { ActiveUnlockInfo(it.status, it.cooldownEndsAt) } }`  
**Verify**: ViewModel exposes `cooldownEndsAt` value when status is COOLDOWN  

### Task 3: Add cooldown timer widget to ActiveSessionScreen  
**File**: `ActiveSessionScreen.kt`  
**Action**: Below the permissions `Card`, add a conditional `Card` that appears only when `activeUnlockInfo?.status == COOLDOWN` and `cooldownEndsAt > now`. Contains:
- Hourglass icon + "Cooldown Active" label
- Live MM:SS countdown (same tick logic as `UnlockCooldownScreen`)
- When countdown reaches 0 → navigate to `SessionComplete`

**Layout position**: Between the permissions Card and the "Request Early Unlock" button (replaces `Spacer(modifier = Modifier.weight(1f))`)  
**Verify**: Timer widget visible on ActiveSessionScreen during cooldown  

### Task 4: Add session-killed navigation in ActiveSessionScreen  
**File**: `ActiveSessionScreen.kt`  
**Action**: The `activeUnlockInfo` flow will emit `null` when `CooldownTimerWorker` calls `DeactivateSessionUseCase` (which calls `endSession` → session becomes non-active → `getActiveSession()` emits `null` → `flatMapLatest` emits `null`). Add a `LaunchedEffect` that watches for the session going null:
```kotlin
LaunchedEffect(remainingTimeMillis) {
    if (remainingTimeMillis == 0L) {
        onNavigateToSessionComplete()
    }
}
```
This already exists. But we also need to handle when the **session disappears entirely** (killed by worker while user is on this screen). The `getActiveSession()` returning null should trigger navigation. This is already handled in `OnboardingViewModel.initialize()` — but only on app restart. We need `ActiveSessionScreen` to react.

**Solution**: Observe `activeUnlockInfo` — when it transitions from `COOLDOWN` to `COMPLETED` (or when the session flow emits null), navigate to `SessionComplete`.

---

## Files Modified

| File | Change Type |
|---|---|
| `ActiveSessionScreen.kt` | Remove loop + add cooldown widget + add session-killed nav |
| `ActiveSessionViewModel.kt` | Change `activeUnlockStatus` to `activeUnlockInfo` with `cooldownEndsAt` |

## Files NOT Modified (verified correct)

| File | Why |
|---|---|
| `CooldownTimerWorker.kt` | ✅ Correctly calls `deactivateSessionUseCase` — background kill works |
| `VerifyUnlockOtpUseCase.kt` | ✅ Correctly schedules WorkManager with `endsAt` |
| `DeactivateSessionUseCase.kt` | ✅ Correctly calls `endSession` + `cancelTimers` + `stopRuntime` |
| `UnlockCooldownScreen.kt` | ✅ Timer logic works, no changes needed |
| `AppNavigation.kt` | ✅ Routes are correct |

## Success Criteria

1. User verifies OTP → cooldown starts → user presses "Return to Active Session"
2. ActiveSessionScreen shows cooldown timer widget at bottom with live countdown
3. When cooldown timer hits 00:00 → navigates to SessionComplete
4. If user stays on UnlockCooldownScreen, `onCooldownExpired` still works (unchanged)
5. No navigation loops
