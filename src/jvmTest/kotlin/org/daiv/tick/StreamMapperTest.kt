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


class StreamMapperTest : StoreStreamTest() {

    @Test
    fun timeInt() {
        TimeIntValueMapper.testMapper(TimeIntValue(5L, 9))
    }
}
