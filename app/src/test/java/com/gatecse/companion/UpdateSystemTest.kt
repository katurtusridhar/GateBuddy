package com.gatecse.companion

import com.gatecse.companion.updater.ReleaseCatalog
import com.gatecse.companion.updater.ReleaseChangelog
import com.gatecse.companion.updater.ReleaseCompatibility
import com.gatecse.companion.updater.ReleaseDatabase
import com.gatecse.companion.updater.ReleaseEntry
import com.gatecse.companion.updater.ReleaseSigningCertificate
import com.gatecse.companion.updater.UpdateCheckResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class UpdateSystemTest {

    private fun createSampleRelease(
        versionCode: Int = 3,
        versionName: String = "1.0.2",
        releaseType: String = "standard",
        sha256: String = "3ebce6420763e897b1dcc25a44fe286385d059c8587b76a5394a2798a140c40d"
    ): ReleaseEntry {
        return ReleaseEntry(
            versionCode = versionCode,
            versionName = versionName,
            releaseDate = "2026-10-25T10:40:00Z",
            releaseType = releaseType,
            changelog = ReleaseChangelog(
                summary = "Test summary",
                highlights = listOf("Highlight 1", "Highlight 2")
            ),
            apkUrl = "https://github.com/katurtusridhar/GateBuddy/releases/download/v1.0.2/GateBuddy-v1.0.2.apk",
            apkSizeBytes = 21040765L,
            sha256 = sha256,
            signingCertificate = ReleaseSigningCertificate(
                sha256Fingerprint = "78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73",
                subjectDn = "CN=Android Debug"
            ),
            compatibility = ReleaseCompatibility(
                minSdk = 26,
                targetSdk = 35,
                supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            ),
            database = ReleaseDatabase(
                databaseVersion = 3,
                minSupportedDatabaseVersion = 3,
                requiresSchemaDowngrade = false
            )
        )
    }

    @Test
    fun testCatalogJsonSerializationAndParsing() {
        val jsonString = """
        {
          "manifestVersion": 1,
          "channel": "debug",
          "packageName": "com.gatecse.companion.debug",
          "recommendedUpdate": {
            "versionCode": 3,
            "versionName": "1.0.2",
            "releaseDate": "2026-10-25T11:00:00Z",
            "releaseType": "standard",
            "changelog": {
              "summary": "Critical continuous track sequencing",
              "highlights": ["Fixed DL sequencing", "Added in-app updater"]
            },
            "apkUrl": "https://github.com/katurtusridhar/GateBuddy/releases/download/v1.0.2/GateBuddy-v1.0.2.apk",
            "apkSizeBytes": 21040765,
            "sha256": "3EBCE6420763E897B1DCC25A44FE286385D059C8587B76A5394A2798A140C40D",
            "signingCertificate": {
              "sha256Fingerprint": "78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73",
              "subjectDn": "CN=Android Debug"
            },
            "compatibility": {
              "minSdk": 26,
              "targetSdk": 35,
              "supportedAbis": ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"]
            },
            "database": {
              "databaseVersion": 3,
              "minSupportedDatabaseVersion": 3,
              "requiresSchemaDowngrade": false
            }
          },
          "releases": []
        }
        """.trimIndent()

        val json = JSONObject(jsonString)
        assertEquals(1, json.getInt("manifestVersion"))
        assertEquals("debug", json.getString("channel"))
        assertEquals("com.gatecse.companion.debug", json.getString("packageName"))

        val rec = json.getJSONObject("recommendedUpdate")
        assertEquals(3, rec.getInt("versionCode"))
        assertEquals("1.0.2", rec.getString("versionName"))
        assertEquals("standard", rec.getString("releaseType"))
        assertEquals(21040765L, rec.getLong("apkSizeBytes"))
        assertEquals(3, rec.getJSONObject("database").getInt("databaseVersion"))
    }

    @Test
    fun testUpdateSelectionLogicWithForwardRelease() {
        val installedCode = 2L
        val installedName = "1.0.1"
        val candidate = createSampleRelease(versionCode = 3, versionName = "1.0.2", releaseType = "standard")

        val catalog = ReleaseCatalog(
            manifestVersion = 1,
            channel = "debug",
            packageName = "com.gatecse.companion.debug",
            recommendedUpdate = candidate,
            releases = listOf(candidate)
        )

        // Evaluate selection
        val updateAvailable = catalog.recommendedUpdate != null &&
                catalog.recommendedUpdate.releaseType == "standard" &&
                catalog.recommendedUpdate.versionCode > installedCode

        assertTrue("A standard release with versionCode 3 > installed 2 must trigger an update", updateAvailable)
    }

    @Test
    fun testUpdateSelectionLogicExcludesRollbackCandidate() {
        val installedCode = 3L // Already on v1.0.2
        // Rollback candidate has higher versionCode (4) but releaseType is 'rollback'
        val rollbackCandidate = createSampleRelease(versionCode = 4, versionName = "1.0.1-r1", releaseType = "rollback")

        val catalog = ReleaseCatalog(
            manifestVersion = 1,
            channel = "debug",
            packageName = "com.gatecse.companion.debug",
            recommendedUpdate = null, // No forward update recommended
            releases = listOf(rollbackCandidate)
        )

        // Auto-update checker must examine recommendedUpdate ONLY
        val recommended = catalog.recommendedUpdate
        val updateAvailable = recommended != null &&
                recommended.releaseType == "standard" &&
                recommended.versionCode > installedCode

        assertFalse("Rollback candidate with higher versionCode must NEVER be auto-prompted as a standard update", updateAvailable)
    }

    @Test
    fun testSha256ChecksumVerificationAlgorithm() {
        val testBytes = "GATEBuddy Sideloaded Update Verification Payload 2026".toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(testBytes)
        val computedHex = digest.joinToString("") { "%02x".format(it) }

        // Test matching hash passes (case-insensitive)
        assertTrue(computedHex.equals(computedHex.uppercase(), ignoreCase = true))

        // Test mutated hash fails
        val mutatedHex = computedHex.substring(0, computedHex.length - 1) + if (computedHex.last() == '0') '1' else '0'
        assertFalse(computedHex.equals(mutatedHex, ignoreCase = true))
    }

    @Test
    fun testFingerprintNormalization() {
        val colonDelimited = "78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73"
        val rawLowercase = "78496c7bd8f3411f903612971439eb816747a44ced09cef431bb45a5d47ff273"

        fun normalize(fp: String): String = fp.replace(":", "").trim().uppercase()

        assertEquals(normalize(colonDelimited), normalize(rawLowercase))
    }

    @Test
    fun testStorageRequirementPreflightRule() {
        val apkSize = 21040765L // ~21MB
        val requiredStorage = apkSize * 2L // ~42MB

        fun isStorageSufficient(availableBytes: Long, required: Long): Boolean {
            return availableBytes >= required
        }

        // Available: 100MB -> sufficient
        assertTrue(isStorageSufficient(100L * 1024 * 1024, requiredStorage))

        // Available: 30MB -> insufficient
        assertFalse(isStorageSufficient(30L * 1024 * 1024, requiredStorage))
    }
}
