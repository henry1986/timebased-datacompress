package org.daiv.tick

import kotlinx.serialization.Serializable
import mu.KotlinLogging

interface InsertDirectionCheck {
    fun isToInsertBefore(lastInsertTickTime: Long): Boolean
}

interface ListInserter<T> : InsertDirectionCheck {
    //    fun lastTick(): StoredTick
    fun insertList(list: List<T>)
}

@Serializable
data class IPConnectionData(val ip: String, val port: Int)

interface ReadToStored<READ, STORED> {
    fun readToStoredBid(it: READ): STORED

    fun readToStoredAsk(read: READ): STORED
}

interface ToRunnerFunctionConverter<READ> {
    fun toRunnerFunction(func: (Long, Long) -> List<READ>): FromTo.() -> Unit
}

interface ToRunnerFunctionConverterImpl<READ, STORED> : ToRunnerFunctionConverter<READ>, ReadToStored<READ, STORED> {
    val bid: ListInserter<STORED>
    val ask: ListInserter<STORED>

    override fun toRunnerFunction(func: (Long, Long) -> List<READ>): FromTo.() -> Unit {
        return {
            val read = func(from, to)
            bid.insertList(read.map { readToStoredBid(it) })
            ask.insertList(read.map { readToStoredAsk(it) })
        }
    }
}

data class DataWriter<READ, STORED>(
    override val bid: ListInserter<STORED>,
    override val ask: ListInserter<STORED>,
    override val maxRead: Long,
    private val readToStored: ReadToStored<READ, STORED>
) : DataWriterStrategy<READ>, InsertDirectionCheck by bid, ReadToStored<READ, STORED> by readToStored,
    ToRunnerFunctionConverterImpl<READ, STORED>


interface FromTo {
    val from: Long
    val to: Long
}

interface Runner : FromTo {

    fun next(): Runner
    fun finish(): Boolean

    fun getNext(insert: Runner.() -> Unit): Runner? {
        if (finish()) {
            return null
        }
        insert()
        return next()
    }

    fun readDataToDatabase(
        func: Runner.() -> Unit
    ) {
        getNext(func)?.let { next -> next.readDataToDatabase(func) }
    }

}

data class BackwardRunner(override val from: Long, override val to: Long, val end: Long, val maxRead: Long) : Runner {
    override fun next(): Runner {
        val nextTo = from - 1L
        val maxNext = nextTo - maxRead
        return copy(from = if (maxNext < end) end else maxNext, to = nextTo)
    }

    override fun finish(): Boolean {
        return from >= to
    }
}

data class ForwardRunner(
    override val from: Long,
    override val to: Long,
    val end: Long,
    val maxRead: Long,
) : Runner {
    override fun next(): ForwardRunner {
        val nextFrom = to + 1L
        val maxNext = nextFrom + maxRead
        return copy(from = nextFrom, to = if (maxNext > end) end else maxNext)
    }

    override fun finish() = from >= to
}

interface GetRunner : InsertDirectionCheck {
    companion object {
        private val logger = KotlinLogging.logger { }
    }
    val maxRead: Long
    fun getRunner(from: Long, to: Long): Runner {
        return if (isToInsertBefore(to))
            BackwardRunner(to + 1L, to + 1L, from, maxRead).next()
        else
            ForwardRunner(from - 1L, from - 1L, to, maxRead).next()
    }
}


interface DataWriterStrategy<READ> : ToRunnerFunctionConverter<READ>, GetRunner {

    fun start(from: Long, to: Long, func: (Long, Long) -> List<READ>) {
        getRunner(from, to).readDataToDatabase(toRunnerFunction(func))
    }
}

