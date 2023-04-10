package org.daiv.tick

interface Intervalable {
    val interval: Long
}

interface FileDataFactory : Intervalable {
    fun <R : DataCollection> fileData(time: Long, fileData: (Long, Long) -> R): R {
        val fileStart = time / interval * interval
        val fileEnd = fileStart + interval
        return fileData(fileStart, fileEnd)
    }

    fun startTime(time: Long) = time / interval * interval

    fun stopTime(time: Long): Long {
        val fileStart = startTime(time)
        return fileStart + interval - 1L
    }

    fun fileName(start: Long, end: Long) = "$start-$end"
}

interface FileDataCreator<T : DataCollection> : FileDataFactory {

    fun build(start: Long, end: Long, isCurrent:Boolean, fileName: String = fileName(start, end)): T

    fun defaultFileData(time: Long): T = fileData(time) { start, end -> build(start, end, false) }
    fun currentFileData(time: Long): T = fileData(time) { start, end -> build(start, end, true) }
}

