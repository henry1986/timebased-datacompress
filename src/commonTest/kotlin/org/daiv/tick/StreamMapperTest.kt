package org.daiv.tick

import kotlin.test.Test

class LogDataTest {

    @Test
    fun test() {
        val h1 = Header(listOf(), "cpState")
        val h2 = Header(listOf(), "linkState")
        val rows = LogData(
            "2023-04-12", listOf(
                LogColumn(
                    h1,
                    listOf(
                        Datapoint(Header(listOf(), "cpState"), 5L, 15),
                        Datapoint(h1, 6L, 18),
                        Datapoint(Header(listOf(), "cpState"), 9L, 20)
                    )
                ),
                LogColumn(
                    h2,
                    listOf(
                        Datapoint(h2, 5L, 15),
                        Datapoint(Header(listOf(), "linkState"), 6L, 14),
                        Datapoint(Header(listOf(), "linkState"), 8L, 12),
                        Datapoint(Header(listOf(), "linkState"), 9L, 19),
                    )
                )
            )
        ).toRows()
        rows.header.forEach {
            println("it: $it")
        }
        rows.rows.forEach {
            println("it: $it")
        }
    }

    class NativeDataGetterMock : NativeDataGetter {
        override val byte: Byte
            get() = TODO("Not yet implemented")
        override val string: String
            get() = TODO("Not yet implemented")
        override val long: Long
            get() = TODO("Not yet implemented")
        override val double: Double
            get() = TODO("Not yet implemented")
        override val int: Int
            get() = TODO("Not yet implemented")
        override val position: Int
            get() = TODO("Not yet implemented")
        override val array: ByteArray
            get() = TODO("Not yet implemented")
        override val limit: Int
            get() = TODO("Not yet implemented")

        override fun put(src: ByteArray, offset: Int, length: Int) {
            TODO("Not yet implemented")
        }

        override fun flip(): NativeDataGetter {
            TODO("Not yet implemented")
        }

    }

    class ReadStreamMock : ReadStream {

        override fun read(byteArray: ByteArray, off: Int, len: Int): Int {
            TODO("Not yet implemented")
        }

        override fun close() {
            TODO("Not yet implemented")
        }

        override fun readInt(): Int {
            TODO("Not yet implemented")
        }

    }

//    @Test
//    fun read(){
//        NativeDataGetterMock().read(5, )
//    }
}
