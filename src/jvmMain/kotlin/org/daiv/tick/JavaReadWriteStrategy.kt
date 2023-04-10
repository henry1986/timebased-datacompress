package org.daiv.tick

import mu.KotlinLogging
import org.daiv.util.json.log
import java.io.*
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class ByteBufferAdapter(val byteBuffer: ByteBuffer) : NativeDataGetter {
    override val long: Long
        get() = byteBuffer.long
    override val double: Double
        get() = byteBuffer.double
    override val int: Int
        get() = byteBuffer.int

    override fun put(src: ByteArray, offset: Int, length: Int) {
        byteBuffer.put(src, offset, length)
    }

    override fun flip(): ByteBufferAdapter {
        byteBuffer.flip()
        return this
    }

}

object JavaFileRefFactory : FileRefFactory {
    override fun createFile(fileName: String): FileRef {
        return JavaFileRef(File(fileName))
    }
}

class JavaFileRef(val file: File) : FileRef {
    override fun listFiles(): List<JavaFileRef> {
        return file.listFiles().map { JavaFileRef(it) }
    }

    override fun delete() {
        file.mkdirs()
    }

    override fun mkdirs() {
        file.mkdirs()
    }

    override fun exists(): Boolean {
        return file.exists()
    }

    override val fileName: String = file.name

}

class NativeOutputStreamReceiver(val dataOutputStream: DataOutputStream) : NativeDataReceiver {
    override fun writeLong(l: Long) {
        dataOutputStream.writeLong(l)
    }

    override fun writeDouble(d: Double) {
        dataOutputStream.writeDouble(d)
    }

    override fun writeInt(i: Int) {
        dataOutputStream.writeInt(i)
    }

    override fun flush() {
        dataOutputStream.flush()
    }

    override fun close() {
        dataOutputStream.close()
    }
}

fun FileRef.toJavaFile() = if(this is JavaFileRef) file else File(fileName)

class JavaInputStreams(file: File) : ReadStream {
    val d = try {
        GZIPInputStream(FileInputStream(file))
    } catch (t: FileNotFoundException) {
        throw t
    }

    val pr = DataInputStream(d)
    override fun read(byteArray: ByteArray): Int {
        return d.read(byteArray)
    }

    override fun close() {
        d.close()
    }

    override fun readInt(): Int {
        return pr.readInt()
    }
}

class JavaListReadWriteStrategyFactory<T:Any>(val mapper: StreamMapper<T>):ListReaderWriterFactory<T>{
    override fun create(fileRef: FileRef): LRWStrategy<T> {
        return JavaReadWriteStrategy(fileRef.toJavaFile(), mapper)
    }
}

class JavaReadWriteStrategy<T : Any>(val file: File, override val mapper: StreamMapper<T>) : LRWStrategy<T> {
    override val fileName: String
        get() = file.name

    override fun readStream(): ReadStream {
        return JavaInputStreams(file)
    }

    override fun getNativeDataReceiver(): NativeDataReceiver {
        return NativeOutputStreamReceiver(DataOutputStream(GZIPOutputStream(FileOutputStream(file))))
    }

    override fun getNativeDataGetter(size: Int): NativeDataGetter {
        return ByteBufferAdapter(ByteBuffer.allocate(size))
    }

    companion object {
        fun <T : Any> create(file: File, mapper: StreamMapper<T>) =
            JavaReadWriteStrategy(file, mapper)
    }
}


private fun <T : Any> readBytes(lastRead: List<TimeIntValue>, testFile: File, mapper: StreamMapper<T>) {
    val logger = KotlinLogging.logger {}
    val readBytes = JavaReadWriteStrategy.create(testFile, mapper).read()
    if (lastRead != readBytes) {
        logger.error { "readBytes are unequal, size of read: ${readBytes.size} vs ${lastRead.size}" }
        val last = readBytes.take(50)
        last.log(logger)
        if (lastRead.take(40) != readBytes.take(40)) {
            logger.error { "first are unequal ${lastRead.last()} vs ${readBytes.last()}" }
        }
    } else {
        logger.trace { "succes!" }
    }
}


