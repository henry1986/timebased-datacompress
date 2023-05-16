package org.daiv.tick.list.readerWriter

import org.daiv.tick.*
import kotlin.test.Test
import kotlin.test.assertEquals

class StepByStepPlacerTest {
    class CloseHandler : Closeable {
        private var closed = false
        val wasClosed
            get() = closed

        override fun close() {
            closed = true
        }
    }

    class ReadStreamMock(val list: MutableList<Int>, val closeHandler: CloseHandler = CloseHandler()) : ReadStream,
        Closeable by closeHandler {
        private var closed = false
        val wasClosed
            get() = closed

        override fun readInt(): Int {
            return list.removeFirst()
        }

        override fun read(): Int {
            if(list.isEmpty()){
                return -1
            }
            return list.removeFirst()
        }

        fun IntToByteArray(data: Int): ByteArray {
            val result = ByteArray(4)
            result[0] = (data and -0x1000000 shr 24).toByte()
            result[1] = (data and 0x00FF0000 shr 16).toByte()
            result[2] = (data and 0x0000FF00 shr 8).toByte()
            result[3] = (data and 0x000000FF shr 0).toByte()
            return result
        }

        override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
            if (list.isEmpty()) {
                return -1
            }
            val got = list.removeFirst()
            val moved = IntToByteArray(got)
            (0 until 4).map { byteArray[it] = moved[it] }
            return 4
        }
    }

    class NativeDataReceiverMock(val closeHandler: CloseHandler = CloseHandler()) : NativeDataWriter,
        Closeable by closeHandler {
        val list: MutableList<Int> = mutableListOf()

        override fun writeLong(l: Long) {
            TODO("Not yet implemented")
        }

        override fun writeDouble(d: Double) {
            TODO("Not yet implemented")
        }

        override fun writeString(string: String) {
            TODO("Not yet implemented")
        }

        override fun writeInt(i: Int) {
            list.add(i)
        }

        override fun writeByte(i: Int) {
            list.add(i)
        }

        override fun flush() {
        }
    }

    class NativeDataGetterMock(val list: MutableList<Int>) : NativeData {
        override val byte: Byte
            get() = list.removeFirst().toByte()
        override val string: String
            get() = TODO("Not yet implemented")
        override val long: Long
            get() = TODO("Not yet implemented")
        override val double: Double
            get() = TODO("Not yet implemented")
        override val int: Int
            get() = list.removeFirst()
    }


    class IOStreamerGeneratorMock(private val readList: MutableList<Int>) : IOStream {
        val r = ReadStreamMock(readList)
        val writer = NativeDataReceiverMock()
        override fun readStream(): ReadStream {
            return r
        }

        override fun getNativeDataReceiver(): NativeDataWriter {
            return writer
        }


        override fun read(size: Int, d: ReadStream): NativeData {
            return NativeDataGetterMock(readList)
        }
    }

    class StreamMapperMock : StreamMapper<Int> {
        override val size: Int = 4

        override fun toOutput(t: Int, dataOutputStream: NativeDataWriter) {
            dataOutputStream.writeInt(t)
        }

        override fun toElement(byteBuffer: NativeData): Int {
            return byteBuffer.int
        }

    }

    @Test
    fun test() {
        val streamer = IOStreamerGeneratorMock(mutableListOf(5, 4, 5, 6))
        val s = StepByStepPlacer(streamer)
        val toRead = listOf(4, 6)
        s.store(StreamMapperMock(), toRead)
        assertEquals(listOf(5, 4, 5, 6), streamer.writer.list)
        val got = s.read(StreamMapperMock())
        assertEquals(toRead, got)
    }
}
