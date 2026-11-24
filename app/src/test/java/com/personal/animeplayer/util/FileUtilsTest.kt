package com.personal.animeplayer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileUtilsTest {

    @Test
    fun testDecodePercentEncodedJapaneseBracketsAndTruncatedTail() {
        val raw = "%E3%80%8CCreditless%E3%80%8DOne_Punch_Man_OP___Opening_1%E3%80%8CUHD_60FPS%E.mp4"
        assertEquals(
            "「Creditless」One_Punch_Man_OP___Opening_1「UHD_60FPS",
            FileUtils.getDisplayTitle(raw)
        )
    }

    @Test
    fun testDecodePercentEncodedSpacesAndBracketsPreservingPlus() {
        val raw = "%5BSubsPlease%5D%20Frieren%20-%2001%20%5B1080p%20HDR10+%5D.mkv"
        assertEquals(
            "[SubsPlease] Frieren - 01 [1080p HDR10+]",
            FileUtils.getDisplayTitle(raw)
        )
    }

    @Test
    fun testLiteralPercentAndPlusUntouched() {
        assertEquals(
            "Mob Psycho 100% Pure [HDR10+]",
            FileUtils.getDisplayTitle("Mob Psycho 100% Pure [HDR10+].mkv")
        )
        assertEquals(
            "100%",
            FileUtils.getDisplayTitle("100%.mp4")
        )
    }
}
