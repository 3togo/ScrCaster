package io.github.togo3.scrcaster.services

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import io.github.togo3.scrcaster.R
import io.github.togo3.scrcaster.nativecore.AdbSocketStream
import io.github.togo3.scrcaster.nativecore.NativeAdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class RemoteFileKind {
    Directory,
    Image,
    Video,
    Audio,
    Archive,
    Apk,
    Text,
    Link,
    Other,
}

data class RemoteFileEntry(
    val inode: Long?,
    val permissions: String,
    val hardLinks: Int?,
    val owner: String?,
    val group: String?,
    val sizeBytes: Long?,
    val modifiedAt: LocalDateTime?,
    val name: String,
    val fullPath: String,
    val symlinkTarget: String? = null,
    val kind: RemoteFileKind,
    val isDirectory: Boolean,
)

data class RemoteFileStat(
    val path: String,
    val name: String,
    val typeLabel: String?,
    val sizeBytes: Long?,
    val blocks: Long?,
    val ioBlockBytes: Long?,
    val inode: Long?,
    val hardLinks: Int?,
    val octalMode: String?,
    val permissions: String?,
    val uid: Long?,
    val uidName: String?,
    val gid: Long?,
    val gidName: String?,
    val accessTime: String?,
    val modifyTime: String?,
    val changeTime: String?,
    val device: String?,
    val deviceType: String?,
    val symlinkTarget: String?,
    val rawOutput: String,
) {
    val title: String
        get() = name.ifBlank { path }
}

data class DirectoryDownloadSnapshot(
    val remoteRootPath: String,
    val totalBytes: Long?,
    val directories: List<String>,
    val files: List<String>,
)
