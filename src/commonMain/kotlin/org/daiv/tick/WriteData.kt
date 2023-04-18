package org.daiv.tick


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
        write(streamerFactory.streamer(datapoint.header), datapoint)
    }

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
                streamMapperMap[ending]?.let { streamMapper ->
                    val strategy = lRWStrategy.create(file, streamMapper.streamer(Header(headerList, name)))
                    LogColumn(strategy.read())
                } ?: run {
                    println("could not find $ending")
                    LogColumn(emptyList())
                }
            })
        }
    }

    private fun <R> write(streamMapper: EndingStreamMapper<Datapoint<R>>, datapoint: Datapoint<R>) {
        val dir = fFactory.createFile(mainDir, currentDateGetter.currentDate())
        dir.mkdirs()
        val file = fFactory.createFile(dir, datapoint.header.toName() + ".${streamMapper.ending}")
        val strategy = lRWStrategy.create(file, streamMapper)
        if (!file.exists()) {
            strategy.store(listOf(datapoint))
        } else {
            val read = strategy.read()
//            println("read: $read")
            strategy.store(read + datapoint)
        }
    }
}
