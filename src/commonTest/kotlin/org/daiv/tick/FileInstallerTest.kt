package org.daiv.tick

import kotlin.test.*

infix fun <T : Timeable> FileInstallerTest.DataCollectionMock.with(list: List<T>): FileInstallerTest.DataCollectionContainer<T> {
    return FileInstallerTest.DataCollectionContainer(this, list)
}

class FileInstallerTest {

    data class DataCollectionMock(override val isCurrent: Boolean, override val start: Long, override val end: Long) :
        CurrentDataCollection{
        override fun toString(): String {
            return "$isCurrent - $start - $end"
        }
        }


    class FileInstallerMock<T : Timeable>(
        override val interval: Long,
        val toStore: MutableList<DataCollectionContainer<T>>
    ) : FileInstaller<T, DataCollectionMock> {

//        override val fileDatas: List<DataCollectionMock>
//            get() = toStore.map { it.dataCollection }

        override fun build(start: Long, end: Long, isCurrent: Boolean, fileName: String): DataCollectionMock {
            return DataCollectionMock(isCurrent, start, end)
        }

        override fun doesCurrentFileExists(): Boolean {
            return toStore.any { it.dataCollection.isCurrent }
        }

        override fun deleteCurrentFile() {
            println("delete current file called")
            if (toStore.lastOrNull() == null) {
                fail("test failed -deleteCurrent should only be called, if there is a current file")
            } else {
                val last = toStore.removeLast()
                if (!last.dataCollection.isCurrent) {
                    fail("last removed is not current")
                } else {
                    println("current file deleted: $last")
                }
            }
        }

        override fun readCurrentTicks(): List<T> {
            return toStore.lastOrNull()?.list ?: emptyList()
        }

        override fun store(fileData: DataCollectionMock, ticks: List<T>) {
            if (toStore.lastOrNull()?.let { it.dataCollection.end <= fileData.start } == true) {
                toStore.add(fileData with ticks)
            } else {
                val i = -toStore.binarySearchBy(fileData.start) { it.dataCollection.start } - 1
                toStore.add(i, fileData with ticks)
            }
        }

        override fun getFirstFile(): DataCollectionMock {
            return toStore.first().dataCollection
        }

        override fun getFirstFileTicks(): List<T> {
            return toStore.firstOrNull()?.list ?: emptyList()
        }

        override fun deleteFirstFile() {
            toStore.removeFirst()
        }
    }

    data class DataCollectionContainer<T : Timeable>(val dataCollection: DataCollectionMock, val list: List<T>) {
        override fun toString(): String {
            return "$dataCollection - ${list.map { it.time }}"
        }
    }

    @Test
    fun testIsToInsertBefore() {
        val toStore: MutableList<DataCollectionContainer<SimpleTimeable>> = mutableListOf(
            DataCollectionMock(false, 100, 110) with timeableListOf(102, 108),
            DataCollectionMock(true, 140, 150) with timeableListOf(141)
        )
        val c = FileInstallerMock(10L, toStore)
        assertTrue(c.isToInsertBefore(99L))
        assertFalse(c.isToInsertBefore(143L))
        assertFalse(c.isToInsertBefore(103L))
        assertTrue(c.isToInsertBefore(102L))
        assertTrue(c.isToInsertBefore(101L))
        assertFalse(c.isToInsertBefore(111L))

    }

