package org.daiv.tick

import mu.KotlinLogging
import org.daiv.time.isoTime
import org.daiv.util.json.log
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.xml.crypto.Data

class ByteBufferAdapter(val byteBuffer: ByteBuffer) : NativeDataGetter {
    override val byte: Byte
        get() = byteBuffer.get()
    override val string:String
        get() {
            return String(byteBuffer.array().drop(byteBuffer.position()).toByteArray())
        }
    override val long: Long
        get() = byteBuffer.long
    override val double: Double
        get() = byteBuffer.double
    override val int: Int
        get() = byteBuffer.int

    override val position
        get() = byteBuffer.position()

    override val array
        get() = byteBuffer.array()

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

    override fun createFile(dir: FileRef, fileName: String): FileRef {
        return JavaFileRef(File("${dir.absolutePath}/$fileName"))
    }
}

class JavaFileRef(val file: File) : FileRef {

    override val absolutePath: String
        get() = file.absolutePath

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

    override fun writeString(string: String) {
        dataOutputStream.writeBytes(string)
//        OutputStreamWriter(dataOutputStream, StandardCharsets.UTF_8).write(string)
    }

    override fun writeInt(i: Int) {
        dataOutputStream.writeInt(i)
    }

    override fun writeByte(i: Int) {
        dataOutputStream.writeByte(i)
    }

    override fun flush() {
        dataOutputStream.flush()
    }

    override fun close() {
        dataOutputStream.close()
    }
}

fun FileRef.toJavaFile() = if (this is JavaFileRef) file else File(fileName)

class JavaInputStreams(file: FileRef) : ReadStream {
    val d = try {
        GZIPInputStream(FileInputStream(file.toJavaFile()))
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

class JavaListReadWriteStrategyFactory<T : Any>(val mapper: StreamMapper<T>) : ListReaderWriterFactory<T> {
    override fun create(fileRef: FileRef): LRWStrategy<T> {
        return JavaReadWriteStrategy(fileRef, mapper)
    }
}

object JavaLRWSFactory : LRWStrategyFactory {
    override fun <T> create(file: FileRef, mapper: StreamMapper<T>): LRWStrategy<T> {
        return JavaReadWriteStrategy(file, mapper)
    }
}

class JavaReadWriteStrategy<T>(val file: FileRef, override val mapper: StreamMapper<T>) : LRWStrategy<T>,
    FileNameable by file {

    override fun readStream(): ReadStream {
        return JavaInputStreams(file)
    }

    override fun getNativeDataReceiver(): NativeDataReceiver {
        return NativeOutputStreamReceiver(DataOutputStream(GZIPOutputStream(FileOutputStream(file.toJavaFile()))))
    }

    override fun getNativeDataGetter(size: Int): NativeDataGetter {
        return ByteBufferAdapter(ByteBuffer.allocate(size))
    }

    companion object {
        fun <T : Any> create(file: FileRef, mapper: StreamMapper<T>) =
            JavaReadWriteStrategy(file, mapper)
    }
}


private fun <T : Any> readBytes(lastRead: List<TimeIntValue>, testFile: FileRef, mapper: StreamMapper<T>) {
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


val dp = Datapoint("cp1.state", 5L, 9)

object JavaCurrentDataGetter : CurrentDateGetter {
    override fun currentDate(): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now())
    }
}


fun main() {
    val w = WriteData(JavaLRWSFactory, JavaFileRefFactory, JavaCurrentDataGetter, JavaFileRefFactory.createFile("main"))
    w.write(StringStreamerFactory, Datapoint("sState", 5L, "HelloWorld"))
//    val dir = File("main/2023-04-11/")
//    dir.mkdirs()
//    val file = File("main/2023-04-11/sState")
//    val dout = DataOutputStream(GZIPOutputStream(FileOutputStream(file)))
//    val string = "Hello Wolr"
//    dout.writeBytes(string)
//    dout.close()
//    val d = try {
//        GZIPInputStream(FileInputStream(file))
//    } catch (t: FileNotFoundException) {
//        throw t
//    }
//    val size = string.length
//    val b = ByteArray(size)
//    val x = d.read(b)
//    println("x: $x")
//    println("b: ${String(b)}")

//    val fileFactory = JavaFileRefFactory
//    val mainDir = fileFactory.createFile("main")
//    mainDir.listFiles().forEach {
//        it.listFiles().forEach {
//            println("list: ${it.absolutePath}")
//        }
//    }
//    val dir = fileFactory.createFile(mainDir, JavaCurrentDataGetter.currentDate())
//    dir.mkdirs()
//    val f = fileFactory.createFile(dir, "state.dp")
//    val rw = JavaReadWriteStrategy(f, TimeIntValueMapper)
//    val all = rw.read()
//    rw.store(
//        all + listOf(
//            TimeIntValue(5L, 9),
//        )
//    )
//    val read = rw.read()
//    read.forEach {
//        println("it: $it")
//    }
}

