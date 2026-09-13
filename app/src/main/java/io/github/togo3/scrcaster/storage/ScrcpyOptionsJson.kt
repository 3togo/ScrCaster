package io.github.togo3.scrcaster.storage

import android.content.Context
import android.os.Parcelable
import androidx.datastore.preferences.core.*
import io.github.togo3.scrcaster.R
import io.github.togo3.scrcaster.scrcpy.ClientOptions
import io.github.togo3.scrcaster.scrcpy.ClientOptions.KeyInjectMode
import io.github.togo3.scrcaster.scrcpy.ClientOptions.RecordFormat
import io.github.togo3.scrcaster.scrcpy.ScrcpyAspectRatio
import io.github.togo3.scrcaster.scrcpy.Shared.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

internal fun encodeBundleToJson(bundle: ScrcpyOptions.Bundle): JSONObject =
    JSONObject()
        .put("crop", bundle.crop)
        .put("recordFilename", bundle.recordFilename)
        .put("videoCodecOptions", bundle.videoCodecOptions)
        .put("audioCodecOptions", bundle.audioCodecOptions)
        .put("videoEncoder", bundle.videoEncoder)
        .put("audioEncoder", bundle.audioEncoder)
        .put("cameraId", bundle.cameraId)
        .put("cameraSize", bundle.cameraSize)
        .put("cameraSizeCustom", bundle.cameraSizeCustom)
        .put("cameraSizeUseCustom", bundle.cameraSizeUseCustom)
        .put("cameraAr", bundle.cameraAr)
        .put("cameraZoom", bundle.cameraZoom)
        .put("cameraFps", bundle.cameraFps)
        .put("logLevel", bundle.logLevel)
        .put("videoCodec", bundle.videoCodec)
        .put("audioCodec", bundle.audioCodec)
        .put("videoSource", bundle.videoSource)
        .put("audioSource", bundle.audioSource)
        .put("recordFormat", bundle.recordFormat)
        .put("cameraFacing", bundle.cameraFacing)
        .put("minSizeAlignment", bundle.minSizeAlignment)
        .put("maxSize", bundle.maxSize)
        .put("videoBitRate", bundle.videoBitRate)
        .put("audioBitRate", bundle.audioBitRate)
        .put("maxFps", bundle.maxFps)
        .put("angle", bundle.angle)
        .put("captureOrientation", bundle.captureOrientation)
        .put("captureOrientationLock", bundle.captureOrientationLock)
        .put("displayOrientation", bundle.displayOrientation)
        .put("recordOrientation", bundle.recordOrientation)
        .put("displayImePolicy", bundle.displayImePolicy)
        .put("displayId", bundle.displayId)
        .put("screenOffTimeout", bundle.screenOffTimeout)
        .put("showTouches", bundle.showTouches)
        .put("fullscreen", bundle.fullscreen)
        .put("control", bundle.control)
        .put("videoPlayback", bundle.videoPlayback)
        .put("audioPlayback", bundle.audioPlayback)
        .put("turnScreenOff", bundle.turnScreenOff)
        .put("keyInjectMode", bundle.keyInjectMode)
        .put("forwardKeyRepeat", bundle.forwardKeyRepeat)
        .put("stayAwake", bundle.stayAwake)
        .put("disableScreensaver", bundle.disableScreensaver)
        .put("powerOffOnClose", bundle.powerOffOnClose)
        .put("legacyPaste", bundle.legacyPaste)
        .put("clipboardAutosync", bundle.clipboardAutosync)
        .put("downsizeOnError", bundle.downsizeOnError)
        .put("mouseHover", bundle.mouseHover)
        .put("gamepad", bundle.gamepad)
        .put("cleanup", bundle.cleanup)
        .put("powerOn", bundle.powerOn)
        .put("video", bundle.video)
        .put("audio", bundle.audio)
        .put("requireAudio", bundle.requireAudio)
        .put("killAdbOnClose", bundle.killAdbOnClose)
        .put("cameraHighSpeed", bundle.cameraHighSpeed)
        .put("list", bundle.list)
        .put("audioDup", bundle.audioDup)
        .put("newDisplay", bundle.newDisplay)
        .put("startApp", bundle.startApp)
        .put("startAppCustom", bundle.startAppCustom)
        .put("startAppUseCustom", bundle.startAppUseCustom)
        .put("vdDestroyContent", bundle.vdDestroyContent)
        .put("vdSystemDecorations", bundle.vdSystemDecorations)
        .put("cameraTorch", bundle.cameraTorch)
        .put("keepActive", bundle.keepActive)
        .put("flexDisplay", bundle.flexDisplay)
        .put("ignoreVideoEncoderConstraints", bundle.ignoreVideoEncoderConstraints)
        .put("renderFit", bundle.renderFit)
        .put("aspectRatio", bundle.aspectRatio)
        .put("aspectRatioCustom", bundle.aspectRatioCustom)

