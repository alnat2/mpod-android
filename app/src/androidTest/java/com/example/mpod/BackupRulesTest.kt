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
        val cookieExclusions = readExclusionParents(R.xml.backup_rules, "CookiePrefs.xml")
        val pendingSyncExclusions = readExclusionParents(
            R.xml.backup_rules,
            "PendingPlaybackSync.xml"
        )

        assertEquals(listOf("full-backup-content"), cookieExclusions)
        assertEquals(listOf("full-backup-content"), pendingSyncExclusions)
    }

    @Test
    fun cloudBackupAndDeviceTransferExcludeSessionCookies() {
        val cookieExclusions = readExclusionParents(
            R.xml.data_extraction_rules,
            "CookiePrefs.xml"
        )
        val pendingSyncExclusions = readExclusionParents(
            R.xml.data_extraction_rules,
            "PendingPlaybackSync.xml"
        )

        assertEquals(listOf("cloud-backup", "device-transfer"), cookieExclusions)
        assertEquals(listOf("cloud-backup", "device-transfer"), pendingSyncExclusions)
    }

    private fun readExclusionParents(resourceId: Int, excludedPath: String): List<String> {
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
                            if (domain == "sharedpref" && path == excludedPath) {
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
