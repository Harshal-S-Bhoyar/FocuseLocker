# Cooldown Countdown Bug — Fix Plan

> Status: READY TO IMPLEMENT  
> Based on: full source-code read, zero assumptions

---

## Facts (What the Code Actually Does)

### Fact 1 — `VerifyUnlockOtpUseCase` (line 42–44)
When OTP is valid:
```kotlin
val endsAt = System.currentTimeMillis() + 10 * 60 * 1000L  // 10 min from now
unlockRepository.updateStatus(requestId, UnlockRequestStatus.COOLDOWN)
unlockRepository.setCooldownEndsAt(requestId, endsAt)
unlockWorkerScheduler.scheduleCooldownTimer(endsAt)
```
`endsAt` is stored in DB. `CooldownTimerWorker` is scheduled to deactivate session at `endsAt`.

### Fact 2 — `UnlockViewModel` (line 87)
When DB status = COOLDOWN, state becomes:
```kotlin
UnlockUiState.CooldownActive(requestId = request.id, endsAt = request.cooldownEndsAt ?: 0L)
```
`endsAt` is correctly passed into the UI state.

### Fact 3 — `OtpEntryScreen` (line 28–31)
```kotlin
LaunchedEffect(state) {
    if (state is UnlockUiState.CooldownActive) {
        onNavigateToCooldown()  // ← triggers navigation to UnlockCooldown route
    }
}
```
Navigation to `UnlockCooldownScreen` is triggered correctly.

### Fact 4 — `UnlockCooldownScreen` (line 68–78) — **BUG #1**
```kotlin
val endsAt = (state as? UnlockUiState.CooldownActive)?.endsAt ?: 0L
if (endsAt > 0) {
    val expiryTimeStr = SimpleDateFormat("HH:mm:ss", ...).format(Date(endsAt))
    Text(text = stringResource(R.string.unlock_cooldown_unlocks_at, expiryTimeStr))
}
```
Shows **static clock time** (e.g. "Unlocks at 00:28:00"), NOT a live countdown.  
No `LaunchedEffect` or coroutine tick. Time never updates.

### Fact 5 — `AppNavigation.kt` (line 188–196) — **BUG #2**
```kotlin
composable(Screen.UnlockCooldown.route) {
    UnlockCooldownScreen(
        onNavigateToHome = {
            navController.navigate(Screen.Onboarding.route) {
                popUpTo(0) { inclusive = true }  // ← clears ENTIRE backstack
            }
        }
    )
}
```
`Return to Home` → navigates to `Onboarding`.  
`OnboardingScreen` then checks if a session is active → redirects to `ActiveSession`.  
`ActiveSession` shows the session timer (the main lock countdown), NOT the cooldown.  
**The cooldown screen is abandoned immediately.**

### Fact 6 — No auto-navigation when cooldown expires
`UnlockCooldownScreen` has no `LaunchedEffect` that fires when countdown hits zero.  
`CooldownTimerWorker` deactivates the session in DB — but the screen does not react.

---

## Root Causes (Exact, Verified)

| # | Root Cause | Evidence |
|---|-----------|----------|
| RC-1 | "Return to Home" goes to Onboarding, not ActiveSession | `AppNavigation.kt` line 191: `Screen.Onboarding.route` |
| RC-2 | Countdown is a static clock string, not a live timer | `UnlockCooldownScreen.kt` lines 71–78: `SimpleDateFormat` with no update loop |
| RC-3 | No auto-navigation when cooldown timer hits zero | `UnlockCooldownScreen.kt`: no `LaunchedEffect` watching countdown |

---

## Fix Plan — 3 Precise Patches

---

### Patch 1 — Fix `AppNavigation.kt`: "Return to Home" → `ActiveSession`

**File:** `app/src/main/java/com/focuslock/ui/navigation/AppNavigation.kt`  
**Lines:** 188–196  

**Current (WRONG):**
```kotlin
composable(Screen.UnlockCooldown.route) {
    UnlockCooldownScreen(
        onNavigateToHome = {
            navController.navigate(Screen.Onboarding.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    )
}
```

