package org.daiv.tick

interface NativeDataReader<T:NativeDataReader<T>>{
    fun put(src: ByteArray, offset: Int, length: Int)
    fun flip(): T
    fun read(size: Int, d: ByteArrayReader): T {
        val bytes = ByteArray(size)
        var read = 0
        do {
            val toRead = read
            val newlyRead = d.read(bytes, toRead, size - toRead)
            if (newlyRead == -1) {
                break
            }
            read += newlyRead
            try {
                put(bytes, toRead, newlyRead)
            } catch (t: Throwable) {
                throw RuntimeException("faild: ${bytes.size}, $read, $newlyRead", t)
            }
        } while (read != -1 && read != size)
        return flip()
    }
}


