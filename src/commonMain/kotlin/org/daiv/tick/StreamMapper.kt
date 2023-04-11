package org.daiv.tick

import mu.KotlinLogging

interface NativeDataReceiver {
    fun writeLong(l: Long)
    fun writeDouble(d: Double)
    fun writeString(string: String)
    fun writeInt(i: Int)
    fun writeByte(i: Int)
    fun flush()
    fun close()
}

interface NativeDataGetter {
    val byte: Byte
    val string: String
    val long: Long
    val double: Double
    val int: Int
    val position: Int
    val array: ByteArray
    fun put(src: ByteArray, offset: Int, length: Int)
    fun flip(): NativeDataGetter
}

interface StreamMapper<T> {
    val size: Int

    fun toOutput(t: T, dataOutputStream: NativeDataReceiver)
    fun toElement(byteBuffer: NativeDataGetter): T
}

interface FlexibleStreamMapper<T> : StreamMapper<T> {
    fun readSize(byteBuffer: NativeDataGetter): Int
}


class TimeEnumValueMapper<T : Enum<T>>(val factory: (Int) -> T) : StreamMapper<TimeEnumValue<T>> {
    override val size = 8 + 4 // Long + Int
    override fun toOutput(storedTick: TimeEnumValue<T>, dataOutputStream: NativeDataReceiver) =
        storedTick.toOutput(dataOutputStream)

    override fun toElement(byteBuffer: NativeDataGetter): TimeEnumValue<T> = byteBuffer.toTimeEnumValue(factory)
}

object TimeIntValueMapper : StreamMapper<TimeIntValue> {
    override val size = 8 + 4 // Long + Int
    override fun toOutput(storedTick: TimeIntValue, dataOutputStream: NativeDataReceiver) =
        storedTick.toOutput(dataOutputStream) { storedTick.value }

    override fun toElement(byteBuffer: NativeDataGetter): TimeIntValue = byteBuffer.toTimeIntValue()
}

object TimeDoubleValueMapper : StreamMapper<TimeDoubleValue> {
    override val size = 8 + 8 // Long + Int
    override fun toOutput(timeDoubleValue: TimeDoubleValue, dataOutputStream: NativeDataReceiver) =
        timeDoubleValue.toNativeOutput(dataOutputStream) { writeDouble(timeDoubleValue.value) }

    override fun toElement(byteBuffer: NativeDataGetter): TimeDoubleValue = byteBuffer.toTimeDoubleValue()
}

interface TValueable<T> {
    val value: T
}

data class Datapoint<T>(override val name: String, override val time: Long, override val value: T) : Timeable,
    TValueable<T>, Nameable

class GenericStreamMapper<T>(
    val name: String,
    addSize: Int,
    val write: NativeDataReceiver.(Datapoint<T>) -> Unit,
    val getT: NativeDataGetter.() -> T
) : StreamMapper<Datapoint<T>> {
    override val size: Int = 8 + addSize

    override fun toOutput(t: Datapoint<T>, dataOutputStream: NativeDataReceiver) {
        t.toNativeOutput(dataOutputStream) { write(t) }
    }

    override fun toElement(byteBuffer: NativeDataGetter): Datapoint<T> {
        return Datapoint(name, byteBuffer.long, byteBuffer.getT())
    }
}

fun booleanStream(name: String) = GenericStreamMapper(name, 1, { writeByte(it.value.toInt()) }, { byte })
fun doubleStream(name: String) = GenericStreamMapper(name, 8, { writeDouble(it.value) }, { double })
interface StreamerFactory<T> {
    fun streamer(name: String): StreamMapper<T>
}

object StringStreamerFactory : StreamerFactory<Datapoint<String>> {
    override fun streamer(name: String): StreamMapper<Datapoint<String>> {
        return StringStreamer(name)
    }
}

class StringStreamer(val name: String) : FlexibleStreamMapper<Datapoint<String>> {
    override val size: Int = 8

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

interface CurrentDateGetter {
    fun currentDate(): String
}

class WriteData(
    val lRWStrategy: LRWStrategyFactory,
    val fFactory: FileRefFactory,
    val currentDateGetter: CurrentDateGetter,
    val mainDir: FileRef
) {
    fun <R> write(streamerFactory: StreamerFactory<Datapoint<R>>, datapoint: Datapoint<R>) {
        write(streamerFactory.streamer(datapoint.name), datapoint)
    }

    fun <R> write(streamMapper: StreamMapper<Datapoint<R>>, datapoint: Datapoint<R>) {
        val dir = fFactory.createFile(mainDir, currentDateGetter.currentDate())
        dir.mkdirs()
        val file = fFactory.createFile(dir, datapoint.name)
        val strategy = lRWStrategy.create(file, streamMapper)
        val read = strategy.read()
        strategy.store(read + datapoint)
//        strategy.store(listOf(datapoint))
    }
}


data class TimeIntValue(override val time: Long, val value: Int) : Timeable
data class TimeDoubleValue(override val time: Long, val value: Double) : Timeable
data class TimeEnumValue<T : Enum<T>>(override val time: Long, val value: T) : Timeable

fun Timeable.toOutput(nativeDataReceiver: NativeDataReceiver, valueGetter: () -> Int) {
    toNativeOutput(nativeDataReceiver) { writeInt(valueGetter()) }
}

fun Timeable.toNativeOutput(nativeDataReceiver: NativeDataReceiver, write: NativeDataReceiver.() -> Unit) {
    nativeDataReceiver.writeLong(time)
    nativeDataReceiver.write()
}

fun <T : Enum<T>> TimeEnumValue<T>.toOutput(nativeDataReceiver: NativeDataReceiver) =
    toOutput(nativeDataReceiver) { value.ordinal }

fun TimeDoubleValue.toOutput(nativeDataReceiver: NativeDataReceiver) {
    nativeDataReceiver.writeLong(time)
    nativeDataReceiver.writeDouble(value)
}

fun <T : Enum<T>> NativeDataGetter.toTimeEnumValue(factory: (Int) -> T): TimeEnumValue<T> {
    return TimeEnumValue(long, factory(int))
}

fun NativeDataGetter.toTimeIntValue(): TimeIntValue {
    return TimeIntValue(long, int)
}

fun NativeDataGetter.toTimeDoubleValue(): TimeDoubleValue {
    return TimeDoubleValue(long, double)
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

interface FileRefable {
    val file: FileRef
}

data class FileDataInfo<out T>(
    override val start: Long,
    override val end: Long,
    override val isCurrent: Boolean,
    val folderFile: T
) : CurrentDataCollection

fun <T : FileRefable> FileDataInfo<T>.toWithRef() = FileDataInfoWithRef(start, end, isCurrent, folderFile)

data class FileDataInfoWithRef<out T : FileRefable>(
    override val start: Long,
    override val end: Long,
    override val isCurrent: Boolean,
    val folderFile: T
) : DataCollectionWithFileRef, FileRefable by folderFile


interface DataCollectionWithFileRef : CurrentDataCollection, FileRefable

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
