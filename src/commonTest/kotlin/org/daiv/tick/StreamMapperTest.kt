package org.daiv.tick

import kotlin.test.Test

class StreamMapperTest {
    @Test
    fun test() {
        val rows = LogData("2023-04-12",listOf(
            LogColumn(
                listOf(
                    Datapoint(Header(listOf(), "cpState"), 5L, 15),
                    Datapoint(Header(listOf(), "cpState"), 6L, 18),
                    Datapoint(Header(listOf(), "cpState"), 9L, 20)
                )
            ),
            LogColumn(
                listOf(
                    Datapoint(Header(listOf(), "linkState"), 5L, 15),
                    Datapoint(Header(listOf(), "linkState"), 6L, 14),
                    Datapoint(Header(listOf(), "linkState"), 8L, 12),
                    Datapoint(Header(listOf(), "linkState"), 9L, 19),
                )
            )
        )).toRows()
        rows.header.forEach {
            println("it: $it")
        }
        rows.rows.forEach {
            println("it: $it")
        }
    }

}