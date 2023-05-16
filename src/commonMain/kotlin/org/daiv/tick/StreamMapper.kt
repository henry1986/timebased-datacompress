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
