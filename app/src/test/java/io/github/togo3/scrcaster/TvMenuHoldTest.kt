package io.github.togo3.scrcaster

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TvMenuHoldTest {
    private val hold = TvMenuHold()
    private val ok = KeyEvent.KEYCODE_DPAD_CENTER

    @Test fun shortPressOnlyClicksOnRelease() {
        assertEquals(TvMenuHold.Result.NONE, hold.key(KeyEvent.ACTION_DOWN, ok, 100))
        assertEquals(TvMenuHold.Result.TAP, hold.key(KeyEvent.ACTION_UP, ok, 200))
        assertEquals(TvMenuHold.Result.NONE, hold.key(KeyEvent.ACTION_UP, ok, 201))
    }

    @Test fun holdWithoutRepeatEventsOpensMenu() {
        hold.key(KeyEvent.ACTION_DOWN, ok, 100)
        assertEquals(TvMenuHold.Result.MENU, hold.key(KeyEvent.ACTION_UP, ok, 800))
    }

    @Test fun repeatsDoNotResetHoldOrSendClicks() {
        hold.key(KeyEvent.ACTION_DOWN, ok, 100)
        assertEquals(TvMenuHold.Result.NONE, hold.key(KeyEvent.ACTION_DOWN, ok, 600, repeat = 1))
        assertEquals(TvMenuHold.Result.MENU, hold.key(KeyEvent.ACTION_UP, ok, 900))
    }

    @Test fun focusLossAndCanceledReleaseDoNotClick() {
        hold.key(KeyEvent.ACTION_DOWN, ok, 100)
        hold.cancel()
        assertEquals(TvMenuHold.Result.NONE, hold.key(KeyEvent.ACTION_UP, ok, 200))
        hold.key(KeyEvent.ACTION_DOWN, ok, 300)
        assertEquals(TvMenuHold.Result.NONE, hold.key(KeyEvent.ACTION_UP, ok, 400, canceled = true))
    }

    @Test fun platformLongPressSignalAlsoOpensMenu() {
        hold.key(KeyEvent.ACTION_DOWN, ok, 100)
        hold.key(KeyEvent.ACTION_DOWN, ok, 600, repeat = 1, longPress = true)
        assertEquals(TvMenuHold.Result.MENU, hold.key(KeyEvent.ACTION_UP, ok, 600))
    }

    @Test fun enterWorksAndUnmatchedReleaseIsIgnored() {
        hold.key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 100)
        assertEquals(TvMenuHold.Result.NONE, hold.key(KeyEvent.ACTION_UP, ok, 200))
        assertEquals(TvMenuHold.Result.MENU, hold.key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 900))
    }
}
