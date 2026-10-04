package com.zigor.discountcard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zigor.discountcard.data.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Проверка новой версии: сравнение номеров и разбор ответа GitHub. */
@RunWith(AndroidJUnit4::class)
class UpdateCheckTest {

    @Test
    fun versionsAreComparedByNumbersNotByText() {
        assertTrue(UpdateChecker.isNewer("1.0.19", "1.0.18"))
        assertTrue(UpdateChecker.isNewer("v1.0.20", "1.0.18"))
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.99"))
        // строковое сравнение решило бы, что «1.0.9» новее «1.0.18»
        assertFalse(UpdateChecker.isNewer("1.0.9", "1.0.18"))
        assertFalse(UpdateChecker.isNewer("1.0.18", "1.0.18"))
        assertFalse(UpdateChecker.isNewer("1.0.18", "1.0.19"))
    }

    @Test
    fun releaseListIsParsed() {
        val json = """
            [
              {
                "tag_name": "v1.0.20",
                "name": "Мои карты 1.0.20",
                "body": "- перенос карт\n- автообновление",
                "draft": false,
                "html_url": "https://github.com/zigorminsk-debug/discount-card/releases/tag/v1.0.20",
                "assets": [
                  {"name": "notes.txt", "browser_download_url": "https://example.test/notes.txt"},
                  {"name": "MoiKarty-v1.0.20-CIKEY.apk", "browser_download_url": "https://example.test/app.apk"}
                ]
              },
              {
                "tag_name": "v1.0.19",
                "name": "Мои карты 1.0.19",
                "body": "",
                "draft": false,
                "html_url": "https://example.test/19",
                "assets": []
              }
            ]
        """.trimIndent()

        val releases = UpdateChecker.parse(json)
        assertEquals(2, releases.size)
        val newest = releases.first()
        assertEquals("1.0.20", newest.version)
        assertEquals("https://example.test/app.apk", newest.apkUrl)
        assertEquals("https://example.test/app.apk", newest.downloadUrl)
        assertTrue(newest.notes.contains("автообновление"))
        // у релиза без APK ссылка ведёт на страницу релиза
        assertNull(releases[1].apkUrl)
        assertEquals("https://example.test/19", releases[1].downloadUrl)
    }

    @Test
    fun brokenAnswerDoesNotCrash() {
        assertTrue(UpdateChecker.parse("это не json").isEmpty())
        assertTrue(UpdateChecker.parse("").isEmpty())
    }
}