internal fun decodeBundleFromJson(bundleJson: JSONObject?): ScrcpyOptions.Bundle {
    val json = bundleJson ?: return ScrcpyOptions.defaultBundle()
    return ScrcpyOptions.defaultBundle().copy(
        crop = json.optStringOrDefault(
            "crop",
            ScrcpyOptions.CROP.defaultValue,
        ),
        recordFilename = json.optStringOrDefault(
            "recordFilename",
            ScrcpyOptions.RECORD_FILENAME.defaultValue,
        ),
        videoCodecOptions = json.optStringOrDefault(
            "videoCodecOptions",
            ScrcpyOptions.VIDEO_CODEC_OPTIONS.defaultValue,
        ),
        audioCodecOptions = json.optStringOrDefault(
            "audioCodecOptions",
            ScrcpyOptions.AUDIO_CODEC_OPTIONS.defaultValue,
        ),
        videoEncoder = json.optStringOrDefault(
            "videoEncoder",
            ScrcpyOptions.VIDEO_ENCODER.defaultValue,
        ),
        audioEncoder = json.optStringOrDefault(
            "audioEncoder",
            ScrcpyOptions.AUDIO_ENCODER.defaultValue,
        ),
        cameraId = json.optStringOrDefault(
            "cameraId",
            ScrcpyOptions.CAMERA_ID.defaultValue,
        ),
        cameraSize = json.optStringOrDefault(
            "cameraSize",
            ScrcpyOptions.CAMERA_SIZE.defaultValue,
        ),
        cameraSizeCustom = json.optStringOrDefault(
            "cameraSizeCustom",
            ScrcpyOptions.CAMERA_SIZE_CUSTOM.defaultValue,
        ),
        cameraSizeUseCustom = json.optBooleanOrDefault(
            "cameraSizeUseCustom",
            ScrcpyOptions.CAMERA_SIZE_USE_CUSTOM.defaultValue,
        ),
        cameraAr = json.optStringOrDefault(
            "cameraAr",
            ScrcpyOptions.CAMERA_AR.defaultValue,
        ),
        cameraZoom = json.optStringOrDefault(
            "cameraZoom",
            ScrcpyOptions.CAMERA_ZOOM.defaultValue,
        ),
        cameraFps = json.optIntOrDefault(
            "cameraFps",
            ScrcpyOptions.CAMERA_FPS.defaultValue,
        ),
        logLevel = json.optStringOrDefault(
            "logLevel",
            ScrcpyOptions.LOG_LEVEL.defaultValue,
        ),
        videoCodec = json.optStringOrDefault(
            "videoCodec",
            ScrcpyOptions.VIDEO_CODEC.defaultValue,
        ),
        audioCodec = json.optStringOrDefault(
            "audioCodec",
            ScrcpyOptions.AUDIO_CODEC.defaultValue,
        ),
        videoSource = json.optStringOrDefault(
            "videoSource",
            ScrcpyOptions.VIDEO_SOURCE.defaultValue,
        ),
        audioSource = json.optStringOrDefault(
            "audioSource",
            ScrcpyOptions.AUDIO_SOURCE.defaultValue,
        ),
        recordFormat = json.optStringOrDefault(
            "recordFormat",
            ScrcpyOptions.RECORD_FORMAT.defaultValue,
        ),
        cameraFacing = json.optStringOrDefault(
            "cameraFacing",
            ScrcpyOptions.CAMERA_FACING.defaultValue,
        ),
        minSizeAlignment = json.optIntOrDefault(
            "minSizeAlignment",
            ScrcpyOptions.MIN_SIZE_ALIGNMENT.defaultValue,
        ),
        maxSize = json.optIntOrDefault(
            "maxSize",
            ScrcpyOptions.MAX_SIZE.defaultValue,
        ),
        videoBitRate = json.optIntOrDefault(
            "videoBitRate",
            ScrcpyOptions.VIDEO_BIT_RATE.defaultValue,
        ),
        audioBitRate = json.optIntOrDefault(
            "audioBitRate",
            ScrcpyOptions.AUDIO_BIT_RATE.defaultValue,
        ),
        maxFps = json.optStringOrDefault(
            "maxFps",
            ScrcpyOptions.MAX_FPS.defaultValue,
        ),
        angle = json.optStringOrDefault(
            "angle",
            ScrcpyOptions.ANGLE.defaultValue,
        ),
        captureOrientation = json.optIntOrDefault(
            "captureOrientation",
            ScrcpyOptions.CAPTURE_ORIENTATION.defaultValue,
        ),
        captureOrientationLock = json.optStringOrDefault(
            "captureOrientationLock",
            ScrcpyOptions.CAPTURE_ORIENTATION_LOCK.defaultValue,
        ),
        displayOrientation = json.optIntOrDefault(
            "displayOrientation",
            ScrcpyOptions.DISPLAY_ORIENTATION.defaultValue,
        ),
        recordOrientation = json.optIntOrDefault(
            "recordOrientation",
            ScrcpyOptions.RECORD_ORIENTATION.defaultValue,
        ),
        displayImePolicy = json.optStringOrDefault(
            "displayImePolicy",
            ScrcpyOptions.DISPLAY_IME_POLICY.defaultValue,
        ),
        displayId = json.optIntOrDefault(
            "displayId",
            ScrcpyOptions.DISPLAY_ID.defaultValue,
        ),
        screenOffTimeout = json.optLongOrDefault(
            "screenOffTimeout",
            ScrcpyOptions.SCREEN_OFF_TIMEOUT.defaultValue,
        ),
        showTouches = json.optBooleanOrDefault(
            "showTouches",
            ScrcpyOptions.SHOW_TOUCHES.defaultValue,
        ),
        fullscreen = json.optBooleanOrDefault(
            "fullscreen",
            ScrcpyOptions.FULLSCREEN.defaultValue,
        ),
        control = json.optBooleanOrDefault(
            "control",
            ScrcpyOptions.CONTROL.defaultValue,
        ),
        videoPlayback = json.optBooleanOrDefault(
            "videoPlayback",
            ScrcpyOptions.VIDEO_PLAYBACK.defaultValue,
        ),
        audioPlayback = json.optBooleanOrDefault(
            "audioPlayback",
            ScrcpyOptions.AUDIO_PLAYBACK.defaultValue,
        ),
        turnScreenOff = json.optBooleanOrDefault(
            "turnScreenOff",
            ScrcpyOptions.TURN_SCREEN_OFF.defaultValue,
        ),
        keyInjectMode = json.optStringOrDefault(
            "keyInjectMode",
            ScrcpyOptions.KEY_INJECT_MODE.defaultValue,
        ),
        forwardKeyRepeat = json.optBooleanOrDefault(
            "forwardKeyRepeat",
            ScrcpyOptions.FORWARD_KEY_REPEAT.defaultValue,
        ),
        stayAwake = json.optBooleanOrDefault(
            "stayAwake",
            ScrcpyOptions.STAY_AWAKE.defaultValue,
        ),
        disableScreensaver = json.optBooleanOrDefault(
            "disableScreensaver",
            ScrcpyOptions.DISABLE_SCREENSAVER.defaultValue,
        ),
        powerOffOnClose = json.optBooleanOrDefault(
            "powerOffOnClose",
            ScrcpyOptions.POWER_OFF_ON_CLOSE.defaultValue,
        ),
        legacyPaste = json.optBooleanOrDefault(
            "legacyPaste",
            ScrcpyOptions.LEGACY_PASTE.defaultValue,
        ),
        clipboardAutosync = json.optBooleanOrDefault(
            "clipboardAutosync",
            ScrcpyOptions.CLIPBOARD_AUTOSYNC.defaultValue,
        ),
        downsizeOnError = json.optBooleanOrDefault(
            "downsizeOnError",
            ScrcpyOptions.DOWNSIZE_ON_ERROR.defaultValue,
        ),
        mouseHover = json.optBooleanOrDefault(
            "mouseHover",
            ScrcpyOptions.MOUSE_HOVER.defaultValue,
        ),
        gamepad = json.optBooleanOrDefault(
            "gamepad",
            ScrcpyOptions.GAMEPAD.defaultValue,
        ),
        cleanup = json.optBooleanOrDefault(
            "cleanup",
            ScrcpyOptions.CLEANUP.defaultValue,
        ),
        powerOn = json.optBooleanOrDefault(
            "powerOn",
            ScrcpyOptions.POWER_ON.defaultValue,
        ),
        video = json.optBooleanOrDefault(
            "video",
            ScrcpyOptions.VIDEO.defaultValue,
        ),
        audio = json.optBooleanOrDefault(
            "audio",
            ScrcpyOptions.AUDIO.defaultValue,
        ),
        requireAudio = json.optBooleanOrDefault(
            "requireAudio",
            ScrcpyOptions.REQUIRE_AUDIO.defaultValue,
        ),
        killAdbOnClose = json.optBooleanOrDefault(
            "killAdbOnClose",
            ScrcpyOptions.KILL_ADB_ON_CLOSE.defaultValue,
        ),
        cameraHighSpeed = json.optBooleanOrDefault(
            "cameraHighSpeed",
            ScrcpyOptions.CAMERA_HIGH_SPEED.defaultValue,
        ),
        list = json.optStringOrDefault(
            "list",
            ScrcpyOptions.LIST.defaultValue,
        ),
        audioDup = json.optBooleanOrDefault(
            "audioDup",
            ScrcpyOptions.AUDIO_DUP.defaultValue,
        ),
        newDisplay = json.optStringOrDefault(
            "newDisplay",
            ScrcpyOptions.NEW_DISPLAY.defaultValue,
        ),
        startApp = json.optStringOrDefault(
            "startApp",
            ScrcpyOptions.START_APP.defaultValue,
        ),
        startAppCustom = json.optStringOrDefault(
            "startAppCustom",
            ScrcpyOptions.START_APP_CUSTOM.defaultValue,
        ),
        startAppUseCustom = json.optBooleanOrDefault(
            "startAppUseCustom",
            ScrcpyOptions.START_APP_USE_CUSTOM.defaultValue,
        ),
        vdDestroyContent = json.optBooleanOrDefault(
            "vdDestroyContent",
            ScrcpyOptions.VD_DESTROY_CONTENT.defaultValue,
        ),
        vdSystemDecorations = json.optBooleanOrDefault(
            "vdSystemDecorations",
            ScrcpyOptions.VD_SYSTEM_DECORATIONS.defaultValue,
        ),
        cameraTorch = json.optBooleanOrDefault(
            "cameraTorch",
            ScrcpyOptions.CAMERA_TORCH.defaultValue,
        ),
        keepActive = json.optBooleanOrDefault(
            "keepActive",
            ScrcpyOptions.KEEP_ACTIVE.defaultValue,
        ),
        flexDisplay = json.optBooleanOrDefault(
            "flexDisplay",
            ScrcpyOptions.FLEX_DISPLAY.defaultValue,
        ),
        ignoreVideoEncoderConstraints = json.optBooleanOrDefault(
            "ignoreVideoEncoderConstraints",
            ScrcpyOptions.IGNORE_VIDEO_ENCODER_CONSTRAINTS.defaultValue,
        ),
        renderFit = json.optStringOrDefault(
            "renderFit",
            ScrcpyOptions.RENDER_FIT.defaultValue,
        ),
        aspectRatio = json.optStringOrDefault(
            "aspectRatio",
            ScrcpyOptions.ASPECT_RATIO.defaultValue,
        ),
        aspectRatioCustom = json.optStringOrDefault(
            "aspectRatioCustom",
            ScrcpyOptions.ASPECT_RATIO_CUSTOM.defaultValue,
        ),
    )
}

private fun JSONObject.optStringOrDefault(key: String, defaultValue: String): String =
    if (has(key) && !isNull(key)) optString(key, defaultValue) else defaultValue

private fun JSONObject.optBooleanOrDefault(key: String, defaultValue: Boolean): Boolean =
    if (has(key) && !isNull(key)) optBoolean(key, defaultValue) else defaultValue

private fun JSONObject.optIntOrDefault(key: String, defaultValue: Int): Int =
    if (has(key) && !isNull(key)) optInt(key, defaultValue) else defaultValue

private fun JSONObject.optLongOrDefault(key: String, defaultValue: Long): Long =
    if (has(key) && !isNull(key)) optLong(key, defaultValue) else defaultValue
