package org.daiv.tick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class DataWriterTest {


    data class GetRunnerMock(override val maxRead: Long, val isToInsertBefore: Boolean) :
        GetRunner {
        override fun isToInsertBefore(lastInsertTickTime: Long): Boolean {
            return isToInsertBefore
        }
    }

    @Test
    fun testEWWriter() {
        val x = GetRunnerMock(10L, false)
        val runner = x.getRunner(10L, 100L)
        assertEquals(ForwardRunner(10L, 20L, 100L, 10L), runner)
    }

    @Test
    fun testEWWriterBackwards() {
        val x = GetRunnerMock(10L, true)
        val runner = x.getRunner(10L, 100L)
        assertEquals(BackwardRunner(90L, 100L, 10L, 10L), runner)
    }


    @Test
    fun readToDatabaseTestFinish() {
        object : Runner {
            override val from: Long = 10L
            override val to: Long = 20L

            override fun next(): Runner {
                return this
            }

            override fun finish(): Boolean {
                return true
            }
        }.readDataToDatabase() {
            fail("should not be called, because runner was finished")
        }

    }

    @Test
    fun readToDatabaseTest() {
        var firstTimeCall = false
        object : Runner {
            override val from: Long = 10L
            override val to: Long = 20L

            override fun next(): Runner {
                finished = true
                return this
            }

            private var finished = false
            override fun finish(): Boolean {
                return finished
            }
        }.readDataToDatabase() {
            if (firstTimeCall) {
                fail("should not be called, because runner was finished")
            }
            firstTimeCall = true
        }
        assertTrue(firstTimeCall, "runner function was never called, although it should have been called exactly once")
    }

    @Test
    fun forwardRunnerTest() {
        val forward = ForwardRunner(10L, 20L, 100L, 10L)
        val next = forward.next()
        assertEquals(ForwardRunner(21L, 31L, 100L, 10L), next)
    }

    @Test
    fun backwardRunnerTest() {
        val forward = BackwardRunner(50L, 60L, 10L, 10L)
        val next = forward.next()
        assertEquals(BackwardRunner(39L, 49L, 10L, 10L), next)
    }

    @Test
    fun backwardRunnerFinishTest() {
        val forward = BackwardRunner(10L, 14L, 10L, 10L)
        val next = forward.next().finish()
        assertTrue(next)
    }

    @Test
    fun forwardRunnerFinishTest() {
        val forward = ForwardRunner(95L, 100L, 100L, 10L)
        val next = forward.next().finish()
        assertTrue(next)
    }

    data class OfferSideTimeable(override val time: Long) : Timeable

    data class ToRunnerFunctionMock(
        override val bid: ListInserter<OfferSideTimeable>,
        override val ask: ListInserter<OfferSideTimeable>,
        private val readToStored: ReadToStored<SimpleTimeable, OfferSideTimeable>
    ) : ToRunnerFunctionConverterImpl<SimpleTimeable, OfferSideTimeable>,
        ReadToStored<SimpleTimeable, OfferSideTimeable> by readToStored

    class TickWriterMock(val list: MutableList<List<OfferSideTimeable>>) : ListInserter<OfferSideTimeable> {
        override fun isToInsertBefore(lastInsertTickTime: Long): Boolean {
            return false
        }

        override fun insertList(list: List<OfferSideTimeable>) {
            this.list.add(list)
        }
    }

    object TimeableReadToStored : ReadToStored<SimpleTimeable, OfferSideTimeable> {
        override fun readToStoredBid(it: SimpleTimeable): OfferSideTimeable {
            return OfferSideTimeable(it.time)
        }

        override fun readToStoredAsk(read: SimpleTimeable): OfferSideTimeable {
            return OfferSideTimeable(read.time)
        }
    }

    fun offerSideTimeableListOf( vararg elements: Int) =
        elements.map { OfferSideTimeable(it.toLong()) }


    data class FromToMock(override val from: Long, override val to: Long) : FromTo

    @Test
    fun instrumentDataWriterTest() {
        val bidList = mutableListOf<List<OfferSideTimeable>>()
        val askList = mutableListOf<List<OfferSideTimeable>>()
        val x = ToRunnerFunctionMock(TickWriterMock(bidList), TickWriterMock(askList), TimeableReadToStored)
        val t = x.toRunnerFunction { from, to -> timeableListOf(5, 10) }
        FromToMock(5L, 6L).t()
        assertEquals(listOf(offerSideTimeableListOf( 5, 10)), bidList)
        assertEquals(listOf(offerSideTimeableListOf( 5, 10)), askList)
    }
}

