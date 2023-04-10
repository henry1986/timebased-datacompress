package org.daiv.tick

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeableTest {


    @Test
    fun testSubList() {
        val list = timeableListOf(5, 9, 15, 30, 35, 45, 49, 52, 59, 63, 68, 76)
        assertEquals(timeableListOf(15, 30, 35), list.subList(15L, 35L))
        assertEquals(timeableListOf(30, 35), list.subList(16L, 35L))
        assertEquals(timeableListOf(), list.subList(16L, 29L))
    }
}