package org.daiv.tick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeReaderTest {

    class NativeDataGetterMock() : NativeDataReader<NativeDataGetterMock> {
        private val list: MutableList<Byte> = mutableListOf()
        val outer: List<Byte>
            get() = list.toList()

        override fun put(src: ByteArray, offset: Int, length: Int) {
            list.addAll(src.drop(offset).take(length))
        }

        override fun flip(): NativeDataGetterMock {
//            list.reverse()
            return this
        }
    }

    class ByteArrayMock(val list: MutableList<List<Byte>>) : ByteArrayReader {
        fun getRet(it: List<Byte>, len: Int, byteArray: ByteArray, off: Int): Int {
            it.forEachIndexed { i, b ->
                if (len <= i) {
                    return i
                }
                byteArray[off + i] = b
            }
            return it.size
        }

        override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
            return list.removeFirstOrNull()?.let {
                getRet(it, len, byteArray, off)
            } ?: -1
        }

    }

    @Test
    fun testRead() {
        val mock = NativeDataGetterMock()
        val byteArrayMock = ByteArrayMock(
            mutableListOf(
                listOf(5, 6, 1),
                listOf(5, 5, 3),
                listOf(5, 3)
            )
        )

        val read = mock.read(8, byteArrayMock)
        assertTrue(byteArrayMock.list.isEmpty())
        assertEquals(listOf(5, 6, 1, 5, 5, 3, 5, 3).map { it.toByte() }, read.outer)
    }
}