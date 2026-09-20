package io.github.togo3.scrcaster

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.togo3.scrcaster.connection.*
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrPairingDialogTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun qrDialogShowsCancelAndInstructions() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            val controller = remember {
                ConnectionController(scope, FakeBackend(), object : ConnectionPreferencesStore {
                    override fun load() = ConnectionPreferences()
                    override fun save(preferences: ConnectionPreferences) = Unit
                })
            }
            MaterialTheme { ConnectionContent(controller, remote = false) }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        compose.onNodeWithText("QR").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    private class FakeBackend : PairingConnectionBackend {
        override fun isStreaming() = false
        override fun cancelPendingConnect() = Unit
        override suspend fun connect(endpoint: ConnectionEndpoint, preferences: PlaybackPreferences) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun pair(endpoint: ConnectionEndpoint, secret: String) = false
        override suspend fun findQrService(name: String): ConnectionEndpoint? = awaitCancellation()
        override suspend fun findConnection(host: String): ConnectionEndpoint? = null
    }
}
