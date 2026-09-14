package io.github.togo3.scrcaster.scrcpy

import io.github.togo3.scrcaster.scrcpy.Shared.*

data class EncoderInfo(
    val codec: Codec,
    val id: String,
    val type: EncoderType,
    val isVendor: Boolean,
    val aliasOf: String? = null,
)

data class CameraInfo(
    val id: String,
    val facing: CameraFacing,
    val activeSize: String,
    val fps: List<UShort>,
)

data class DisplayInfo(
    val id: Int,
    val width: Int,
    val height: Int,
)

data class AppInfo(
    val system: Boolean?,
    val label: String?,
    val packageName: String,
)

data class RecentTaskInfo(
    val packageName: String,
    val appLabel: String? = null,
)