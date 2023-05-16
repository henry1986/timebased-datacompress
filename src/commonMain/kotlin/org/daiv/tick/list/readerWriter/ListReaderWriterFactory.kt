package org.daiv.tick.list.readerWriter

import mu.KotlinLogging
import org.daiv.tick.*

interface ListReaderWriterFactory {
    val storingFile: StoringFile
    fun create(lrwCreator: IOStreamGenerator): ListReaderWriter

    object LRWStrategyFactory : ListReaderWriterFactory {
        override val storingFile: StoringFile = WithRead

        override fun create(lrwCreator: IOStreamGenerator): ListReaderWriter {
            return LRWStrategy(lrwCreator)
        }
    }

    object StepByStepFactory : ListReaderWriterFactory {
        override val storingFile: StoringFile = WithoutRead

        override fun create(lrwCreator: IOStreamGenerator): ListReaderWriter {
            return StepByStepPlacer(lrwCreator)
        }
    }
}

class LRWStrategy(creator: IOStreamGenerator) : ListReaderWriter, IOStreamGenerator by creator {

    override fun <T> store(mapper: StreamMapper<T>, ticks: List<T>) {
        val d = getNativeDataReceiver()
        d.writeInt(ticks.size)
        ticks.forEach { mapper.toOutput(it, d) }
        d.flush()
        d.close()
    }

    private fun read(size: Int, d: ReadStream): NativeDataGetter {
        return getNativeDataGetter(size).read(size, d)
    }

    private fun <T> readFlexibleSizedData(mapper: StreamMapper<out T>): List<T> {
        val d = readStream()
        val maxEntrys = d.readInt()

        val ticks = mutableListOf<T>()
        for (i in 1..maxEntrys) {
            val stringLength = try {
                d.readInt()
            } catch (t: Throwable) {
                logger.error { "could not read anymore, currently read: $ticks" }
                break
            }
            val x = mapper.size + stringLength
            ticks.add(mapper.toElement(read(x, d)))
        }
        d.close()
        return ticks
    }

    override fun <T> read(mapper: StreamMapper<out T>): List<T> {
        if (mapper is FlexibleStreamMapper) {
            return readFlexibleSizedData(mapper)
        }
        val d = readStream()
        val size = d.readInt() * mapper.size
//        logger.trace { "file: $fileName size: $size" }
        val buffer = read(size, d)

        val ticks = mutableListOf<T>()
        for (i in 1..(size / mapper.size)) {
            ticks.add(mapper.toElement(buffer))
        }
        d.close()
        return ticks
    }

    companion object {
        private val logger = KotlinLogging.logger { }
    }
}
