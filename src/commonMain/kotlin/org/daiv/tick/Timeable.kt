package org.daiv.tick

import kotlinx.serialization.Serializable
import org.daiv.time.isoTime
import org.daiv.util.binarySearchByEnd
import org.daiv.util.binarySearchByStart
import kotlin.reflect.KClass

fun <T> List<T>.second() = get(1)
fun <T> List<T>.secondToLast() = get(size - 2)

expect fun Long.toXString(): String

data class SimpleTimeable(override val time: Long) : Timeable {
    override fun toString(): String {
        return "${time.isoTime()} - $time"
    }
}

interface CurrentDataCollection : DataCollection {
    val isCurrent: Boolean
}

interface DataCollection : Comparable<DataCollection> {
    val start: Long
    val end: Long
    override fun compareTo(other: DataCollection): Int = start.compareTo(other.start)
    fun stopTime() = end - 1L
}

class DefaultListable<T : Any>(
    override val clazz: KClass<T>,
    override val list: List<T> = emptyList(),
    val asName: T.() -> String
) : Listable<T> {
    override fun toName(t: T): String {
        return t.asName()
    }
}

interface Listable<T : Any> {
    val list: List<T>
    fun toName(t: T): String
    val clazz: KClass<T>

    fun find(name: String): T? {
        return list.find { toName(it) == name }
    }

    fun listable(vararg elements: T): Listable<T> = object : Listable<T> by this {
        override val list: List<T> = elements.toList()
    }

    companion object {
        val any = object : Listable<Any> {
            override val list: List<Any> = emptyList()


            override fun toName(t: Any): String {
                return t.toString()
            }

            override val clazz: KClass<Any> = Any::class
        }
    }
}

interface StringListable:Listable<String>{
    override val clazz: KClass<String>
        get() = String::class

    override val list: List<String>
        get() = emptyList()

    override fun find(name: String): String? {
        return name
    }

    override fun toName(t: String): String {
        return t
    }
}

/**
 *
 */
interface Timeable : Comparable<Timeable> {
    val time: Long


    override fun compareTo(other: Timeable) = time.compareTo(other.time)
    fun toTimeableClass() = if (this is SimpleTimeable) this else SimpleTimeable(time)

    fun toXString(add: String = ""): String {
        return "${time.toXString()}: $add"
    }

    fun toXLongString(add: String = ""): String {
        return "${time.isoTime()}: $add"
    }
}

/**
 * returns the specified index inclusive [time]
 * if list is empty, 0 is returned
 */
fun <T : Timeable> List<T>.binarySearchByStart(time: Long): Int = binarySearchByStart(time) { it.time }

/**
 * returns the specified index inclusive [time]
 * if list is empty, 0 is returned
 */
fun <T : Timeable> List<T>.binarySearchByEnd(time: Long): Int = binarySearchByEnd(time) { it.time }


fun <T : Timeable> List<T>.subList(from: Long, to: Long): List<T> {
    return subList(binarySearchByStart(from) { it.time }, binarySearchByEnd(to) { it.time })
}

fun timeableListOf(vararg elements: Int) = elements.map { SimpleTimeable(it.toLong()) }

interface Valueable {
    val value: Double

    operator fun minus(other: Valueable) = value - other.value

    operator fun minus(other: Double) = value - other

    operator fun plus(other: Valueable) = value + other.value
    operator fun plus(other: Double) = value + other

    operator fun times(other: Valueable) = value * other.value
    operator fun times(other: Double) = value * other

    fun compareValueTo(other: Valueable) = this.value.compareTo(other.value)

    companion object {
        val comparator: Comparator<Valueable> = object : Comparator<Valueable> {
            override fun compare(a: Valueable, b: Valueable): Int {
                return a.compareValueTo(b)
            }

        }
    }
}

interface ValueTimeable : Valueable, Timeable



interface Nameable {
    val name: String
}


interface StartTime {
    fun firstTime(): Long?
}

interface LastTime {
    fun lastTime(): Long?
}

interface StartEndTime : StartTime, LastTime

interface FromToAccessor<T : Timeable> {
    fun read(from: Long, to: Long, max: Int = Int.MAX_VALUE): List<T>
}

interface StartTimeFromToAccessor<T : Timeable> : FromToAccessor<T>, StartTime

interface LastBeforeReader<T : Timeable> {
    fun readLastBefore(numberToRead: Int, time: Long): List<T>
    fun readNextAfter(numberToRead: Int, time: Long): List<T>
}

interface ReadTimeable<T : Timeable, in D : DataCollection> {
    fun read(fileData: D): List<T>
}

interface FileReader<T:Timeable>{
    fun read(fileData: FileRefable): List<T>
}

interface DataAccessor<T : Timeable> : LastTime, FromToAccessor<T>, StartTimeFromToAccessor<T>, StartEndTime
interface LastReadDataAccessor<T : Timeable> : DataAccessor<T>, LastBeforeReader<T>

fun interface FileRefFactory {
    fun createFile(fileName: String): FileRef
}

