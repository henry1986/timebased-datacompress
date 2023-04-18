# timebased-datacompress

# Time-based Data Compression
This library provides functionality for compressing and storing time-based data points.

# Features
* Compresses data points by mapping them to a stream of bytes
* Supports reading and writing data to and from disk
* Allows for pluggable compression strategies through the LRWStrategyFactory interface
* Supports multiple data types through the Datapoint and StreamMapper interfaces

## StreamMapper Interface

The `StreamMapper` interface defines a common way to map data objects to and from a stream of bytes. To implement the interface, you need to provide two functions:

- `toOutput(t: T, dataOutputStream: NativeDataReceiver)`: writes an object of type T to a data output stream.
- `toElement(byteBuffer: NativeDataGetter): T`: reads an object of type T from a byte buffer.

## WriteData Class

The `WriteData` class provides methods for reading and writing data to disk. It takes a `StreamerFactory` and a `Datapoint` object as input, and outputs a compressed representation of the data. The class also includes a `readDataPoints` function for reading data from disk.

# Usage
## Creating a new WriteData instance
To create a new WriteData instance, you need to provide it with a few dependencies:

* An LRWStrategyFactory instance, which determines the compression strategy used when writing data
* A FileRefFactory instance, which creates FileRef instances used to represent files and directories
* A CurrentDateGetter instance, which provides the current date for use in directory names when writing data
* A FileRef instance representing the main directory where data is stored

```kotlin
val writeData = WriteData(
    lRWStrategy = SomeLRWStrategyFactory(),
    fFactory = SomeFileRefFactory(),
    currentDateGetter = SomeCurrentDateGetter(),
    mainDir = FileRef("/path/to/main/dir")
)
```

# Writing data points

To write a new data point, use the write method, which takes a StreamerFactory instance and a Datapoint instance:

```kotlin
val datapoint = Datapoint(header = SomeHeader(...), data = SomeData(...))
writeData.write(streamerFactory = SomeStreamerFactory(), datapoint = datapoint)
```
# Reading data points
To read data points from disk, use the readDataPoints method, which takes a list of StreamerFactory instances:

```kotlin
val streamMapperList = listOf(SomeStreamerFactory1(), SomeStreamerFactory2(), ...)
val logDataList = writeData.readDataPoints(streamMapperList)
```

# Defining new data types
To define a new data type, implement the Datapoint and StreamMapper interfaces:

```kotlin
data class MyData(val value: Int)

class MyDataMapper : StreamMapper<MyData> {
    override val size: Int = 4

    override fun toOutput(t: MyData, dataOutputStream: NativeDataReceiver) {
        dataOutputStream.writeInt(t.value)
    }

    override fun toElement(byteBuffer: NativeDataGetter): MyData {
        return MyData(byteBuffer.int)
    }
}

data class MyDatapoint(
    val header: Header,
    val data: List<MyData>
) : Datapoint<List<MyData>> {
    override val streamMapper: StreamMapper<List<MyData>> = ListStreamMapper(MyDataMapper())
}

```



License
This library is licensed under the Apache license. See LICENSE.txt for more details.


