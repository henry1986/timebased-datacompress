package org.daiv.tick.list.readerWriter

import mu.KotlinLogging
import org.daiv.tick.*

class StepByStepPlacer(val ioStreamGenerator: IOStreamGenerator) : ListReaderWriter {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun <T> store(mapper: StreamMapper<T>, ticks: List<T>) {
        val d = ioStreamGenerator.getNativeDataReceiver()
        ticks.forEach {
            d.writeByte(5)
            d.writeInt(mapper.size)
            mapper.toOutput(it, d)
        }
        d.flush()
        d.close()
    }

    private fun read(size: Int, d: ReadStream): NativeDataGetter {
        return ioStreamGenerator.getNativeDataGetter(size).read(size, d)
    }

    private fun <T> readFlexibleSizedData(mapper: StreamMapper<out T>): List<T> {
        val d = ioStreamGenerator.readStream()

        val ticks = mutableListOf<T>()
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
            ticks.add(mapper.toElement(read(x, d)))
            read = d.read()
        }
        d.close()
        return ticks
    }

    override fun <T> read(mapper: StreamMapper<out T>): List<T> {
        if (mapper is FlexibleStreamMapper) {
            return readFlexibleSizedData(mapper)
        }
        val d = ioStreamGenerator.readStream()
        val mutableList = mutableListOf<T>()
        while (d.read() != -1) {
            val size = d.readInt()
            val e = try {
                mapper.toElement(ioStreamGenerator.getNativeDataGetter(size).read(size, d))
            } catch (t: Throwable) {
                throw RuntimeException("error with $", t)
            }
            mutableList.add(e)
        }
        d.close()
        return mutableList.toList()
    }
}
