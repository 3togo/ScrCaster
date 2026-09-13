package io.github.togo3.scrcaster.util

import android.content.Intent
import android.os.Build
import android.os.Parcelable

/**
 * Returns the [Parcelable] extra associated with [name], using the typed
 * [Intent.getParcelableExtra] overload on Android 13 (API 33+) and falling back
 * to the deprecated untyped overload on older versions.
 */
inline fun <reified T : Parcelable> Intent.parcelableExtra(name: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }
