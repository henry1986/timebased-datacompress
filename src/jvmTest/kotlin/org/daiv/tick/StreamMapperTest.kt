package org.daiv.tick

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import kotlin.test.assertEquals

open class StoreStreamTest {

    fun <T : Timeable> StreamMapper<T>.testMapper(t: T) {
        val baos = ByteArrayOutputStream()
        val data = DataOutputStream(baos)
        toOutput(t, NativeOutputStreamReceiver(data))
        val bytes = baos.toByteArray()
        val buffer = ByteBuffer.wrap(bytes)
//                         buffer.flip()
        val element = toElement(ByteBufferAdapter(buffer))
        assertEquals(size, buffer.position(), "byte size of storedElement")
        assertEquals(t, element)
    }
}

data class TimeIntValue(override val time:Long, val value:Int):Timeable

object TimeIntValueMapper:StreamMapper<TimeIntValue>{
    override val size: Int = Long.SIZE_BYTES + Int.SIZE_BYTES

    override fun toOutput(t: TimeIntValue, dataOutputStream: NativeDataWriter) {
        dataOutputStream.writeLong(t.time)
        dataOutputStream.writeInt(t.value)
    }

    override fun toElement(byteBuffer: NativeData): TimeIntValue {
        val time = byteBuffer.long
        val value = byteBuffer.int
        return TimeIntValue(time, value)
    }
}


class StreamMapperTest : StoreStreamTest() {

    @Test
    fun timeInt() {
        TimeIntValueMapper.testMapper(TimeIntValue(5L, 9))
    }
}

data class TimeBooleanValue(val time: Long, val value: Boolean)

object TimeBooleanValueMapper : StreamMapper<TimeBooleanValue> {
    override val size: Int = 9

    override fun toOutput(t: TimeBooleanValue, dataOutputStream: NativeDataWriter) {
        dataOutputStream.writeLong(t.time)
        dataOutputStream.writeByte(if(t.value) 1 else 0)
    }

    override fun toElement(byteBuffer: NativeData): TimeBooleanValue {
        val time = byteBuffer.long
        val value = byteBuffer.byte.toInt() == 1
        return TimeBooleanValue(time, value)
    }
}



