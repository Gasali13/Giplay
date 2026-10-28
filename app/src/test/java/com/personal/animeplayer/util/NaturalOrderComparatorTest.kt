package com.personal.animeplayer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderComparatorTest {

    @Test
    fun testNaturalOrderSorting() {
        val rawList = listOf(
            "[Fansub] Anime - Episode 10 [1080p].mkv",
            "[Fansub] Anime - Episode 1 [1080p].mkv",
            "[Fansub] Anime - Episode 2 [1080p].mkv",
            "[Fansub] Anime - Episode 11 [1080p].mkv",
            "[Fansub] Anime - Episode 3 [1080p].mkv"
        )

        val sortedList = rawList.sortedWith(NaturalOrderComparator())

        val expected = listOf(
            "[Fansub] Anime - Episode 1 [1080p].mkv",
            "[Fansub] Anime - Episode 2 [1080p].mkv",
            "[Fansub] Anime - Episode 3 [1080p].mkv",
            "[Fansub] Anime - Episode 10 [1080p].mkv",
            "[Fansub] Anime - Episode 11 [1080p].mkv"
        )

        assertEquals(expected, sortedList)
    }

    @Test
    fun testLeadingZerosAndSuffixSorting() {
        val rawList = listOf(
            "Episode 1 Special",
            "Episode 0001",
            "Episode 1",
            "Episode 01",
            "Episode 2"
        )

        val sortedList = rawList.sortedWith(NaturalOrderComparator())

        val expected = listOf(
            "Episode 1",
            "Episode 01",
            "Episode 0001",
            "Episode 1 Special",
            "Episode 2"
        )

        assertEquals(expected, sortedList)
    }
}
