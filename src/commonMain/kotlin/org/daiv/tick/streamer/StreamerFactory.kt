package org.daiv.tick.streamer

import org.daiv.tick.*

interface StreamerFactory<T> : Endingable {
    fun streamer(name: Header): EndingStreamMapper<T>

    companion object {
        val streamer: List<StreamerFactory<out Datapoint<*>>> =
            listOf(
                StringStreamerFactory,
                BooleanStreamMapperFactory,
                IntStreamerFactory,
                DoubleStreamerFactory,
                LongStreamerFactory
            )
        val endingMap: Map<String, StreamerFactory<out Datapoint<*>>> = streamer.associateBy { it.ending }
    }
}

class GenericStreamMapper<T>(
    val header: Header,
    addSize: Int,
    override val ending: String,
    val write: NativeDataReceiver.(Datapoint<T>) -> Unit,
    val getT: NativeDataGetter.() -> T
) : EndingStreamMapper<Datapoint<T>> {
    override val size: Int = 8 + addSize

    override fun toOutput(t: Datapoint<T>, dataOutputStream: NativeDataReceiver) {
        t.toNativeOutput(dataOutputStream) { write(t) }
    }

    override fun toElement(byteBuffer: NativeDataGetter): Datapoint<T> {
        return Datapoint(header, byteBuffer.long, byteBuffer.getT())
    }
}

object BooleanStreamMapperFactory : StreamerFactory<Datapoint<Boolean>> {
    override val ending: String = "dpBoolean"
    override fun streamer(name: Header): EndingStreamMapper<Datapoint<Boolean>> {
        return GenericStreamMapper(name, 1, ending, { writeByte(if (it.value) 1 else 0) }, { byte.toInt() != 0 })
    }
}


object DoubleStreamerFactory : StreamerFactory<Datapoint<Double>> {
    override val ending: String = "dpDouble"
    override fun streamer(name: Header): EndingStreamMapper<Datapoint<Double>> {
        return GenericStreamMapper(name, 8, ending, { writeDouble(it.value) }, { double })
    }
}

object LongStreamerFactory : StreamerFactory<Datapoint<Long>> {
    override val ending: String = "dpLong"
    override fun streamer(name: Header): EndingStreamMapper<Datapoint<Long>> {
        return GenericStreamMapper(name, 8, ending, { writeLong(it.value) }, { long })
    }
}

object IntStreamerFactory : StreamerFactory<Datapoint<Int>> {
    override val ending: String = "dpInt"
    override fun streamer(name: Header): EndingStreamMapper<Datapoint<Int>> {
        return GenericStreamMapper(name, 4, ending, { writeInt(it.value) }) { int }
    }
}

interface EnumStreamerBuilder<T : Enum<T>> : Endingable, StreamerFactory<Datapoint<T>> {
    companion object {
        fun <T : Enum<T>> enumStreamer(name: Header, ending: String, enumFactory: (Int) -> T) =
            GenericStreamMapper(name, 4, ending, { writeInt(it.value.ordinal) }) { enumFactory(int) }
    }
}
