package com.focuslock.service.accessibility

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.domain.model.BlockReason
import com.focuslock.domain.usecase.CheckUrlForBlockedContentUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserEventSingleFlightTest {

    private val service: FocusAccessibilityService = mockk(relaxed = true)
    private val checkUrl: CheckUrlForBlockedContentUseCase = mockk()
    private val orchestrator: SmartScanOrchestrator = mockk(relaxed = true)

    // Verdicts are applied on Main (the thread that publishes the latest URL), so a test main
    // dispatcher is required. Unconfined runs the verdict inline, which is what the assertions on
    // effects expect; the dispatch test below swaps in a StandardTestDispatcher instead.
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun eventWithUrl(root: AccessibilityNodeInfo): AccessibilityEvent {
        val event: AccessibilityEvent = mockk()
        every { event.source } returns null
        return event
    }

    private fun urlNode(url: String): AccessibilityNodeInfo {
        val node: AccessibilityNodeInfo = mockk()
        every { node.text } returns url
        return node
    }

    @Test
    fun `superseded verdict is discarded`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        coEvery { checkUrl(any(), any(), any(), any()) } coAnswers {
            if (firstArg<String>().contains("slow")) {
                entered.complete(Unit)
                gate.await()
                BlockCheckResult.Blocked("slow", BlockReason.DOMAIN_BLOCKED)
            } else {
                BlockCheckResult.Allowed
            }
        }
        val root: AccessibilityNodeInfo = mockk()
        every { service.rootInActiveWindow } returns root
        val nodeA = urlNode("https://slow.example/page")
        val nodeB = urlNode("https://fast.example/")
        every { root.findAccessibilityNodeInfosByViewId(any()) } returnsMany listOf(
            mutableListOf(nodeA),
            mutableListOf(nodeB)
        )
        val handler = BrowserEventHandler(service, checkUrl, orchestrator, { emptyList() }, { emptyList() })

        handler.handleBrowserEvent(eventWithUrl(root), "com.android.chrome")
        entered.await()
        handler.handleBrowserEvent(eventWithUrl(root), "com.android.chrome")
        coVerify(timeout = 5000) { orchestrator.startScan("https://fast.example/", any(), any()) }

        gate.complete(Unit)
        Thread.sleep(1000)

        verify(exactly = 0) { service.performGlobalAction(any()) }
        verify(exactly = 0) { service.launchOverlayService(any(), any()) }
    }

    @Test
    fun `latest blocked page is still enforced`() = runTest {
        // The positive path: the guard must not swallow a verdict for the page the user is on.
        coEvery { checkUrl(any(), any(), any(), any()) } returns
            BlockCheckResult.Blocked("casino", BlockReason.DOMAIN_BLOCKED)
        val root: AccessibilityNodeInfo = mockk()
        every { service.rootInActiveWindow } returns root
        every { root.findAccessibilityNodeInfosByViewId(any()) } returns mutableListOf(urlNode("https://casino.example/play"))
        val handler = BrowserEventHandler(service, checkUrl, orchestrator, { emptyList() }, { emptyList() })

        handler.handleBrowserEvent(eventWithUrl(root), "com.android.chrome")

        verify(timeout = 5000) { service.performGlobalAction(any()) }
        verify(timeout = 5000) { service.launchOverlayService("casino", BlockReason.DOMAIN_BLOCKED.name) }
    }

    @Test
    fun `blocked verdict effects are applied on the main dispatcher`() = runTest {
        // Regression: the effects used to run on the Dispatchers.Default worker that evaluated the
        // URL, so a newer URL could be published between the guard read and the effect and the
        // stale verdict would still act. Applying the verdict on Main (where the URL is published)
        // serializes the two. Main is a StandardTestDispatcher here, so nothing is applied until
        // Main actually runs.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val checked = CountDownLatch(1)
        coEvery { checkUrl(any(), any(), any(), any()) } coAnswers {
            checked.countDown()
            BlockCheckResult.Blocked("casino", BlockReason.DOMAIN_BLOCKED)
        }
        var effects = 0
        every { service.performGlobalAction(any()) } answers {
            effects++
            true
        }
        val root: AccessibilityNodeInfo = mockk()
        every { service.rootInActiveWindow } returns root
        every { root.findAccessibilityNodeInfosByViewId(any()) } returns mutableListOf(urlNode("https://casino.example/play"))
        val handler = BrowserEventHandler(service, checkUrl, orchestrator, { emptyList() }, { emptyList() })

        handler.handleBrowserEvent(eventWithUrl(root), "com.android.chrome")

        // Block on the real check finishing; the test scheduler is deliberately not advanced, so a
        // verdict applied off Main would show up as an effect here and fail the next assertion.
        assertTrue("the URL check must run", checked.await(5, TimeUnit.SECONDS))
        Thread.sleep(200)
        assertEquals("no effect may be applied while Main has not run", 0, effects)

        val deadline = System.currentTimeMillis() + 5000
        while (effects == 0 && System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            Thread.sleep(20)
        }
        assertEquals("the verdict must be applied once Main runs", 1, effects)
    }

    @Test
    fun `dispose cancels pending checks`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        coEvery { checkUrl(any(), any(), any(), any()) } coAnswers {
            entered.complete(Unit)
            gate.await()
            BlockCheckResult.Blocked("slow", BlockReason.DOMAIN_BLOCKED)
        }
        val root: AccessibilityNodeInfo = mockk()
        every { service.rootInActiveWindow } returns root
        every { root.findAccessibilityNodeInfosByViewId(any()) } returns mutableListOf(urlNode("https://slow.example/page"))
        val handler = BrowserEventHandler(service, checkUrl, orchestrator, { emptyList() }, { emptyList() })

        handler.handleBrowserEvent(eventWithUrl(root), "com.android.chrome")
        entered.await()
        handler.dispose()
        gate.complete(Unit)
        Thread.sleep(1000)

        verify(exactly = 0) { service.performGlobalAction(any()) }
        verify(exactly = 0) { service.launchOverlayService(any(), any()) }
        coVerify(exactly = 0) { orchestrator.startScan(any(), any(), any()) }
    }
}
