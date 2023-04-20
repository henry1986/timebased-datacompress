package org.daiv.tick

import mu.KotlinLogging

/**
 * Interface for mapping a data object to and from a stream of bytes.
 *
 * @param T the type of object being mapped.
 */
interface StreamMapper<T> {
    /**
     * Returns the size of the byte representation of an object of type T.
     */
    val size: Int

    /**
     * Writes an object of type T to a data output stream.
     *
     * [t] the object to be written.
     * [dataOutputStream] the data output stream to write the object to.
     */
    fun toOutput(t: T, dataOutputStream: NativeDataReceiver)

    /**
     * Reads an object of type T from a byte buffer.
     *
     * @param byteBuffer the byte buffer to read the object from.
     * @return the object read from the byte buffer.
     */
    fun toElement(byteBuffer: NativeDataGetter): T
}
//interface StreamMapper<T> {
//    val size: Int
//
//    fun toOutput(t: T, dataOutputStream: NativeDataReceiver)
//    fun toElement(byteBuffer: NativeDataGetter): T
//}

interface Endingable {
    val ending: String
}

interface EndingStreamMapper<T> : StreamMapper<T>, Endingable {
}

interface FlexibleStreamMapper<T> : EndingStreamMapper<T> {
    fun readSize(byteBuffer: NativeDataGetter): Int
}

interface TValueable<T> {
    val value: T
}

data class Header(val header: List<String>, override val name: String) : Nameable {
    fun toName(): String {
        return header.joinToString(".") + "." + name
    }
}

interface Headerable {
    val header: Header
}

data class Datapoint<T>(override val header: Header, override val time: Long, override val value: T) : Timeable,
    TValueable<T>, Headerable

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

interface StreamerFactory<T> : Endingable {
    fun streamer(name: Header): EndingStreamMapper<T>

    companion object {
        val streamer: List<StreamerFactory<out Datapoint<*>>> =
            listOf(StringStreamerFactory, BooleanStreamMapperFactory, IntStreamerFactory, DoubleStreamerFactory, LongStreamerFactory)
        val endingMap: Map<String, StreamerFactory<out Datapoint<*>>> = streamer.associateBy { it.ending }
    }
}

object StringStreamerFactory : StreamerFactory<Datapoint<String>> {
    override val ending: String = "dpString"

    override fun streamer(name: Header): EndingStreamMapper<Datapoint<String>> {
        return StringStreamer(name)
    }
}

interface EnumStreamerBuilder<T : Enum<T>> : Endingable, StreamerFactory<Datapoint<T>> {
    companion object {
        fun <T : Enum<T>> enumStreamer(name: Header, ending: String, enumFactory: (Int) -> T) =
            GenericStreamMapper(name, 4, ending, { writeInt(it.value.ordinal) }) { enumFactory(int) }
    }
}

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

fun Timeable.toOutput(nativeDataReceiver: NativeDataReceiver, valueGetter: () -> Int) {
    toNativeOutput(nativeDataReceiver) { writeInt(valueGetter()) }
}

fun Timeable.toNativeOutput(nativeDataReceiver: NativeDataReceiver, write: NativeDataReceiver.() -> Unit) {
    nativeDataReceiver.writeLong(time)
    nativeDataReceiver.write()
}

interface ReadStream {
    fun read(byteArray: ByteArray): Int
    fun close()
    fun readInt(): Int
}

interface ListReaderWriterFactory<T : Any> {
    fun create(fileRef: FileRef): ListReaderWriter<T>
}

interface FileNameable {
    val fileName: String
}

interface FileRef : FileNameable {
    val absolutePath: String
    fun listFiles(): List<FileRef>
    fun delete()
    fun mkdirs()
    fun exists(): Boolean
}

interface LRWStrategyFactory {
    fun <T> create(file: FileRef, mapper: StreamMapper<T>): LRWStrategy<T>
}

interface LRWStrategy<T> : FileNameable, ListReaderWriter<T> {
    val mapper: StreamMapper<T>
    fun readStream(): ReadStream
    fun getNativeDataReceiver(): NativeDataReceiver
    fun getNativeDataGetter(size: Int): NativeDataGetter
    override fun store(ticks: List<T>) {
        val d = getNativeDataReceiver()
        d.writeInt(ticks.size)
        ticks.forEach { mapper.toOutput(it, d) }
        d.flush()
        d.close()
    }

    private fun read(size: Int, d: ReadStream): NativeDataGetter {
        val buffer = getNativeDataGetter(size)
        val bytes = ByteArray(size)
        do {
            var read = d.read(bytes)
            try {
                buffer.put(bytes, 0, read)
            } catch (t: Throwable) {
                throw t
            }
        } while (read != -1 && read != size)
        buffer.flip()
        return buffer
    }

    private fun readFlexibleSizedData(): List<T> {
        val mapper = mapper as FlexibleStreamMapper
        val d = readStream()
        val maxEntrys = d.readInt()

        val ticks = mutableListOf<T>()
        for (i in 1..maxEntrys) {
            val stringLength = d.readInt()
            val x = mapper.size + stringLength
            ticks.add(mapper.toElement(read(x, d)))
        }
        d.close()
        return ticks
    }

    override fun read(): List<T> {

        if (mapper is FlexibleStreamMapper) {
            return readFlexibleSizedData()
        }
        val d = readStream()
        val size = d.readInt() * mapper.size
        logger.trace { "file: $fileName size: $size" }
        val buffer = read(size, d)

        val ticks = mutableListOf<T>()
        for (i in 1..(size / mapper.size)) {
            ticks.add(mapper.toElement(buffer))
        }
        d.close()
        return ticks
    }


    companion object {
        private val logger = KotlinLogging.logger { }
    }
}


class NewFileDataReader<T : Timeable>(val strategy: ListReaderWriterFactory<T>) :
    ReadTimeable<T, DataCollectionWithFileRef> {
    override fun read(fileData: DataCollectionWithFileRef): List<T> {
        return strategy.create(fileData.file).read()
    }
}

interface ListReaderWriter<T> {
    fun store(ticks: List<T>)
    fun read(): List<T>
}
