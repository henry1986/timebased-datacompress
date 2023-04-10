package org.daiv.tick

import mu.KotlinLogging
import kotlin.test.Test
import kotlin.test.assertEquals

open class CompressedManagerInstallerTest {
    val logger = KotlinLogging.logger {}

    data class TestDataCollection(override val start: Long, override val end: Long, val list: List<SimpleTimeable>) :
        DataCollection {
        override fun toString(): String {
            return "$start - ${list.map { it.time }}"
        }
    }

    class TestReadTimeable : ReadTimeable<SimpleTimeable, TestDataCollection> {
        override fun read(fileData: TestDataCollection) = fileData.list
    }

    data class TestReadAccessor(
        override val allDatas: List<TestDataCollection>,
        override val readTimeable: ReadTimeable<SimpleTimeable, TestDataCollection>
    ) : ReadAccessor<SimpleTimeable, TestDataCollection>


    fun Long.data(to: Long, size: Long = 100L): List<SimpleTimeable> {
        val step = (to - this) / size
        return (0L until size).map { SimpleTimeable((it * step) + this) }
    }

    fun Long.dist(to: Long, step: Long = 10L): List<SimpleTimeable> {
        return (0..((to - this) / step)).map { SimpleTimeable((it * step) + this) }
    }

    fun TestDataCollection.next(size: Long): TestDataCollection {
        val to = 2 * end - start
        return TestDataCollection(end, to, end.data(to, size))
    }

    fun TestDataCollection.list(i: Int, size: Long): List<TestDataCollection> {
        val ret: MutableList<TestDataCollection> = mutableListOf(this)
        for (x in 0 until i) {
            ret.add(ret.last().next(size))
        }
        return ret
    }

    val testDatas = TestDataCollection(0L, 100L, 0L.data(100L, 100L)).list(10, 100L)
    val accessor = TestReadAccessor(testDatas, TestReadTimeable())
    val list = (1L..300L).map { SimpleTimeable(it) } + (600L..800L).map { SimpleTimeable(it) }

    infix fun Long.readTo(to: Long) = (this..to).map { SimpleTimeable(it) }
    fun List<SimpleTimeable>.test(from: Long, to: Long, expect: List<SimpleTimeable> = from readTo to) {
        assertEquals(expect.first(), first())
        assertEquals(expect.last(), last())
        assertEquals(expect.size, size)
        assertEquals(expect.second(), second())
        assertEquals(expect.secondToLast(), secondToLast())
        assertEquals(expect, this)
    }

    val testDatas2 = TestDataCollection(0L, 100L, 0L.data(100L, 10L)).list(10, 10L)
    val accessor2 = TestReadAccessor(testDatas2, TestReadTimeable())

    fun printData() {
        println("testData1:")
        testDatas.forEach {
            println("it: $it")
        }
        println("testData2:")
        testDatas2.forEach {
            println("it: $it")
        }
        println("testData3:")
        testDatas3.forEach {
            println("it: $it")
        }
    }

    val testDatas3 = listOf(
        TestDataCollection(0L, 100L, timeableListOf(5, 9, 24, 26, 34, 38, 63)),
        TestDataCollection(100L, 200L, timeableListOf(105, 109, 184, 192)),
        TestDataCollection(300L, 400L, timeableListOf(305, 334, 338, 354)),
        TestDataCollection(400L, 500L, timeableListOf(409, 424, 434, 463, 486)),
    )
    val accessor3 = TestReadAccessor(testDatas3, TestReadTimeable())

}

class TestX : CompressedManagerInstallerTest() {
    @Test
    fun testSimpleRead() {
        assertEquals(timeableListOf(24, 26, 34), accessor3.read(21, 34))
    }

    @Test
    fun testBorderRead() {
        assertEquals(timeableListOf(63, 105, 109), accessor3.read(62, 110))
    }

    @Test
    fun testGapCompleteRead() {
        assertEquals(timeableListOf(184, 192, 305), accessor3.read(182, 306))
    }

    @Test
    fun testGapEndRead() {
        assertEquals(timeableListOf(305), accessor3.read(210, 309))
    }

    @Test
    fun testGapBeginRead() {
        assertEquals(timeableListOf(184, 192), accessor3.read(182, 210))
    }
}

class TestReadLastBefore : CompressedManagerInstallerTest() {

    @Test
    fun atBeginningOfNextDataCollection() {
        assertEquals(listOf(SimpleTimeable(299L)), accessor.readLastBefore(1, 300L))
    }

