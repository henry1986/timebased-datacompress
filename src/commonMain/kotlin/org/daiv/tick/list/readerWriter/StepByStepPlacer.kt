package org.daiv.tick.list.readerWriter

import mu.KotlinLogging
import org.daiv.tick.*

class StepByStepPlacer(val ioStreamGenerator: IOStream) : ListReaderWriter {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun <T> store(mapper: StreamMapper<T>, ticks: List<T>) {
        val d = ioStreamGenerator.getNativeDataReceiver()
        try {

            ticks.forEach {
                d.writeByte(5)
                mapper.toOutput(it, d)
            }
            d.flush()
        } finally {
            d.close()
        }
    }

    private fun <T> readFlexibleSizedData(mapper: StreamMapper<out T>): List<T> {
        val d = ioStreamGenerator.readStream()
        val ticks = mutableListOf<T>()
        try {
            var read = d.read()
            while (read != -1) {
                if (read != 5) {
                    throw RuntimeException("unexpected value: $read")
                }
                val stringLength = try {
                    d.readInt()
                } catch (t: Throwable) {
                    logger.error { "could not read anymore, currently read: $ticks" }
                    break
                }
                val x = mapper.size + stringLength
                ticks.add(mapper.toElement(ioStreamGenerator.read(x, d)))
                read = d.read()
            }
        } finally {
            d.close()
        }
        return ticks
    }

    override fun <T> read(mapper: StreamMapper<out T>): List<T> {
        if (mapper is FlexibleStreamMapper) {
            return readFlexibleSizedData(mapper)
        }
        val d = ioStreamGenerator.readStream()
        val mutableList = mutableListOf<T>()
        try {
            while (d.read() != -1) {
                val e = try {
                    mapper.toElement(ioStreamGenerator.read(mapper.size, d))
                } catch (t: Throwable) {
                    throw RuntimeException("error with $", t)
                }
                mutableList.add(e)
            }
        } finally {
            d.close()
        }
        return mutableList.toList()
    }
}
