package org.daiv.tick

import mu.KotlinLogging
import org.daiv.time.isoTime


fun interface DataConsumer<T> {
    fun insert(data: List<T>)
}

data class ToStandardInsertResult<T>(val firstTicks: List<T>, val secondTicks: List<T>)

interface FileInstaller<T : Timeable, DATACOLLECTION : CurrentDataCollection> : DataConsumer<T>,
    FileDataCreator<DATACOLLECTION>,
    InsertDirectionCheck {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    //    val fileDatas: List<DATACOLLECTION>
    fun doesCurrentFileExists(): Boolean
    fun deleteCurrentFile()
    fun readCurrentTicks(): List<T>
    fun store(fileData: DATACOLLECTION, ticks: List<T>)
    fun getFirstFile(): DATACOLLECTION
    fun getFirstFileTicks(): List<T>
    fun deleteFirstFile()
//    fun getTicks(fileData: DATACOLLECTION): List<T>

//    fun fileForTime(time:Long):DATACOLLECTION?{
//        return fileDatas.find {
//            it.start <= time && it.end > time
//        }
//    }

    fun buildToStandardInsertResult(stopTime: Long, ticks: List<T>): ToStandardInsertResult<T>? {
        if (ticks.isEmpty()) {
            return null
        }
        val grouped = ticks.groupBy { it.time <= stopTime }

        val ticksToStoreInFile = grouped[true]!!
        val futureTicks = grouped[false] ?: emptyList()
        return ToStandardInsertResult(ticksToStoreInFile, futureTicks)
    }

    fun insertStandard(ticks: List<T>) {
        if (ticks.isEmpty()) {
            return
        }
        val start = ticks.first().time
        val fileData = defaultFileData(start)
        buildToStandardInsertResult(fileData.stopTime(), ticks)?.apply {
            store(fileData, firstTicks)
            insertStandard(secondTicks)
        }
    }

    private fun insertBefore(ticksToInsert: List<T>) {
        if (ticksToInsert.isEmpty()) {
            return
        }
        val firstTicks = getFirstFileTicks()
        val firstTick = firstTicks.first()
        if (ticksToInsert.last().time > firstTick.time) {
            throw IndexOutOfBoundsException("last tick to insert is after first already inserted tick")
        }
        val firstFile = getFirstFile()
        if (ticksToInsert.last().time >= firstFile.start) {
            deleteFirstFile()
            val allTicks = if (firstTicks.first().time == ticksToInsert.last().time) {
                ticksToInsert + firstTicks.drop(1)
            } else {
                ticksToInsert + firstTicks
            }

            if (firstFile.isCurrent) {
                insertWithCurrent(allTicks)
            } else {
                insertStandard(allTicks)
            }
        } else {
            insertStandard(ticksToInsert)
        }
    }

//    fun insertBetween(ticksToInsert: List<T>) {
//        val first = ticksToInsert.first().time
//        val last = ticksToInsert.last().time
//        val firstFile = fileForTime(first)
//        val lastFile = fileForTime(last)
//        insertStandard(ticksToInsert)
//    }

    override fun isToInsertBefore(to: Long): Boolean {
        getFirstFileTicks().firstOrNull()?.let {
            if (it.time >= to) {
                return true
            }
        }
        return false
    }

    override fun insert(ticksToInsert: List<T>) {
        if (ticksToInsert.isEmpty()) {
            return
        }

        getFirstFileTicks().firstOrNull()?.let {
            if (it.time >= ticksToInsert.last().time) {
                insertBefore(ticksToInsert)
                return
            }
        }

        val start = ticksToInsert.first()
            .time
        if (doesCurrentFileExists()) {
            val readTicks = readCurrentTicks()
            val lastTickOfRead = readTicks.last()
            if (lastTickOfRead.time > start) {
                throw IndexOutOfBoundsException(
                    """a tick that already exists or that is previous the latest tick is tried 
                    |to be inserted. earliest insert tick: ${ticksToInsert.first()}, time: ${ticksToInsert.first().time.isoTime()}, latest tick: $lastTickOfRead time: ${lastTickOfRead.time.isoTime()}""".trimMargin()
                )
            }
            deleteCurrentFile()
            val allTicks = if (readTicks.last().time == ticksToInsert.first().time)
                readTicks.dropLast(1) + ticksToInsert
            else
                readTicks + ticksToInsert
            insertWithCurrent(allTicks)
        } else {
            insertWithCurrent(ticksToInsert)
        }
    }

    fun insertWithCurrent(ticksToInsert: List<T>) {
        val result = buildToCurrent(ticksToInsert)
        insertStandard(result.firstTicks)
        result.secondTicks.firstOrNull()?.time?.let {
            store(currentFileData(it), result.secondTicks)
        }
    }

    fun buildToCurrent(ticksToInsert: List<T>): ToStandardInsertResult<T> {
        val startTime = startTime(ticksToInsert.last().time)
        val group = ticksToInsert.groupBy { it.time < startTime }

        val previousTicks = group[true] ?: emptyList()

        val currentTicks = group[false]!!
        return ToStandardInsertResult(previousTicks, currentTicks)
    }
}
