package com.example.mpod

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class BackupRulesTest {
    @Test
    fun legacyBackupExcludesSessionCookies() {
        val exclusions = readCookieExclusionParents(R.xml.backup_rules)

        assertEquals(listOf("full-backup-content"), exclusions)
    }

    @Test
    fun cloudBackupAndDeviceTransferExcludeSessionCookies() {
        val exclusions = readCookieExclusionParents(R.xml.data_extraction_rules)

        assertEquals(listOf("cloud-backup", "device-transfer"), exclusions)
    }

    private fun readCookieExclusionParents(resourceId: Int): List<String> {
        val resources = ApplicationProvider.getApplicationContext<MpodApplication>().resources
        val parser = resources.getXml(resourceId)
        val parents = mutableListOf<String>()
        var parent = ""

        parser.use {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "full-backup-content", "cloud-backup", "device-transfer" -> {
                            parent = parser.name
                        }
                        "exclude" -> {
                            val domain = parser.getAttributeValue(null, "domain")
                            val path = parser.getAttributeValue(null, "path")
                            if (domain == "sharedpref" && path == "CookiePrefs.xml") {
                                parents += parent
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }

        return parents
    }
}
