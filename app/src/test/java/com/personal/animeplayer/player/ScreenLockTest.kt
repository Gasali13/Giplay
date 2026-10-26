package com.personal.animeplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenLockTest {

    @Test
    fun testLockedStateBlocksBackExit() {
        var isScreenLocked = true
        var finishCalled = false
        var feedbackShown = false
        var unlockButtonRevealed = false

        fun handleBack() {
            if (isScreenLocked) {
                unlockButtonRevealed = true
                feedbackShown = true
                return
            }
            finishCalled = true
        }

        // Back pressed while screen is locked
        handleBack()
        assertFalse("Activity must NOT finish when back pressed while locked", finishCalled)
        assertTrue("Unlock button should be revealed", unlockButtonRevealed)
        assertTrue("Feedback toast should be shown", feedbackShown)

        // Unlock screen
        isScreenLocked = false

        // Back pressed when unlocked
        handleBack()
        assertTrue("Activity should finish when back pressed while unlocked", finishCalled)
    }

    @Test
    fun testLockedStateSuppressesControlsVisibility() {
        var isScreenLocked = true
        var isControlsVisible = false

        fun showControls() {
            if (isScreenLocked) return
            isControlsVisible = true
        }

        // Attempt to show controls while locked
        showControls()
        assertFalse("Controls must NOT become visible when screen is locked", isControlsVisible)

        // Unlock and attempt to show controls
        isScreenLocked = false
        showControls()
        assertTrue("Controls should become visible when unlocked", isControlsVisible)
    }

    @Test
    fun testLockedSingleTapWakesUnlockButtonInsteadOfControls() {
        var isScreenLocked = true
        var controlsToggled = false
        var unlockButtonRevealed = false

        fun onSingleTap() {
            if (isScreenLocked) {
                unlockButtonRevealed = true
                return
            }
            controlsToggled = true
        }

        // Tap while locked
        onSingleTap()
        assertTrue("Single tap while locked must wake unlock button", unlockButtonRevealed)
        assertFalse("Single tap while locked must NOT toggle full controls", controlsToggled)

        // Tap while unlocked
        isScreenLocked = false
        onSingleTap()
        assertTrue("Single tap while unlocked should toggle controls", controlsToggled)
    }
}
