package org.daiv.tick

import kotlin.test.Test
import kotlin.test.assertEquals

class LastReadDataAccessorMockTest {

    @Test
    fun testRead() {
        val timeables = timeableListOf(60, 180, 300)
        val accessor = LastReadDataAccessorMock( timeables)
        val got = accessor.read(30L, 90L, 1)
        assertEquals(timeableListOf(60), got)
    }

    @Test
    fun testRead2() {
        val timeables = timeableListOf(60, 180, 300)
        val accessor = LastReadDataAccessorMock(timeables)
        val got = accessor.read(30L, 400L, 2)
        assertEquals(timeableListOf(60, 180), got)
    }

    @Test
    fun testReadLastBefore() {
        val timeables = timeableListOf(60, 180, 300)
        val accessor = LastReadDataAccessorMock(timeables)
        val got = accessor.readLastBefore(1, 90L)
        assertEquals(timeableListOf(60), got)
    }

    @Test
    fun testReadLastBefore2() {
        val timeables = timeableListOf(60, 180, 300)
        val accessor = LastReadDataAccessorMock(timeables)
        val got = accessor.readLastBefore(1, 59L)
        assertEquals(timeableListOf(), got)
    }

    @Test
    fun testReadNextAfter() {
        val timeables = timeableListOf(60, 180, 300)
        val accessor = LastReadDataAccessorMock(timeables)
        val got = accessor.readNextAfter(2, 59L)
        assertEquals(timeableListOf(60, 180), got)
    }

    @Test
    fun testReadNextAfter2() {
        val timeables = timeableListOf(60, 180, 300)
        val accessor = LastReadDataAccessorMock(timeables)
        val got = accessor.readNextAfter(1, 400L)
        assertEquals(timeableListOf(), got)
    }


}