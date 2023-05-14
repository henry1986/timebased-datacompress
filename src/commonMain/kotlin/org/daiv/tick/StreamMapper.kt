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

fun Timeable.toOutput(nativeDataReceiver: NativeDataReceiver, valueGetter: () -> Int) {
    toNativeOutput(nativeDataReceiver) { writeInt(valueGetter()) }
}

fun Timeable.toNativeOutput(nativeDataReceiver: NativeDataReceiver, write: NativeDataReceiver.() -> Unit) {
    nativeDataReceiver.writeLong(time)
    nativeDataReceiver.write()
}

interface Closeable {
    fun close()
}

fun interface ByteArrayReader {
    fun read(byteArray: ByteArray, off: Int, len: Int): Int
}

interface ReadStream : Closeable, ByteArrayReader {
    fun readInt(): Int

    /**
     * returns -1, if end of stream is reached
     */
    fun read(): Int
}

interface ListReaderWriterFactory {
    val storingFile: StoringFile
    fun create(lrwCreator: IOStreamGenerator): ListReaderWriter

    object LRWStrategyFactory : ListReaderWriterFactory {
        override val storingFile: StoringFile = WithRead

        override fun create(lrwCreator: IOStreamGenerator): ListReaderWriter {
            return LRWStrategy(lrwCreator)
        }
    }

    object StepByStepFactory : ListReaderWriterFactory {
        override val storingFile: StoringFile = WithoutRead

        override fun create(lrwCreator: IOStreamGenerator): ListReaderWriter {
            return StepByStepPlacer(lrwCreator)
        }
    }
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

interface StreamMapperHolder<T> {
    val mapper: StreamMapper<T>
}

interface IOStreamGenerator {
    fun readStream(): ReadStream
    fun getNativeDataReceiver(): NativeDataReceiver
    fun getNativeDataGetter(size: Int): NativeDataGetter
}

interface IOStreamGeneratorFactory {
    fun create(fileRef: FileRef, withCompression: Boolean): IOStreamGenerator
}


class StepByStepPlacer(creator: IOStreamGenerator) : ListReaderWriter, IOStreamGenerator by creator {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun <T> store(mapper: StreamMapper<T>, ticks: List<T>) {
        val d = getNativeDataReceiver()
        ticks.forEach {
            d.writeByte(5)
            d.writeInt(mapper.size)
            mapper.toOutput(it, d)
        }
        d.flush()
        d.close()
    }

    private fun read(size: Int, d: ReadStream): NativeDataGetter {
        return getNativeDataGetter(size).read(size, d)
    }

    private fun <T> readFlexibleSizedData(mapper: StreamMapper<out T>): List<T> {
        val d = readStream()

        val ticks = mutableListOf<T>()
        while (d.read() != -1) {
            val stringLength = try {
                d.readInt()
            } catch (t: Throwable) {
                logger.error { "could not read anymore, currently read: $ticks" }
                break
            }
            val x = mapper.size + stringLength
            ticks.add(mapper.toElement(read(x, d)))
        }
        d.close()
        return ticks
    }

    override fun <T> read(mapper: StreamMapper<out T>): List<T> {
        if (mapper is FlexibleStreamMapper) {
            return readFlexibleSizedData(mapper)
        }
        val d = readStream()
        val mutableList = mutableListOf<T>()
        while (d.read() != -1) {
            val size = d.readInt()
            val e = try {
                mapper.toElement(getNativeDataGetter(size).read(size, d))
            } catch (t: Throwable) {
                throw RuntimeException("error with $",t)
            }
            mutableList.add(e)
        }
        d.close()
        return mutableList.toList()
    }
}

class LRWStrategy(creator: IOStreamGenerator) : ListReaderWriter, IOStreamGenerator by creator {

    override fun <T> store(mapper: StreamMapper<T>, ticks: List<T>) {
        val d = getNativeDataReceiver()
        d.writeInt(ticks.size)
        ticks.forEach { mapper.toOutput(it, d) }
        d.flush()
        d.close()
    }

    private fun read(size: Int, d: ReadStream): NativeDataGetter {
        return getNativeDataGetter(size).read(size, d)
    }

    private fun <T> readFlexibleSizedData(mapper: StreamMapper<out T>): List<T> {
        val d = readStream()
        val maxEntrys = d.readInt()

        val ticks = mutableListOf<T>()
        for (i in 1..maxEntrys) {
            val stringLength = try {
                d.readInt()
            } catch (t: Throwable) {
                logger.error { "could not read anymore, currently read: $ticks" }
                break
            }
            val x = mapper.size + stringLength
            ticks.add(mapper.toElement(read(x, d)))
        }
        d.close()
        return ticks
    }

    override fun <T> read(mapper: StreamMapper<out T>): List<T> {
        if (mapper is FlexibleStreamMapper) {
            return readFlexibleSizedData(mapper)
        }
        val d = readStream()
        val size = d.readInt() * mapper.size
//        logger.trace { "file: $fileName size: $size" }
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


//class NewFileDataReader<T : Timeable>(val strategy: ListReaderWriterFactory<T>) :
//    ReadTimeable<T, DataCollectionWithFileRef> {
//    override fun read(fileData: DataCollectionWithFileRef): List<T> {
//        return strategy.create(fileData.file).read()
//    }
//}

interface ListReaderWriter {
    fun <T> store(mapper: StreamMapper<T>, ticks: List<T>)
    fun <T> read(mapper: StreamMapper<out T>): List<T>
}
