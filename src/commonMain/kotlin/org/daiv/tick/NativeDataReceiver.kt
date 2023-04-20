package org.daiv.tick

interface NativeDataReceiver {
    fun writeLong(l: Long)
    fun writeDouble(d: Double)
    fun writeString(string: String)
    fun writeInt(i: Int)
    fun writeByte(i: Int)
    fun flush()
    fun close()
}

interface NativeDataGetter {
    val byte: Byte
    val string: String
    val long: Long
    val double: Double
    val int: Int
    val position: Int
    val array: ByteArray
    val limit:Int
    fun put(src: ByteArray, offset: Int, length: Int)
    fun flip(): NativeDataGetter

}
