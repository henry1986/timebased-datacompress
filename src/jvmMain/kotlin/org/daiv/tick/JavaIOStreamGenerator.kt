package org.daiv.tick

import org.daiv.tick.streamer.EnumStreamerBuilder
import org.daiv.tick.streamer.IntStreamerFactory
import org.daiv.tick.streamer.StreamerFactory
import org.daiv.tick.streamer.StringStreamerFactory
import java.io.*
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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

    /**
     * returns this buffer limit
     */
    override val limit: Int
        get() = byteBuffer.limit()

    override fun put(src: ByteArray, offset: Int, length: Int) {
        try {
            byteBuffer.put(src, offset, length)
        } catch (t: Throwable) {
            throw RuntimeException(
                "put failed $src, $offset, $length, while ${byteBuffer.position()} and ${byteBuffer.limit()}",
                t
            )
        }
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

class JavaInputStreams(val file: FileRef, val withCompression: Boolean) : ReadStream {
    fun create(): FileInputStream {
        return FileInputStream(file.toJavaFile())
    }

    val d = try {
        if (withCompression) GZIPInputStream(create()) else create()
    } catch (t: FileNotFoundException) {
        throw t
    }

    val pr = DataInputStream(d)
    override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
        return d.read(byteArray, off, len)
    }

    override fun close() {
        d.close()
    }

    override fun readInt(): Int {
        pr.read()
        return pr.readInt()
    }

    override fun read(): Int {
        return pr.read()
    }
}

object JavaIOStreamGeneratorFactory:IOStreamGeneratorFactory{
    override fun create(fileRef: FileRef, withCompression: Boolean): IOStreamGenerator {
        return JavaIOStreamGenerator(fileRef, withCompression)
    }

}

class JavaIOStreamGenerator(
    val file: FileRef,
    val withCompression: Boolean
) : IOStreamGenerator, FileNameable by file {

    override fun readStream(): ReadStream {
        return JavaInputStreams(file, withCompression)
    }

    private fun create(): OutputStream {
        return FileOutputStream(file.toJavaFile(), true)
    }

    override fun getNativeDataReceiver(): NativeDataReceiver {
        return NativeOutputStreamReceiver(
            DataOutputStream(
                if (withCompression) GZIPOutputStream(create()) else create()
            )
        )
    }

    override fun getNativeDataGetter(size: Int): NativeDataGetter {
        return ByteBufferAdapter(ByteBuffer.allocate(size))
    }

    companion object {
        fun <T : Any> create(file: FileRef, mapper: StreamMapper<T>, withCompression: Boolean) =
            JavaIOStreamGenerator(file, withCompression)
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

fun JavaWriteDataFactory(
    fileName: String,
    withCompression: Boolean,
    listReaderWriterFactory: ListReaderWriterFactory,
): WriteData {
    val file = JavaFileRefFactory.createFile(fileName)
    return WriteData(
        listReaderWriterFactory,
        JavaIOStreamGeneratorFactory,
        JavaFileRefFactory,
        JavaCurrentDataGetter,
        file,
        withCompression,
        listReaderWriterFactory.storingFile
    )
}


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


fun WriteData.write(logDP: LogDP<*>, time: Long) {
    logDP.write(this, time)
}

fun WriteData.writeString(header: Header, value: String, time: Long) =
    write(LogDP(StringStreamerFactory, header, value), time)


private fun writeLoop(w: WriteData, time: Long) {
//    w.writeString(Header(listOf("uesa", "cp1"), "sState"), "HelloWorld", time)
//    w.writeString(Header(listOf("cp1"), "cp1.- EV CP-state resolved"), "resolvedValue", time)
//    w.writeString(Header(listOf("cp2"), "cp2.- EV CP-state resolved"), "resolvedValue", time)
//    w.writeString(Header(listOf("cp3"), "cp3.- EV CP-state resolved"), "resolvedValue", time)
    w.writeInt(Header(listOf("uesa", "cp1"), "cpState"), 9, time)
//    w.writeEnum(TestWrite, Header(listOf("uesa", "cp1"), "testWrite"), TestWrite.W1, time)
}

private fun writeTest(storingFile: StoringFile) {
    val w: WriteData = JavaWriteDataFactory("main", false, ListReaderWriterFactory.LRWStrategyFactory)
    writeLoop(w, 5L)
    val got = w.readDataPoints(StreamerFactory.streamer + TestWrite)
    got.forEach {
        println("it: $it")
    }
}

private fun writeMultipleTimes() {
    val w: WriteData = JavaWriteDataFactory("main", false, ListReaderWriterFactory.StepByStepFactory)
    var counter = 0
    while (counter < 232) {
        try {
            writeLoop(w, counter * 10L)
            println("counter: $counter")
            counter++
        } catch (t: Throwable) {
            throw RuntimeException("error at $counter", t)
        }
    }
    val got = w.readDataPoints(StreamerFactory.streamer + TestWrite)
    got.forEach {
        it.list.forEach {
            println("name: ${it.header}")
            it.list.forEach {
                println("time: ${it.time}: ${it.value}")
            }
        }
    }
}

private object TestC {
    private val f = File("main/testX")
    fun testDataoutputStream() {
        val d = DataOutputStream(GZIPOutputStream(FileOutputStream(f, true)))
        val s = "HelloWorld"
        d.writeByte(15)
        d.writeInt(s.length)
        d.writeBytes(s)
        d.close()
    }

    fun readString(f: InputStream) {

        f.read()
    }

    fun readFile() {
        val f = DataInputStream(GZIPInputStream(FileInputStream(f)))
        while (f.read() == 15) {
            val length = f.readInt()
            f.read(ByteArray(length))
            println(f.readInt())
            println(f.readInt())
        }
        println(f.read())
        println(f.read())
        f.close()
    }
}

private class ArrayTester {
    val buffer = ByteBuffer.allocate(5)
    val byteArray = listOf(5, 2, 3, 6, 7).map { it.toByte() }.toByteArray()

    private fun put(off: Int, length: Int) {
        buffer.put(byteArray, off, length)
        val arrayGot = buffer.array().toList()
        println("arrayGot: $arrayGot")

    }

    fun testByteBuffer() {
        put(0, 2)
        put(2, 1)
        put(3, 1)
        put(4, 1)
    }
}

fun main() {
    writeMultipleTimes()
//    ArrayTester().testByteBuffer()
//    TestC.testDataoutputStream()
//    TestC.readFile()
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

