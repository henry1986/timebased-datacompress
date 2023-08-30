package org.daiv.tick

import kotlinx.serialization.Serializable
import org.daiv.tick.streamer.StreamerFactory

data class ColumnValue(val header: Header, val value: String)

data class LogRow(val time: Long, val logColumn: List<ColumnValue>)

data class DayLogRow(val day: String, val header: List<Header>, val rows: List<LogRow>)

fun LogData.toRows(): DayLogRow {
    val all = list.flatMap { it.list }
    val header = all.map { it.header }.distinct()
    val sorted = all.groupBy { it.time }.toList().sortedBy { it.first }
        .map { LogRow(it.first, it.second.map { ColumnValue(it.header, it.value.toString()) }) }
    return DayLogRow(day, header, sorted)
}


data class LogColumn(val header: Header, val list: List<Datapoint<*>>)

data class LogData(val day: String, val list: List<LogColumn>) {
    override fun toString(): String {
        return "$day to ${list.map { it.list + "\n" }}"
    }
}

@Serializable
data class LogDP<T>(val streamerFactory: StreamerFactory<Datapoint<T>>, val header: Header, val value: T) {
    fun write(writeData: WriteData, time: Long) {
        writeData.write(streamerFactory, Datapoint(header, time, value))
    }
}