    @Test
    fun install() {
        val toStore: MutableList<DataCollectionContainer<SimpleTimeable>> = mutableListOf()
        val c = FileInstallerMock(10L, toStore)

        c.insert(timeableListOf(5, 6, 29, 31, 36, 100, 130))
        assertEquals(
            listOf(
                DataCollectionMock(false, 0, 10) with timeableListOf(5, 6),
                DataCollectionMock(false, 20, 30) with timeableListOf(29),
                DataCollectionMock(false, 30, 40) with timeableListOf(31, 36),
                DataCollectionMock(false, 100, 110) with timeableListOf(100),
                DataCollectionMock(true, 130, 140) with timeableListOf(130),
            ), toStore
        )
        c.insert(timeableListOf(132, 138, 141))
        assertEquals(
            listOf(
                DataCollectionMock(false, 0, 10) with timeableListOf(5, 6),
                DataCollectionMock(false, 20, 30) with timeableListOf(29),
                DataCollectionMock(false, 30, 40) with timeableListOf(31, 36),
                DataCollectionMock(false, 100, 110) with timeableListOf(100),
                DataCollectionMock(false, 130, 140) with timeableListOf(130, 132, 138),
                DataCollectionMock(true, 140, 150) with timeableListOf(141),
            ), toStore
        )
//        println("toStore: ")
//        toStore.forEach {
//            println("it: ${it.first} with ${it.second.map { it.time }}")
//        }
    }
    @Test
    fun installReplacingEndStart() {
        val toStore: MutableList<DataCollectionContainer<TimeableWithFlag>> = mutableListOf()
        val c = FileInstallerMock(10L, toStore)

        c.insert(flagTimeableListOf(false, 5, 6, 29, 31, 36, 100, 130, 132))
        assertEquals(
            listOf(
                DataCollectionMock(false, 0, 10) with flagTimeableListOf(false, 5, 6),
                DataCollectionMock(false, 20, 30) with flagTimeableListOf(false, 29),
                DataCollectionMock(false, 30, 40) with flagTimeableListOf(false, 31, 36),
                DataCollectionMock(false, 100, 110) with flagTimeableListOf(false, 100),
                DataCollectionMock(true, 130, 140) with flagTimeableListOf(false, 130, 132),
            ), toStore
        )
        c.insert(flagTimeableListOf(true, 132, 138, 141))
        assertEquals(
            listOf(
                DataCollectionMock(false, 0, 10) with flagTimeableListOf(false, 5, 6),
                DataCollectionMock(false, 20, 30) with flagTimeableListOf(false, 29),
                DataCollectionMock(false, 30, 40) with flagTimeableListOf(false, 31, 36),
                DataCollectionMock(false, 100, 110) with flagTimeableListOf(false, 100),
                DataCollectionMock(false, 130, 140) with (flagTimeableListOf(false, 130)
                        + flagTimeableListOf(true, 132, 138)),
                DataCollectionMock(true, 140, 150) with flagTimeableListOf(true, 141),
            ), toStore
        )
//        println("toStore: ")
//        toStore.forEach {
//            println("it: ${it.first} with ${it.second.map { it.time }}")
//        }
    }
//    @Test
//    fun findFileForTimeTest() {
//        val toStore: MutableList<DataCollectionContainer> = mutableListOf(
//            DataCollectionMock(false, 100, 110) with timeableListOf(102, 108),
//            DataCollectionMock(true, 140, 150) with timeableListOf(141)
//        )
//        val installer = FileInstallerMock(10L, toStore)
//        val file = installer.fileForTime(105L)
//        val fileData = DataCollectionMock(false, 100L, 110L)
//        assertEquals(fileData, file)
//    }
//
//    @Test
//    fun findNoFileForTimeTest() {
//        val toStore: MutableList<DataCollectionContainer> = mutableListOf(
//            DataCollectionMock(false, 100, 110) with timeableListOf(102, 108),
//            DataCollectionMock(true, 140, 150) with timeableListOf(141)
//        )
//        val installer = FileInstallerMock(10L, toStore)
//        val file = installer.fileForTime(205L)
//        assertNull(file)
//    }

    @Test
    fun installBeforeFailsBecauseOfOverlappingTicks() {
        val toStore: MutableList<DataCollectionContainer<SimpleTimeable>> =
            mutableListOf(
                DataCollectionMock(false, 100, 110) with timeableListOf(102, 108),
                DataCollectionMock(true, 140, 150) with timeableListOf(141)
            )
        val c = FileInstallerMock(10L, toStore)
        assertFailsWith<IndexOutOfBoundsException> { c.insert(timeableListOf(101, 103)) }
    }

    @Test
    fun installBeforeNoOverlap() {
        val toStore: MutableList<DataCollectionContainer<SimpleTimeable>> =
            mutableListOf(
                DataCollectionMock(false, 100, 110) with timeableListOf(102, 108),
                DataCollectionMock(true, 140, 150) with timeableListOf(141)
            )
        val c = FileInstallerMock(10L, toStore)
        c.insert(timeableListOf(92, 95))
        assertEquals(
            listOf(
                DataCollectionMock(false, 90, 100) with timeableListOf(92, 95),
                DataCollectionMock(false, 100, 110) with timeableListOf(102, 108),
                DataCollectionMock(true, 140, 150) with timeableListOf(141),
            ), toStore
        )
    }

