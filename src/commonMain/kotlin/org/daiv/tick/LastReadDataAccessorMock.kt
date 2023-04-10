package org.daiv.tick


class LastReadDataAccessorMock<T : Timeable>(val list: List<T>) : LastReadDataAccessor<T> {

    override fun read(from: Long, to: Long, max: Int): List<T> {
        val start = list.binarySearchByStart(from)
        val end = list.binarySearchByEnd(to)
        return list.subList(start, end).take(max)
    }

    override fun readLastBefore(numberToRead: Int, time: Long): List<T> {
        val end = list.binarySearchByEnd(time)
        return list.take(end).takeLast(numberToRead)
    }

    override fun readNextAfter(numberToRead: Int, time: Long): List<T> {
        val start = list.binarySearchByStart(time)
        return list.drop(start).take(numberToRead)
    }

    override fun firstTime(): Long? {
        return list.firstOrNull()?.time
    }

    override fun lastTime(): Long? {
        return list.lastOrNull()?.time
    }
}
