package io.github.togo3.scrcaster.connection

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AndroidConnectionPreferencesTest {

    private class FakeSharedPreferences : SharedPreferences {
        private val map = HashMap<String, Any?>()

        override fun getAll(): Map<String, *> = map
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = map[key] as? Set<String> ?: defValues
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    }

    private class FakeEditor(private val map: HashMap<String, Any?>) : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor { map[key] = value; return this }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { map[key] = values; return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { map[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { map[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { map[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { map[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { map.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { map.clear(); return this }
        override fun commit(): Boolean = true
        override fun apply() {}
    }

    private val prefs = FakeSharedPreferences()

    @Before
    fun resetPrefs() {
        prefs.edit().clear().commit()
    }

    @Test
    fun loadDefaultsWhenEmpty() {
        val store = createPrefsStore()
        val loaded = store.load()
        assertNull(loaded.lastEndpoint)
        assertTrue(loaded.rememberedEndpoints.isEmpty())
        assertTrue(loaded.playback.audio)
        assertEquals("LONG_EDGE", loaded.playback.renderFit)
        assertEquals("DEVICE", loaded.playback.aspectRatio)
        assertEquals("", loaded.playback.customRatio)
    }

    @Test
    fun saveAndLoadRoundTripsEndpoint() {
        val store = createPrefsStore()
        val original = ConnectionPreferences(
            lastEndpoint = ConnectionEndpoint("192.168.1.100", 5555),
            rememberedEndpoints = listOf(ConnectionEndpoint("192.168.1.100", 5555)),
            playback = PlaybackPreferences(audio = true, renderFit = "FIT", aspectRatio = "DEVICE", customRatio = ""),
        )
        store.save(original)
        val loaded = createPrefsStore().load()
        assertEquals(ConnectionEndpoint("192.168.1.100", 5555), loaded.lastEndpoint)
        assertEquals(listOf(ConnectionEndpoint("192.168.1.100", 5555)), loaded.rememberedEndpoints)
    }

    @Test
    fun saveAndLoadRoundTripsPlaybackPreferences() {
        val store = createPrefsStore()
        val original = ConnectionPreferences(
            lastEndpoint = null,
            rememberedEndpoints = emptyList(),
            playback = PlaybackPreferences(audio = false, renderFit = "STRETCH", aspectRatio = "DEVICE", customRatio = "21:9"),
        )
        store.save(original)
        val loaded = createPrefsStore().load()
        assertFalse(loaded.playback.audio)
        assertEquals("STRETCH", loaded.playback.renderFit)
        assertEquals("DEVICE", loaded.playback.aspectRatio)
        assertEquals("21:9", loaded.playback.customRatio)
    }

    @Test
    fun loadParsesLegacyHostPortFormat() {
        prefs.edit().putString("host", "10.0.0.5").putString("port", "37123").commit()
        val store = createPrefsStore()
        val loaded = store.load()
        assertEquals(ConnectionEndpoint("10.0.0.5", 37123), loaded.lastEndpoint)
        assertTrue(loaded.rememberedEndpoints.contains(ConnectionEndpoint("10.0.0.5", 37123)))
    }

    @Test
    fun loadIgnoresInvalidLegacyPort() {
        prefs.edit().putString("host", "10.0.0.5").putString("port", "99999").commit()
        val store = createPrefsStore()
        val loaded = store.load()
        assertNull(loaded.lastEndpoint)
    }

    @Test
    fun loadParsesRememberedDevicesList() {
        prefs.edit().putString("devices", "192.168.1.1:5555\n192.168.1.2:4444").commit()
        val store = createPrefsStore()
        val loaded = store.load()
        assertEquals(2, loaded.rememberedEndpoints.size)
        assertTrue(loaded.rememberedEndpoints.contains(ConnectionEndpoint("192.168.1.1", 5555)))
        assertTrue(loaded.rememberedEndpoints.contains(ConnectionEndpoint("192.168.1.2", 4444)))
    }

    @Test
    fun loadDeduplicatesRememberedDevices() {
        prefs.edit().putString("devices", "192.168.1.1:5555\n192.168.1.1:5555\n192.168.1.2:4444").commit()
        val store = createPrefsStore()
        val loaded = store.load()
        assertEquals(2, loaded.rememberedEndpoints.size)
    }

    @Test
    fun loadFallsBackToLegacyEndpointWhenNoDevicesList() {
        prefs.edit().putString("host", "172.16.0.1").putString("port", "5555").commit()
        val store = createPrefsStore()
        val loaded = store.load()
        assertEquals(listOf(ConnectionEndpoint("172.16.0.1", 5555)), loaded.rememberedEndpoints)
    }

    @Test
    fun loadUsesSelectedEndpointWhenInRememberedList() {
        prefs.edit()
            .putString("devices", "192.168.1.1:5555\n192.168.1.2:4444")
            .putString("selected", "192.168.1.2:4444")
            .commit()
        val store = createPrefsStore()
        val loaded = store.load()
        assertEquals(ConnectionEndpoint("192.168.1.2", 4444), loaded.lastEndpoint)
    }

    @Test
    fun loadIgnoresSelectedEndpointNotInRememberedList() {
        prefs.edit()
            .putString("devices", "192.168.1.1:5555")
            .putString("selected", "192.168.1.99:9999")
            .commit()
        val store = createPrefsStore()
        val loaded = store.load()
        assertEquals(ConnectionEndpoint("192.168.1.1", 5555), loaded.lastEndpoint)
    }

    @Test
    fun loadInvalidRenderFitFallsBackToDefault() {
        prefs.edit().putString("renderFit", "INVALID").commit()
        val store = createPrefsStore()
        val loaded = store.load()
        assertEquals("LONG_EDGE", loaded.playback.renderFit)
    }

    @Test
    fun loadInvalidAspectRatioFallsBackToDefault() {
        prefs.edit().putString("aspectRatio", "BOGUS").commit()
        val store = createPrefsStore()
        val loaded = store.load()
        assertEquals("DEVICE", loaded.playback.aspectRatio)
    }

    @Test
    fun savePersistsAllFields() {
        val store = createPrefsStore()
        val original = ConnectionPreferences(
            lastEndpoint = ConnectionEndpoint("1.2.3.4", 1234),
            rememberedEndpoints = listOf(ConnectionEndpoint("1.2.3.4", 1234), ConnectionEndpoint("5.6.7.8", 5678)),
            playback = PlaybackPreferences(audio = false, renderFit = "CROP", aspectRatio = "4:3", customRatio = ""),
        )
        store.save(original)
        assertEquals("1.2.3.4", prefs.getString("host", ""))
        assertEquals("1234", prefs.getString("port", ""))
        assertEquals("1.2.3.4:1234", prefs.getString("selected", ""))
        assertEquals("1.2.3.4:1234\n5.6.7.8:5678", prefs.getString("devices", ""))
        assertFalse(prefs.getBoolean("audio", true))
        assertEquals("CROP", prefs.getString("renderFit", ""))
        assertEquals("4:3", prefs.getString("aspectRatio", ""))
    }

    private fun createPrefsStore(): AndroidConnectionPreferences {
        val context = org.mockito.Mockito.mock(Context::class.java)
        org.mockito.Mockito.`when`(context.getSharedPreferences("tv_connection", Context.MODE_PRIVATE)).thenReturn(prefs)
        return AndroidConnectionPreferences(context)
    }
}
