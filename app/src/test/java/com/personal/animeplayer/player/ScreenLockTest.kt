package com.personal.animeplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLockTest {

    @Test
    fun testDefaultUnlockedStateAllowsControlsAndBackExit() {
        val controller = ScreenLockController()

        assertFalse(controller.isLocked)
        assertTrue(controller.canShowControls())
        assertEquals(LockAction.ALLOW, controller.onBackPressed())
        assertEquals(LockAction.ALLOW, controller.onSingleTap())
        assertEquals(LockAction.ALLOW, controller.onToggleControls())
        assertFalse(controller.shouldConsumeBackKeyEvent())
    }

    @Test
    fun testLockedStateBlocksBackExitAndUnlocksCleanly() {
        val controller = ScreenLockController()
        controller.lock()

        assertTrue(controller.isLocked)
        assertEquals(LockAction.REVEAL_UNLOCK_ONLY, controller.onBackPressed())
        assertTrue(controller.shouldConsumeBackKeyEvent())

        controller.unlock()

        assertFalse(controller.isLocked)
        assertEquals(LockAction.ALLOW, controller.onBackPressed())
        assertFalse(controller.shouldConsumeBackKeyEvent())
    }

    @Test
    fun testLockedStateSuppressesControlsVisibility() {
        val controller = ScreenLockController()
        controller.lock()

        assertFalse("Controls must NOT become visible when screen is locked", controller.canShowControls())

        controller.unlock()

        assertTrue("Controls should become visible when unlocked", controller.canShowControls())
    }

    @Test
    fun testLockedSingleTapAndToggleWakeUnlockButtonInsteadOfControls() {
        val controller = ScreenLockController()
        controller.lock()

        assertEquals(LockAction.REVEAL_UNLOCK_ONLY, controller.onSingleTap())
        assertEquals(LockAction.REVEAL_UNLOCK_ONLY, controller.onToggleControls())

        controller.unlock()

        assertEquals(LockAction.ALLOW, controller.onSingleTap())
        assertEquals(LockAction.ALLOW, controller.onToggleControls())
    }
}