    @Test
    fun installBefore() {
        val toStore: MutableList<DataCollectionContainer<SimpleTimeable>> =
            mutableListOf(
                DataCollectionMock(false, 100, 110) with timeableListOf(102, 108),
                DataCollectionMock(true, 140, 150) with timeableListOf(141)
            )
        val c = FileInstallerMock(10L, toStore)
        c.insert(timeableListOf(101, 102))
        assertEquals(
            listOf(
                DataCollectionMock(false, 100, 110) with timeableListOf(101, 102, 108),
                DataCollectionMock(true, 140, 150) with timeableListOf(141),
            ), toStore
        )
    }

    data class TimeableWithFlag(override val time: Long, val flag: Boolean) : Timeable

    fun flagTimeableListOf(flag: Boolean, vararg elements: Int) = elements.map { TimeableWithFlag(it.toLong(), flag) }

    @Test
    fun installBeforeWithReplacingEndStart() {
        val toStore: MutableList<DataCollectionContainer<TimeableWithFlag>> =
            mutableListOf(
                DataCollectionMock(false, 100, 110) with flagTimeableListOf(false, 102, 108),
                DataCollectionMock(true, 140, 150) with flagTimeableListOf(false, 141)
            )
        val c = FileInstallerMock(10L, toStore)
        c.insert(flagTimeableListOf(true, 101, 102))
        assertEquals(
            listOf(
                DataCollectionMock(false, 100, 110) with flagTimeableListOf(true, 101, 102) + TimeableWithFlag(
                    108,
                    false
                ),
                DataCollectionMock(true, 140, 150) with flagTimeableListOf(false, 141),
            ), toStore
        )
    }

    @Test
    fun installBeforeMultipleFiles() {
        val toStore: MutableList<DataCollectionContainer<SimpleTimeable>> =
            mutableListOf(
                DataCollectionMock(false, 100, 110) with timeableListOf(102, 108),
                DataCollectionMock(true, 140, 150) with timeableListOf(141)
            )
        val c = FileInstallerMock(10L, toStore)
        c.insert(timeableListOf(92, 95, 101))
        assertEquals(
            listOf(
                DataCollectionMock(false, 90, 100) with timeableListOf(92, 95),
                DataCollectionMock(false, 100, 110) with timeableListOf(101, 102, 108),
                DataCollectionMock(true, 140, 150) with timeableListOf(141),
            ), toStore
        )
    }

    @Test
    fun installBeforeMultipleFilesGap() {
        val toStore: MutableList<DataCollectionContainer<SimpleTimeable>> =
            mutableListOf(
                DataCollectionMock(false, 100, 110) with timeableListOf(102, 108),
                DataCollectionMock(true, 140, 150) with timeableListOf(141)
            )
        val c = FileInstallerMock(10L, toStore)
        c.insert(timeableListOf(82, 85, 101))
        assertEquals(
            listOf(
                DataCollectionMock(false, 80, 90) with timeableListOf(82, 85),
                DataCollectionMock(false, 100, 110) with timeableListOf(101, 102, 108),
                DataCollectionMock(true, 140, 150) with timeableListOf(141),
            ), toStore
        )
    }

    @Test
    fun installBeforeCurrent() {
        val toStore: MutableList<DataCollectionContainer<SimpleTimeable>> =
            mutableListOf(
                DataCollectionMock(true, 140, 150) with timeableListOf(141)
            )
        val c = FileInstallerMock(10L, toStore)
        c.insert(timeableListOf(92, 95, 101, 140))
        assertEquals(
            listOf(
                DataCollectionMock(false, 90, 100) with timeableListOf(92, 95),
                DataCollectionMock(false, 100, 110) with timeableListOf(101),
                DataCollectionMock(true, 140, 150) with timeableListOf(140, 141),
            ), toStore
        )
    }

    @Test
    fun installBeforeCurrentOnly() {
        val toStore: MutableList<DataCollectionContainer<SimpleTimeable>> =
            mutableListOf(
                DataCollectionMock(true, 140, 150) with timeableListOf(146)
            )
        val c = FileInstallerMock(10L, toStore)
        c.insert(timeableListOf(140, 143))
        assertEquals(
            listOf(
                DataCollectionMock(true, 140, 150) with timeableListOf(140, 143, 146),
            ), toStore
        )
    }
}
