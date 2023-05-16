package org.daiv.tick

interface NativeDataWriter {
    fun writeLong(l: Long)
    fun writeDouble(d: Double)
    fun writeString(string: String)
    fun writeInt(i: Int)
    fun writeByte(i: Int)
    fun flush()
    fun close()
}

interface NativeData{
    val byte: Byte
    val string: String
    val long: Long
    val double: Double
    val int: Int
}

interface NativeDataGetter:NativeDataReader<NativeDataGetter>, NativeData {
    val position: Int
    val array: ByteArray
    val limit:Int
}
