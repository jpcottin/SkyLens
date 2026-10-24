package com.jpcottin.skylens.ui.objects

import com.jpcottin.skylens.ui.preview.PreviewSky
import org.junit.Assert.assertEquals
import org.junit.Test

/** The objects list is keyed by object id: a search must never yield two rows with the same key. */
class ObjectSectionsTest {
    @Test
    fun search_neverYieldsTwoRowsWithTheSameKey() {
        val ids = ObjectSections.from(PreviewSky.nightSnapshot, "a").stars.map { it.obj.id }
        assertEquals(ids.toString(), ids.size, ids.toSet().size)
    }
}
