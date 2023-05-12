package org.daiv.tick.streamer

import org.daiv.tick.*

class StringStreamer(val name: Header) : FlexibleStreamMapper<Datapoint<String>> {
    override val size: Int = 8

    override val ending: String = StringStreamerFactory.ending

    override fun toOutput(t: Datapoint<String>, dataOutputStream: NativeDataReceiver) {
        dataOutputStream.writeInt(t.value.length)
        dataOutputStream.writeLong(t.time)
        dataOutputStream.writeString(t.value)
    }

    override fun readSize(byteBuffer: NativeDataGetter): Int {
        return byteBuffer.int
    }

    override fun toElement(byteBuffer: NativeDataGetter): Datapoint<String> {
        return Datapoint(name, byteBuffer.long, byteBuffer.string)
    }

}

object StringStreamerFactory : StreamerFactory<Datapoint<String>> {
    override val ending: String = "dpString"

    override fun streamer(name: Header): EndingStreamMapper<Datapoint<String>> {
        return StringStreamer(name)
    }
}
