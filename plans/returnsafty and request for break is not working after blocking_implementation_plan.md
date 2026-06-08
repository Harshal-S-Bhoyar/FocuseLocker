# Fix Blocking Overlay Button Navigation

## Problem

When the red blocking overlay appears during a FocusLocker session, both the **"Return"** and **"Request OTP"** buttons are visually clickable but produce no visible effect. The user remains stuck on the red screen.

## Root Causes (Verified by Code Inspection)

### 1. Overlay never dismissed after button click

Both `openMainActivity()` and `openUnlockFlow()` in [BlockingOverlayService.kt](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/app/src/main/java/com/focuslock/service/overlay/BlockingOverlayService.kt#L174-L188) call `startActivity()` but **never call `hideOverlay()` or `stopSelf()`**. The overlay remains on screen, covering the launched activity.

### 2. MainActivity ignores the `navigate_to` intent extra

[MainActivity.kt](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/app/src/main/java/com/focuslock/MainActivity.kt#L23-L44):
- `onCreate()` always starts at `Screen.Onboarding.route` — never reads intent extras
- `onNewIntent()` is not overridden — if the activity is already alive, the intent with `navigate_to=early_unlock` is silently discarded

### 3. "Return" button goes to wrong destination

`openMainActivity()` launches `MainActivity` which always starts at Onboarding. It should navigate to the active session screen.

## Proposed Changes

### [MODIFY] [BlockingOverlayService.kt](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/app/src/main/java/com/focuslock/service/overlay/BlockingOverlayService.kt)

Add `hideOverlay()` and `stopSelf()` to both button handler methods:

```diff
 private fun openMainActivity() {
     val intent = Intent(this, MainActivity::class.java).apply {
         flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
+        putExtra("navigate_to", "active_session")
     }
     startActivity(intent)
-    // Auto-dismiss logic should be handled by accessibility service detecting URL change
+    hideOverlay()
+    stopSelf()
 }

 private fun openUnlockFlow() {
     val intent = Intent(this, MainActivity::class.java).apply {
         flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
         putExtra("navigate_to", "early_unlock")
     }
     startActivity(intent)
+    hideOverlay()
+    stopSelf()
 }
```

---

### [MODIFY] [MainActivity.kt](file:///c:/Users/harshal.bhoyar/Project/FocuseLocker/app/src/main/java/com/focuslock/MainActivity.kt)

Read the `navigate_to` intent extra to determine the correct start destination, and handle `onNewIntent()` for when the activity is already alive:

```diff
 @AndroidEntryPoint
 class MainActivity : ComponentActivity() {

+    private val _navigateTo = mutableStateOf<String?>(null)
+
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         enableEdgeToEdge()

+        _navigateTo.value = intent.getStringExtra("navigate_to")
+
         setContent {
             FocusLockTheme {
                 Surface(...) {
                     val navController = rememberNavController()
+                    val navigateTo by remember { _navigateTo }
+
+                    val startDest = when (navigateTo) {
+                        "active_session" -> Screen.ActiveSession.route
+                        "early_unlock" -> Screen.UnlockRequest.route
+                        else -> Screen.Onboarding.route
+                    }

                     AppNavigation(
                         navController = navController,
-                        startDestination = Screen.Onboarding.route
+                        startDestination = startDest
                     )
                 }
             }
         }
     }
+
+    override fun onNewIntent(intent: Intent) {
+        super.onNewIntent(intent)
+        _navigateTo.value = intent.getStringExtra("navigate_to")
+    }
 }
```

> [!IMPORTANT]
> No changes to `BlockingOverlayScreen.kt`, `OverlayContract.kt`, or `AppNavigation.kt` — the issue is entirely in the service not dismissing + activity not routing.

## Verification Plan

### Manual Testing
1. Start a FocusLock session with keywords
2. Open browser and search for a blocked keyword
3. Verify red overlay appears
4. Click **"Return"** → overlay dismisses, user sees ActiveSession screen
5. Repeat step 2-3, click **"Request OTP"** → overlay dismisses, user sees UnlockRequest screen

### Logcat Validation
```bash
adb logcat -s BlockingOverlayService | grep -E "hideOverlay|openMainActivity|openUnlockFlow"
```
