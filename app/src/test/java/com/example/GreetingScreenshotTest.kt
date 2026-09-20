package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.audio.model.AudioDeviceModel
import com.example.audio.model.DeviceCategory
import com.example.ui.components.DeviceSelectorCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleDevice = AudioDeviceModel(
        id = 1,
        name = "Phone Speaker",
        type = 2,
        typeLabel = "Built-in Speaker",
        category = DeviceCategory.BUILT_IN,
        isInput = false,
        isOutput = true,
        isSelected = true,
        isBuiltIn = true
    )
    composeTestRule.setContent {
        MyApplicationTheme {
            DeviceSelectorCard(
                device = sampleDevice,
                isInput = false,
                onClick = {}
            )
        }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