    @Test
    fun with2AtEndOfDataCollection() {
        assertEquals(listOf(SimpleTimeable(299L), SimpleTimeable(300L)), accessor.readLastBefore(2, 301L))
    }

    @Test
    fun with102AtEndOfDataCollection() {
        val actual = accessor.readLastBefore(102, 301L)
//                           val first = actual.first()
//                           logger.trace { "first: $first" }
//                           val last = actual.last()
//                           logger.trace { "last: $last" }
//                           logger.trace { "size: ${actual.size}" }
        assertEquals(199L readTo 300L, actual)
    }

    @Test
    fun fromBeginningOverlapping() {
        val actual = accessor.readLastBefore(305, 301L)
//                           val first = actual.first()
//                           logger.trace { "first: $first" }
//                           val last = actual.last()
//                           logger.trace { "last: $last" }
//                           logger.trace { "size: ${actual.size}" }
        assertEquals(0L readTo 300L, actual)
    }

    @Test
    fun tillEnd() {
        val actual = accessor.readLastBefore(10, 10001L)
//                           val first = actual.first()
//                           logger.trace { "first: $first" }
//                           val last = actual.last()
//                           logger.trace { "last: $last" }
//                           logger.trace { "size: ${actual.size}" }
        assertEquals(1090L readTo 1099L, actual)
    }
}

class TestReadNextAfter : CompressedManagerInstallerTest() {
    fun readTest(numberOfToRead: Int, start: Long, from: Long, to: Long) =
        accessor.readNextAfter(numberOfToRead, start).test(from, to)

    @Test
    fun atBeginningOfNextDataCollection() {
        assertEquals(listOf(SimpleTimeable(301L)), accessor.readNextAfter(1, 300L))
    }

    @Test
    fun with2AtEndOfDataCollection() {
        readTest(2, 299L, 300L, 301L)
    }

    @Test
    fun with102AtEndOfDataCollection() {
        readTest(102, 299L, 300L, 401L)
    }

    @Test
    fun fromBeginningOverlapping() {
        readTest(301, -1L, 0L, 300L)
    }

    @Test
    fun tillEnd() {
        readTest(10, 1090L, 1091L, 1099L)
    }
}

class Read : CompressedManagerInstallerTest() {
    fun readTest(from: Long, to: Long) = accessor.read(from, to).test(from, to)

    @Test
    fun simpleRead() {
        readTest(200L, 250L)
    }

    @Test
    fun overFileBorder() {
        readTest(200L, 300L)
    }

    @Test
    fun overTwoFileBorder() {
        readTest(100L, 300L)
    }

    @Test
    fun overThreeFileBorder() {
        readTest(110L, 450L)
    }

    @Test
    fun fromStart() {
        readTest(0L, 300L)
    }

    @Test
    fun tillEnd() {
        readTest(500L, 1099L)
    }
}

class WithGapReadLastBefore : CompressedManagerInstallerTest() {
    fun readTest(numberOfToRead: Int, start: Long, from: Long, to: Long) =
        accessor2.readLastBefore(numberOfToRead, start).test(from, to, from.dist(to, 10L))

    @Test
    fun lastBefore() {
        readTest(2, 205L, 190L, 200L)
    }

    @Test
    fun sixLastBeforeOverBorder() {
        readTest(6, 215L, 160L, 210L)
    }
}

class WithGapNextAfter : CompressedManagerInstallerTest() {
    fun readTest(numberOfToRead: Int, start: Long, from: Long, to: Long) =
        accessor2.readNextAfter(numberOfToRead, start).test(from, to, from.dist(to, 10L))

    @Test
    fun noBorder() {
        readTest(2, 202L, 210L, 220L)
    }

    @Test
    fun overBorder() {
        readTest(4, 189L, 190L, 220L)
    }
}

class WithGapRead : CompressedManagerInstallerTest() {
    fun readTest(start: Long, end: Long, from: Long, to: Long) =
        accessor2.read(start, end).test(from, to, from.dist(to, 10L))

    @Test
    fun noBorder() {
        readTest(201, 232L, 210L, 230L)
    }

    @Test
    fun overBorder() {
        readTest(156L, 212L, 160L, 210L)
    }
}

class TestFirstAndLast : CompressedManagerInstallerTest() {
    val emptyAccessor = TestReadAccessor(emptyList(), TestReadTimeable())

    @Test
    fun last() {
        assertEquals(SimpleTimeable(1090L), accessor2.last())
    }

    @Test
    fun lastCanBeNull() {
        assertEquals(null, emptyAccessor.last())
    }

    @Test
    fun firstCanBeNull() {
        assertEquals(null, emptyAccessor.first())
    }
}


