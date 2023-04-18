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
import java.util.stream.LongStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.xml.crypto.Data

class ByteBufferAdapter(val byteBuffer: ByteBuffer) : NativeDataGetter {
    override val byte: Byte
        get() = byteBuffer.get()
    override val string: String
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
        val f = File("${dir.absolutePath}/$fileName")
        return JavaFileRef(f)
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


//private fun <T : Any> readBytes(lastRead: List<TimeIntValue>, testFile: FileRef, mapper: StreamMapper<T>) {
//    val logger = KotlinLogging.logger {}
//    val readBytes = JavaReadWriteStrategy.create(testFile, mapper).read()
//    if (lastRead != readBytes) {
//        logger.error { "readBytes are unequal, size of read: ${readBytes.size} vs ${lastRead.size}" }
//        val last = readBytes.take(50)
//        last.log(logger)
//        if (lastRead.take(40) != readBytes.take(40)) {
//            logger.error { "first are unequal ${lastRead.last()} vs ${readBytes.last()}" }
//        }
//    } else {
//        logger.trace { "succes!" }
//    }
//}

object JavaCurrentDataGetter : CurrentDateGetter {
    override fun currentDate(): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now())
    }
}

fun JavaWriteDataFactory(fileName: String) =
    WriteData(JavaLRWSFactory, JavaFileRefFactory, JavaCurrentDataGetter, JavaFileRefFactory.createFile(fileName))


enum class TestWrite {
    W1, W2;

    companion object : StreamerFactory<Datapoint<TestWrite>> {
        override val ending: String = "dpTestWrite"
        override fun streamer(name: Header): EndingStreamMapper<Datapoint<TestWrite>> {
            return EnumStreamerBuilder.enumStreamer(name, ending) { values()[it] }
        }
    }
}

fun WriteData.writeInt(header: Header, value: Int, time: Long) =
    write(LogDP(IntStreamerFactory, header, value), time)

//fun WriteData.writeDouble(header: Header, value: Double) =
//    write(LogDP(DoubleStreamerFactory, header, value))
//
//fun WriteData.writeLong(header: Header, value: Long) =
//    write(LogDP(LongStreamerFactory, header, value))
//
fun <T : Enum<T>> WriteData.writeEnum(s: StreamerFactory<Datapoint<T>>, header: Header, value: T, time: Long) =
    write(LogDP(s, header, value), time)


fun WriteData.write(logDP: LogDP<*>, time:Long) {
    logDP.write(this, time)
}

fun WriteData.writeString(header: Header, value: String, time: Long) =
    write(LogDP(StringStreamerFactory, header, value), time)


fun main() {
    val w = JavaWriteDataFactory("main")
    w.writeString(Header(listOf("uesa", "cp1"), "sState"), "HelloWorld", 5L)
    w.writeInt(Header(listOf("uesa", "cp1"), "cpState"), 9, 5L)
    w.writeEnum(TestWrite, Header(listOf("uesa", "cp1"), "testWrite"), TestWrite.W1, 5L)
    val got = w.readDataPoints(StreamerFactory.streamer + TestWrite)
    got.forEach {
        println("it: $it")
    }
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

