package io.github.togo3.scrcaster.scrcpy

import com.github.promeg.pinyinhelper.Pinyin
import io.github.togo3.scrcaster.scrcpy.Shared.*
import java.util.Locale

internal object ScrcpyOutputParser {

    private val VIDEO_ENCODER_INFO_REGEX =
        Regex("""--video-codec=(\S+)\s+--video-encoder=(\S+)\s+\((hw|sw)\)(\s+\[vendor])?(?:\s+\(alias for (\S+)\))?""")
    private val AUDIO_ENCODER_INFO_REGEX =
        Regex("""--audio-codec=(\S+)\s+--audio-encoder=(\S+)\s+\((hw|sw)\)(\s+\[vendor])?(?:\s+\(alias for (\S+)\))?""")
    private val DISPLAY_REGEX =
        Regex("""--display-id=(\d+)\s+\((\d+)x(\d+)\)""")
    private val CAMERA_SIZE_REGEX =
        Regex("""\b([1-9][0-9]{1,4}x[1-9][0-9]{1,4})\b""")
    private val CAMERA_INFO_REGEX =
        Regex("""--camera-id=(\S+)\s+\(([^,]+),\s*([0-9]+x[0-9]+),\s*fps=\[([0-9,\s]+)\]\)""")
    private val APP_REGEX =
        Regex("""^\s*([*-])\s+(.+?)\s{2,}([A-Za-z0-9._]+)\s*$""", RegexOption.MULTILINE)
    private val RECENT_TASK_PACKAGE_REGEX =
        Regex("""\bcmp=([A-Za-z0-9._]+)/""")

    fun parseEncoders(output: String): Pair<List<EncoderInfo>, List<EncoderInfo>> {
        val videoInfos = linkedMapOf<String, EncoderInfo>()
        val audioInfos = linkedMapOf<String, EncoderInfo>()

        VIDEO_ENCODER_INFO_REGEX.findAll(output).forEach { match ->
            val info = EncoderInfo(
                codec = Codec.fromString(match.groupValues[1], Codec.Type.VIDEO),
                id = match.groupValues[2],
                type = if (match.groupValues[3] == EncoderType.HARDWARE.s) {
                    EncoderType.HARDWARE
                } else {
                    EncoderType.SOFTWARE
                },
                isVendor = match.groupValues[4].isNotBlank(),
                aliasOf = match.groupValues[5].ifBlank { null },
            )
            videoInfos.putIfAbsent(info.id, info)
        }

        AUDIO_ENCODER_INFO_REGEX.findAll(output).forEach { match ->
            val info = EncoderInfo(
                codec = Codec.fromString(match.groupValues[1], Codec.Type.AUDIO),
                id = match.groupValues[2],
                type = if (match.groupValues[3] == EncoderType.HARDWARE.s) {
                    EncoderType.HARDWARE
                } else {
                    EncoderType.SOFTWARE
                },
                isVendor = match.groupValues[4].isNotBlank(),
                aliasOf = match.groupValues[5].ifBlank { null },
            )
            audioInfos.putIfAbsent(info.id, info)
        }

        return videoInfos.values.toList() to audioInfos.values.toList()
    }

    fun parseDisplays(output: String): List<DisplayInfo> {
        val displays = LinkedHashSet<DisplayInfo>()
        DISPLAY_REGEX.findAll(output).forEach { match ->
            displays.add(
                DisplayInfo(
                    id = match.groupValues[1].toInt(),
                    width = match.groupValues[2].toInt(),
                    height = match.groupValues[3].toInt(),
                ),
            )
        }
        return displays.toList()
    }

    fun parseCameras(output: String): List<CameraInfo> {
        val cameras = LinkedHashSet<CameraInfo>()
        CAMERA_INFO_REGEX.findAll(output).forEach { match ->
            val facing = match.groupValues[2]
            val activeSize = match.groupValues[3]
            val fpsValues = match.groupValues[4]
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }

            cameras.add(
                CameraInfo(
                    id = match.groupValues[1],
                    facing = CameraFacing.fromString(facing),
                    activeSize = activeSize,
                    fps = fpsValues.map(Int::toUShort),
                ),
            )
        }
        return cameras.toList()
    }

    fun parseCameraSizes(output: String): List<String> {
        val sizes = LinkedHashSet<String>()
        CAMERA_SIZE_REGEX.findAll(output).forEach { match ->
            sizes.add(match.groupValues[1])
        }
        return sizes.toList()
    }

    fun parseApps(output: String): List<AppInfo> {
        val apps = LinkedHashSet<AppInfo>()
        APP_REGEX.findAll(output).forEach { match ->
            apps.add(
                AppInfo(
                    system = match.groupValues[1] == "*",
                    label = match.groupValues[2].trim(),
                    packageName = match.groupValues[3].trim(),
                ),
            )
        }
        return apps.toList().sortedBy { appSortKey(it) }
    }

    fun parseRecentTasks(output: String): List<RecentTaskInfo> {
        val packages = LinkedHashSet<String>()
        RECENT_TASK_PACKAGE_REGEX.findAll(output).forEach { match ->
            val packageName = match.groupValues[1].trim()
            if (packageName.isNotBlank()) {
                packages += packageName
            }
        }
        return packages.map { packageName ->
            RecentTaskInfo(
                packageName = packageName,
            )
        }
    }

    private fun appSortKey(app: AppInfo): String {
        val label = app.label?.takeIf { it.isNotBlank() } ?: app.packageName
        val tokens = label.map { char ->
            when {
                char.code <= 0x7F -> AppSortToken(
                    priority = 0,
                    value = char.lowercaseChar().toString(),
                )

                Pinyin.isChinese(char) -> AppSortToken(
                    priority = 1,
                    value = Pinyin.toPinyin(char).lowercase(Locale.ROOT),
                )

                else -> AppSortToken(
                    priority = 2,
                    value = char.lowercaseChar().toString(),
                )
            }
        }
        val firstToken = tokens.firstOrNull { it.value.any(Char::isLetterOrDigit) }
            ?: tokens.firstOrNull()
        val firstLetter = firstToken
            ?.value
            ?.firstOrNull(Char::isLetterOrDigit)
            ?: Char.MAX_VALUE

        return buildString {
            append(firstLetter)
            append('\u0000')
            append(firstToken?.priority ?: 2)
            append('\u0000')
            tokens.forEach { token ->
                append(token.value)
                append('\u0000')
            }
            append('\u0001')
            append(app.packageName.lowercase(Locale.ROOT))
        }
    }

    private data class AppSortToken(
        val priority: Int,
        val value: String,
    )
}