package com.personal.animeplayer.util

import com.personal.animeplayer.data.model.Episode
import com.personal.animeplayer.data.model.SortOrder

/**
 * Utility functions for consistent sorting of media items across all activities and view models.
 */
object SortUtils {

    private val naturalOrderComparator = NaturalOrderComparator()

    fun sortEpisodes(episodes: List<Episode>, order: SortOrder): List<Episode> {
        return when (order) {
            SortOrder.NAME -> episodes.sortedWith { e1, e2 ->
                val name1 = e1.fileName.ifEmpty { e1.title }
                val name2 = e2.fileName.ifEmpty { e2.title }
                naturalOrderComparator.compare(name1, name2)
            }
            SortOrder.NEWEST -> episodes.sortedWith { e1, e2 ->
                val dateCmp = e2.dateAdded.compareTo(e1.dateAdded)
                if (dateCmp != 0) {
                    dateCmp
                } else {
                    val name1 = e1.fileName.ifEmpty { e1.title }
                    val name2 = e2.fileName.ifEmpty { e2.title }
                    naturalOrderComparator.compare(name1, name2)
                }
            }
            SortOrder.OLDEST -> episodes.sortedWith { e1, e2 ->
                val dateCmp = e1.dateAdded.compareTo(e2.dateAdded)
                if (dateCmp != 0) {
                    dateCmp
                } else {
                    val name1 = e1.fileName.ifEmpty { e1.title }
                    val name2 = e2.fileName.ifEmpty { e2.title }
                    naturalOrderComparator.compare(name1, name2)
                }
            }
        }
    }
}

fun List<Episode>.sortedByOrder(order: SortOrder): List<Episode> {
    return SortUtils.sortEpisodes(this, order)
}
