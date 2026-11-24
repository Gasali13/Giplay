package com.personal.animeplayer.player

enum class LockAction {
    ALLOW,
    REVEAL_UNLOCK_ONLY
}

/**
 * Pure state machine managing player screen-lock behavior so it can be unit-tested directly
 * without Android UI dependencies.
 */
class ScreenLockController {

    var isLocked: Boolean = false
        private set

    fun lock() {
        isLocked = true
    }

    fun unlock() {
        isLocked = false
    }

    fun canShowControls(): Boolean = !isLocked

    fun onBackPressed(): LockAction =
        if (isLocked) LockAction.REVEAL_UNLOCK_ONLY else LockAction.ALLOW

    fun onSingleTap(): LockAction =
        if (isLocked) LockAction.REVEAL_UNLOCK_ONLY else LockAction.ALLOW

    fun onToggleControls(): LockAction =
        if (isLocked) LockAction.REVEAL_UNLOCK_ONLY else LockAction.ALLOW

    fun shouldConsumeBackKeyEvent(): Boolean = isLocked
}
