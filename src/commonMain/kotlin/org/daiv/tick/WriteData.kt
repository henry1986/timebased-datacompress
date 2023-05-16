package org.daiv.tick

import mu.KotlinLogging
import org.daiv.tick.list.readerWriter.ListReaderWriterFactory
import org.daiv.tick.streamer.StreamerFactory


interface CurrentDateGetter {
    fun currentDate(): String
}

interface StoringFile {
    fun <R> onFile(
        file: FileRef,
        withCompression: Boolean,
        strategyFactory: ListReaderWriterFactory,
        ioStreamGeneratorFactory: IOStreamGeneratorFactory,
        streamMapper: StreamMapper<Datapoint<R>>,
        datapoint: Datapoint<R>
    )
}

object WithRead : StoringFile {
    private val logger = KotlinLogging.logger {}

    override fun <R> onFile(
        file: FileRef,
        withCompression: Boolean,
        strategyFactory: ListReaderWriterFactory,
        ioStreamGeneratorFactory: IOStreamGeneratorFactory,
        streamMapper: StreamMapper<Datapoint<R>>,
        datapoint: Datapoint<R>
    ) {
        val strategy = strategyFactory.create(ioStreamGeneratorFactory.create(file, withCompression))
        if (!file.exists()) {
            strategy.store(streamMapper, listOf(datapoint))
        } else {
            val read: List<Datapoint<R>> = try {
                strategy.read(streamMapper)
            } catch (t: Throwable) {
                logger.error(t) { "reading error at file ${file.fileName}" }
                emptyList()
            }
            strategy.store(streamMapper, read + datapoint)
        }
    }
}

object WithoutRead : StoringFile {
    override fun <R> onFile(
        file: FileRef,
        withCompression: Boolean,
        strategyFactory: ListReaderWriterFactory,
        ioStreamGeneratorFactory: IOStreamGeneratorFactory,
        streamMapper: StreamMapper<Datapoint<R>>,
        datapoint: Datapoint<R>
    ) {
        strategyFactory.create(ioStreamGeneratorFactory.create(file, withCompression))
            .store(streamMapper, listOf(datapoint))
    }
}

/**
 * Class responsible for writing and reading [Datapoint]s to and from files.
 *
 * @property lRWStrategy The strategy factory for reading and writing data to files.
 * @property fFactory The file reference factory.
 * @property currentDateGetter The current date getter.
 * @property mainDir The main directory where data files are stored.
 * @property withCompression if the data needs to be compressed or not
 */
class WriteData(
    val listReaderWriterFactory: ListReaderWriterFactory,
    val ioStreamGeneratorFactory: IOStreamGeneratorFactory,
    val fFactory: FileRefFactory,
    val currentDateGetter: CurrentDateGetter,
    val mainDir: FileRef,
    val withCompression: Boolean,
    val storingFile: StoringFile
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Writes a [Datapoint] to a file using the given [StreamerFactory].
     *
     * @param streamerFactory The streamer factory to use for writing the datapoint.
     * @param datapoint The datapoint to be written.
     */
    fun <R> write(streamerFactory: StreamerFactory<Datapoint<R>>, datapoint: Datapoint<R>) {
        write(streamerFactory.streamer(datapoint.header), datapoint)
    }

    /**
     * Reads [Datapoint]s from files using the provided list of [StreamerFactory]s.
     *
     * @param streamMapperList The list of streamer factories to use for reading the datapoints.
     * @return A list of [LogData] objects containing the data read from the files.
     */
    fun readDataPoints(streamMapperList: List<StreamerFactory<out Datapoint<*>>>): List<LogData> {
        return mainDir.listFiles().map { dir ->
            val files = dir.listFiles()
            val streamMapperMap = streamMapperList.associateBy { it.ending }
            LogData(dir.fileName, files.map {
                val file = fFactory.createFile(dir, it.fileName)
                val split = file.fileName.split(".")
                val ending = split.last()
                val headerList = split.dropLast(2)
                val name = split.dropLast(1).last()
                val header = Header(headerList, name)
                try {
                    streamMapperMap[ending]?.let { streamerFactory ->
                        val streamMapper = streamerFactory.streamer(header)
                        LogColumn(header, listReaderWriterFactory.create(ioStreamGeneratorFactory.create(file, withCompression)).read(streamMapper))
                    } ?: run {
                        println("could not find $ending")
                        LogColumn(header, emptyList())
                    }
                } catch (t: Throwable) {
                    logger.error(t) { "reading error at file ${file.fileName}" }
                    LogColumn(header, emptyList())
                }
            })
        }
    }

    /**
     * Writes a [Datapoint] to a file using the given [EndingStreamMapper].
     *
     * @param streamMapper The ending stream mapper to use for writing the datapoint.
     * @param datapoint The datapoint to be written.
     */
    private fun <R> write(
        streamMapper: EndingStreamMapper<Datapoint<R>>,
        datapoint: Datapoint<R>,
    ) {
        val file = createFile(datapoint, streamMapper.ending)
        try {
            storingFile.onFile(file,withCompression, listReaderWriterFactory, ioStreamGeneratorFactory, streamMapper, datapoint)
        } catch (t: Throwable) {
            throw RuntimeException("writing error at file ${file.fileName}", t)
        }
    }

    private fun <R> createFile(datapoint: Datapoint<R>, ending: String): FileRef {
        val dir = fFactory.createFile(mainDir, currentDateGetter.currentDate())
        dir.mkdirs()
        return fFactory.createFile(dir, datapoint.header.toName() + ".${ending}")
    }
}
