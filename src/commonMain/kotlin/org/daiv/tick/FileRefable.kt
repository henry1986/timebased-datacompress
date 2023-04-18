package org.daiv.tick

interface FileRefable {
    val file: FileRef
}

data class FileDataInfo<out T>(
    override val start: Long,
    override val end: Long,
    override val isCurrent: Boolean,
    val folderFile: T
) : CurrentDataCollection

fun <T : FileRefable> FileDataInfo<T>.toWithRef() = FileDataInfoWithRef(start, end, isCurrent, folderFile)

data class FileDataInfoWithRef<out T : FileRefable>(
    override val start: Long,
    override val end: Long,
    override val isCurrent: Boolean,
    val folderFile: T
) : DataCollectionWithFileRef, FileRefable by folderFile


interface DataCollectionWithFileRef : CurrentDataCollection, FileRefable
