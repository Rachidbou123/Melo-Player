package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Melo", appName)
  }

  @Test
  fun `test song duration formatted`() {
    val song = com.example.data.model.Song(
        title = "Test Track",
        durationMs = 215000L // 3 min 35 sec
    )
    assertEquals("3:35", song.durationFormatted)
  }

}