**Replace with:**
```kotlin
composable(Screen.UnlockCooldown.route) {
    UnlockCooldownScreen(
        onNavigateToHome = {
            // Navigate to ActiveSession — session is still running during cooldown.
            // Use popUpTo(UnlockCooldown) inclusive=true so back button
            // doesn't return to this screen.
            navController.navigate(Screen.ActiveSession.route) {
                popUpTo(Screen.UnlockCooldown.route) { inclusive = true }
            }
        },
        onCooldownExpired = {
            // Cooldown timer reached zero — session is deactivated by CooldownTimerWorker.
            // Navigate to SessionComplete and clear entire stack.
            navController.navigate(Screen.SessionComplete.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    )
}
```

**Why:** Session is still ACTIVE during cooldown. The correct "home" during an active session IS `ActiveSession`. Onboarding is for unauthenticated users.

---

### Patch 2 — Fix `UnlockCooldownScreen.kt`: Add live countdown + auto-navigate

**File:** `app/src/main/java/com/focuslock/ui/session/UnlockCooldownScreen.kt`

**Current signature:**
```kotlin
fun UnlockCooldownScreen(
    viewModel: UnlockViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit
)
```

**New signature + full replacement:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockCooldownScreen(
    viewModel: UnlockViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit,
    onCooldownExpired: () -> Unit   // NEW — called when 10-min timer hits zero
) {
    val state by viewModel.uiState.collectAsState()
    
    // Get the endsAt timestamp from the ViewModel state
    val endsAt = (state as? UnlockUiState.CooldownActive)?.endsAt ?: 0L

    // Live countdown in milliseconds — ticks every second
    var remainingMillis by remember(endsAt) {
        mutableStateOf(maxOf(0L, endsAt - System.currentTimeMillis()))
    }

    // Tick the countdown every second
    LaunchedEffect(endsAt) {
        while (remainingMillis > 0L) {
            delay(1000L)
            remainingMillis = maxOf(0L, endsAt - System.currentTimeMillis())
        }
        // Countdown reached zero — cooldown is over
        if (endsAt > 0L) {
            onCooldownExpired()
        }
    }

    // Format remainingMillis as MM:SS
    val minutes = (remainingMillis / 1000) / 60
    val seconds = (remainingMillis / 1000) % 60
    val countdownText = String.format("%02d:%02d", minutes, seconds)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Cooldown Active") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.unlock_cooldown_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.unlock_cooldown_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // LIVE countdown display — replaces static clock string
            if (endsAt > 0L) {
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "remaining before unlock",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onNavigateToHome,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Return to Active Session")
            }
        }
    }
}
```

**Why:**
- `remember(endsAt)` — resets whenever `endsAt` changes
- `LaunchedEffect(endsAt)` — coroutine loop ticks every 1 second, cancels when `endsAt` changes or composable leaves composition
- `maxOf(0L, endsAt - System.currentTimeMillis())` — wall-clock subtraction, no drift
- When reaches zero → `onCooldownExpired()` fires → navigates to `SessionComplete`

---

### Patch 3 — Add `FontWeight` import to `UnlockCooldownScreen.kt`

Add these imports (some already exist, add only the missing ones):
```kotlin
import androidx.compose.ui.text.font.FontWeight
```

---

## No Other Files Need Changes

| File | Reason |
|------|--------|
| `UnlockViewModel.kt` | `CooldownActive(endsAt)` already correct |
| `VerifyUnlockOtpUseCase.kt` | `endsAt` calculation correct |
| `CooldownTimerWorker.kt` | DB deactivation at `endsAt` correct |
| `OtpEntryScreen.kt` | Navigation to cooldown correct |
| `ActiveSessionScreen.kt` | Unchanged — shows session timer correctly |

---

## Test Steps After Fix

1. Start a focus session
2. Tap "Request Unlock" → get OTP → enter correct OTP
3. **Expected:** `UnlockCooldownScreen` shows `MM:SS` live countdown (starts ~10:00)
4. Tap "Return to Active Session"
5. **Expected:** `ActiveSessionScreen` with session timer (NOT Onboarding)
6. Back-press from ActiveSession → cannot go back to cooldown (popped)
7. Wait 10 min OR wait for `CooldownTimerWorker` to fire
8. **Expected:** App navigates to `SessionCompleteScreen` automatically
