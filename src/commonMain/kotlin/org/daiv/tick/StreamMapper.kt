package org.daiv.tick

import mu.KotlinLogging

interface NativeDataReceiver {
    fun writeLong(l: Long)
    fun writeDouble(d: Double)
    fun writeInt(i: Int)
    fun flush()
    fun close()
}

interface NativeDataGetter {
    val long: Long
    val double: Double
    val int: Int
    fun put(src: ByteArray, offset: Int, length: Int)
    fun flip(): NativeDataGetter
}

interface StreamMapper<T : Any> {
    val size: Int

    fun toOutput(t: T, dataOutputStream: NativeDataReceiver)
    fun toElement(byteBuffer: NativeDataGetter): T
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

data class TimeIntValue(override val time: Long, val value: Int) : Timeable
data class TimeDoubleValue(override val time: Long, val value: Double) : Timeable
data class TimeEnumValue<T : Enum<T>>(override val time: Long, val value: T) : Timeable

fun Timeable.toOutput(nativeDataReceiver: NativeDataReceiver, valueGetter: () -> Int) {
    nativeDataReceiver.writeLong(time)
    nativeDataReceiver.writeInt(valueGetter())
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
    fun listFiles(): List<FileRef>
    fun delete()
    fun mkdirs()
    fun exists(): Boolean
}

interface LRWStrategy<T : Any> : FileNameable, ListReaderWriter<T> {
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

    override fun read(): List<T> {
        val d = readStream()
        val pr = d
        val size = pr.readInt() * mapper.size
        logger.trace { "file: $fileName size: $size" }
        val buffer = getNativeDataGetter(size)
        val ticks = mutableListOf<T>()
        val bytes = ByteArray(size)
        var read = 0
        read = d.read(bytes)
        while (read != -1) {
            try {
                buffer.put(bytes, 0, read)
            } catch (t: Throwable) {
                throw t
            }
            read = d.read(bytes)
        }
        buffer.flip()
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
interface FileRefable{
    val file:FileRef
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



interface DataCollectionWithFileRef:CurrentDataCollection, FileRefable

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
