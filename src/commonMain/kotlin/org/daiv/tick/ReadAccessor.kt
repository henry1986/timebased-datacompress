package org.daiv.tick

import org.daiv.util.binarySearchByEnd
import org.daiv.util.binarySearchByStart

interface ReadAccessor<T : Timeable, D : DataCollection> : LastReadDataAccessor<T> {
    val allDatas: List<D>
    val readTimeable: ReadTimeable<T,in D>

    fun D.ticks() = readTimeable.read(this)

    override fun read(from: Long, to: Long, max: Int): List<T> {
        val iStart = allDatas.binarySearchByStart(from) { it.stopTime() }
        val iEnd = allDatas.binarySearchByEnd(to) { it.start }
        if (iStart == iEnd - 1) {
            return allDatas[iStart].ticks()
                .dropWhile { it.time < from }
                .takeWhile { it.time <= to }
                .take(max)
        }

        val takenStartTicks = allDatas[iStart].ticks()
            .dropWhile { it.time < from }
            .takeWhile { it.time <= to }
        if (takenStartTicks.size > max) {
            return takenStartTicks.take(max)
        }

        val betweenTicks = if (iEnd - iStart > 2) allDatas.subList(iStart + 1, iEnd - 1)
            .flatMap { it.ticks() } else emptyList()

        if (betweenTicks.size + takenStartTicks.size > max) {
            return (takenStartTicks + betweenTicks).take(max)
        }

        val takenEndTicks = allDatas[iEnd - 1].ticks()
            .takeWhile { it.time <= to }
        return (takenStartTicks + betweenTicks + takenEndTicks).take(max)
    }

    override fun firstTime() = first()?.time
    override fun lastTime() = last()?.time

    fun first(): T? {
        return allDatas.firstOrNull()
            ?.ticks()
            ?.firstOrNull()
    }

    fun last() = (allDatas.lastOrNull())?.ticks()?.lastOrNull()

    private fun List<D>.recReverse(numberToRead: Int, ret: List<T>, i: Int = size - 1): List<T> {
        if (i >= 0) {
            val fileData = get(i)
            val ticks = readTimeable.read(fileData)
            return if (ticks.size < numberToRead - ret.size) {
                recReverse(numberToRead, ticks + ret, i - 1)
            } else {
                ticks.takeLast(numberToRead - ret.size) + ret
            }
        }
        return ret
    }

    override fun readLastBefore(numberOfCandles: Int, to: Long): List<T> {
        val datas = allDatas.binarySearchByEnd(to) { it.start }
            .let { allDatas.take(it) }
        if (datas.isEmpty()) return emptyList()
        return datas.dropLast(1)
            .recReverse(
                numberOfCandles,
                readTimeable.read(datas.last()).takeWhile { it.time < to }.takeLast(numberOfCandles)
            )
    }


    private fun List<D>.rec(numberToRead: Int, ret: List<T>, i: Int = 0): List<T> {
        if (i < size) {
            val fileData = get(i)
            val ticks = readTimeable.read(fileData)
            return if (ticks.size < numberToRead - ret.size) {
                rec(numberToRead, ret + ticks, i + 1)
            } else {
                ret + ticks.take(numberToRead - ret.size)
            }
        }
        return ret
    }

    override fun readNextAfter(numberToRead: Int, start: Long): List<T> {
        val relevantFileDatas = allDatas.binarySearchByStart(start) { it.stopTime() }
            .let { allDatas.drop(it) }

        val ticks = relevantFileDatas.firstOrNull()?.let {
            readTimeable.read(it)
                .dropWhile { it.time <= start }
                .take(numberToRead)
        } ?: return emptyList()
        return relevantFileDatas.drop(1)
            .rec(numberToRead, ticks)
    }

    fun readAll() = allDatas.flatMap { it.ticks() }
}
