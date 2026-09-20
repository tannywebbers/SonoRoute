package com.example

import android.media.AudioDeviceInfo
import com.example.audio.model.DeviceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDeviceMapperTest {

    @Test
    fun testCategoryClassification() {
        // Built-in checks
        val speakerType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        assertEquals(2, speakerType)

        // Bluetooth checks
        val btSco = AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        val btA2dp = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        assertEquals(7, btSco)
        assertEquals(8, btA2dp)

        // Wired checks
        val wiredHeadphones = AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        assertEquals(4, wiredHeadphones)
    }
}
