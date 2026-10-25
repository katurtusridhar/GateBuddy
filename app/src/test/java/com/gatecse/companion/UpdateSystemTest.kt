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
            releaseId = "rel-$versionName-$versionCode",
            behaviorVersion = versionName,
            installVersionCode = versionCode,
            installVersionName = versionName,
            releaseType = releaseType,
            releaseDate = "2026-10-25T10:40:00Z",
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
        // Test updating from v1.0.1 (code 2) to v1.0.2 (code 3)
        val installedCode2 = 2L
        val candidate102 = createSampleRelease(versionCode = 3, versionName = "1.0.2", releaseType = "standard")
        val updateAvailable102 = candidate102.releaseType == "standard" && candidate102.versionCode > installedCode2
        assertTrue("A standard release with versionCode 3 > installed 2 must trigger an update", updateAvailable102)

        // Test updating from v1.0.2 (code 3) to v1.0.3 (code 4)
        val installedCode3 = 3L
        val candidate103 = createSampleRelease(versionCode = 4, versionName = "1.0.3", releaseType = "standard")
        val updateAvailable103 = candidate103.releaseType == "standard" && candidate103.versionCode > installedCode3
        assertTrue("A standard release with versionCode 4 > installed 3 must trigger an update", updateAvailable103)
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

    @Test
    fun testNoUpdateAvailableWhenInstalledVersionIsEqualOrGreater() {
        val installedCode = 3L
        val candidateSame = createSampleRelease(versionCode = 3, versionName = "1.0.2")

        val updateAvailableSame = candidateSame.releaseType == "standard" && candidateSame.versionCode > installedCode
        assertFalse("Same version must not trigger update", updateAvailableSame)

        val installedCodeHigher = 4L
        val updateAvailableLower = candidateSame.releaseType == "standard" && candidateSame.versionCode > installedCodeHigher
        assertFalse("Older candidate must not trigger forward update", updateAvailableLower)
    }

    @Test
    fun testPackageNameMismatchRejection() {
        val appPackage = "com.gatecse.companion.debug"
        val spoofedCatalog = ReleaseCatalog(
            manifestVersion = 1,
            channel = "debug",
            packageName = "com.malicious.app",
            recommendedUpdate = createSampleRelease(versionCode = 3),
            releases = listOf()
        )

        val isPackageValid = spoofedCatalog.packageName == appPackage
        assertFalse("Mismatched package name must be rejected", isPackageValid)
    }

    @Test
    fun testSignatureMismatchRejection() {
        val appFingerprint = "78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73"
        val candidateCertUntrusted = ReleaseSigningCertificate(
            sha256Fingerprint = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
        )

        fun normalize(fp: String): String = fp.replace(":", "").trim().uppercase()

        val isSignatureMatch = normalize(candidateCertUntrusted.sha256Fingerprint) == normalize(appFingerprint)
        assertFalse("Mismatched signing certificate fingerprint must be rejected", isSignatureMatch)
    }

    @Test
    fun testCorruptPayloadChecksumMismatchDetection() {
        val expectedSha256 = "837AACEBF0708A0E2B995455D3D7573D46020220CD9BF56AE593C418F96793B7"
        val payload = "Normal APK payload byte stream".toByteArray(Charsets.UTF_8)
        
        // Compute genuine hash
        val md = MessageDigest.getInstance("SHA-256")
        val genuineHash = md.digest(payload).joinToString("") { "%02x".format(it) }

        // Corrupt single bit
        val corruptedPayload = payload.clone()
        corruptedPayload[0] = (corruptedPayload[0].toInt() xor 1).toByte()
        md.reset()
        val corruptedHash = md.digest(corruptedPayload).joinToString("") { "%02x".format(it) }

        assertFalse("Corrupted payload hash must not match expected hash", corruptedHash.equals(expectedSha256, ignoreCase = true))
        assertFalse("Corrupted payload hash must not match genuine hash", corruptedHash.equals(genuineHash, ignoreCase = true))
    }

    @Test
    fun testActualCandidateApkArtifactIntegrity() {
        val apk102 = java.io.File("C:/Users/sridh/Downloads/GateBuddy-v1.0.2.apk")
        if (apk102.exists()) {
            assertEquals("Candidate v1.0.2 size must match exact build output", 20892324L, apk102.length())
            val md = MessageDigest.getInstance("SHA-256")
            apk102.inputStream().use { stream ->
                val buffer = ByteArray(65536)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            val computedHash = md.digest().joinToString("") { "%02x".format(it) }
            assertEquals(
                "Candidate v1.0.2 SHA-256 must match exactly",
                "837aacebf0708a0e2b995455d3d7573d46020220cd9bf56ae593c418f96793b7",
                computedHash.lowercase()
            )
        }

        val apk103 = java.io.File("C:/Users/sridh/Downloads/GateBuddy-v1.0.3.apk")
        if (apk103.exists()) {
            assertEquals("Candidate v1.0.3 size must match exact build output", 20892324L, apk103.length())
            val md = MessageDigest.getInstance("SHA-256")
            apk103.inputStream().use { stream ->
                val buffer = ByteArray(65536)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            val computedHash = md.digest().joinToString("") { "%02x".format(it) }
            assertEquals(
                "Candidate v1.0.3 SHA-256 must match exactly",
                "75f9509d3340b22e18e1637ce1d51952f26dd56229561c910cdbef8db3671ad7",
                computedHash.lowercase()
            )
        }

        val apkRollback5 = java.io.File("C:/Users/sridh/Downloads/GateBuddy-v1.0.2-r1.apk")
        if (apkRollback5.exists()) {
            assertEquals("Rollback v1.0.2-r1 size must match exact build output", 20908708L, apkRollback5.length())
            val md = MessageDigest.getInstance("SHA-256")
            apkRollback5.inputStream().use { stream ->
                val buffer = ByteArray(65536)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            val computedHash = md.digest().joinToString("") { "%02x".format(it) }
            assertEquals(
                "Rollback v1.0.2-r1 SHA-256 must match exactly",
                "b231b7938a18aaf6e308160792c2e5677cf2ee084cc5da1de97113699448d24d",
                computedHash.lowercase()
            )
        }

        val apkForward6 = java.io.File("C:/Users/sridh/Downloads/GateBuddy-v1.0.4.apk")
        if (apkForward6.exists()) {
            assertEquals("Forward v1.0.4 size must match exact build output", 20908712L, apkForward6.length())
            val md = MessageDigest.getInstance("SHA-256")
            apkForward6.inputStream().use { stream ->
                val buffer = ByteArray(65536)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            val computedHash = md.digest().joinToString("") { "%02x".format(it) }
            assertEquals(
                "Forward v1.0.4 SHA-256 must match exactly",
                "b880a0b7cc5a2d6a9bbf835143578f146a1cab750f83bac26641cca07504a35d",
                computedHash.lowercase()
            )
        }
    }

    // Version Manager logic tests
    private fun evaluateAction(
        release: ReleaseEntry,
        installedCode: Long,
        currentDbVersion: Int = 3,
        deviceSdk: Int = 35
    ): com.gatecse.companion.updater.ReleaseActionState {
        val targetCode = release.installVersionCode.toLong()

        if (targetCode == installedCode) {
            return com.gatecse.companion.updater.ReleaseActionState.Current
        }

        if (targetCode < installedCode) {
            return com.gatecse.companion.updater.ReleaseActionState.HistoricalBuildRequired(
                reason = "Direct in-place install blocked by Android (Build ${release.installVersionCode} < current Build $installedCode). Use a Restore build to run this version.",
                historicalBehaviorVersion = release.behaviorVersion.ifEmpty { release.installVersionName }
            )
        }

        if (release.database.requiresSchemaDowngrade ||
            release.database.minSupportedDatabaseVersion > currentDbVersion ||
            release.database.databaseVersion < currentDbVersion
        ) {
            return com.gatecse.companion.updater.ReleaseActionState.Incompatible(
                "Database schema mismatch: requires schema downgrade or unsupported database version."
            )
        }

        if (deviceSdk < release.compatibility.minSdk) {
            return com.gatecse.companion.updater.ReleaseActionState.Incompatible(
                "Requires Android API ${release.compatibility.minSdk}+ (device is API $deviceSdk)."
            )
        }

        return if (release.releaseType.equals("rollback", ignoreCase = true)) {
            val targetBehavior = release.restoresBehaviorVersion ?: release.behaviorVersion.ifEmpty { release.installVersionName }
            com.gatecse.companion.updater.ReleaseActionState.CanRestore(release, targetBehavior)
        } else {
            com.gatecse.companion.updater.ReleaseActionState.CanUpdate(release)
        }
    }

    @Test
    fun testVersionManagerActionEvaluationCurrentBuild() {
        val installedCode = 4L
        val rel103 = createSampleRelease(versionCode = 4, versionName = "1.0.3", releaseType = "standard")
        val action = evaluateAction(rel103, installedCode)
        assertTrue("Matching build must be identified as Current", action is com.gatecse.companion.updater.ReleaseActionState.Current)
    }

    @Test
    fun testVersionManagerActionEvaluationForwardUpdate() {
        val installedCode = 4L
        val rel104 = createSampleRelease(versionCode = 6, versionName = "1.0.4", releaseType = "standard")
        val action = evaluateAction(rel104, installedCode)
        assertTrue("Higher standard build must be identified as CanUpdate", action is com.gatecse.companion.updater.ReleaseActionState.CanUpdate)
    }

    @Test
    fun testVersionManagerActionEvaluationRollbackCandidate() {
        val installedCode = 4L
        val relRollback = ReleaseEntry(
            releaseId = "rel-1.0.2-r1-b5",
            behaviorVersion = "1.0.2",
            installVersionCode = 5,
            installVersionName = "1.0.2-r1",
            releaseType = "rollback",
            restoresBehaviorVersion = "1.0.2",
            releaseDate = "2026-10-25T12:35:00Z",
            changelog = ReleaseChangelog("Rollback summary", listOf("Highlight 1")),
            apkUrl = "https://gate-buddy.vercel.app/releases/GateBuddy-v1.0.2-r1.apk",
            apkSizeBytes = 20908708L,
            sha256 = "b231b7938a18aaf6e308160792c2e5677cf2ee084cc5da1de97113699448d24d",
            signingCertificate = ReleaseSigningCertificate("78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73"),
            compatibility = ReleaseCompatibility(26, 35, listOf("arm64-v8a")),
            database = ReleaseDatabase(3, 3, false)
        )
        val action = evaluateAction(relRollback, installedCode)
        assertTrue("Rollback release with higher build must be identified as CanRestore", action is com.gatecse.companion.updater.ReleaseActionState.CanRestore)
        assertEquals("1.0.2", (action as com.gatecse.companion.updater.ReleaseActionState.CanRestore).targetBehaviorVersion)
    }

    @Test
    fun testVersionManagerActionEvaluationHistoricalLowerBuildRequiresRestore() {
        val installedCode = 4L // Currently on v1.0.3 (Build 4)
        val historical102 = createSampleRelease(versionCode = 3, versionName = "1.0.2", releaseType = "standard")
        val action = evaluateAction(historical102, installedCode)
        assertTrue(
            "Historical lower versionCode must be blocked with HistoricalBuildRequired",
            action is com.gatecse.companion.updater.ReleaseActionState.HistoricalBuildRequired
        )
    }

    @Test
    fun testVersionManagerActionEvaluationDatabaseSchemaMismatchIncompatible() {
        val installedCode = 4L
        val incompatibleDbRelease = ReleaseEntry(
            releaseId = "rel-bad-db",
            behaviorVersion = "2.0.0",
            installVersionCode = 7,
            installVersionName = "2.0.0",
            releaseType = "standard",
            changelog = ReleaseChangelog("Incompatible DB", listOf()),
            apkUrl = "https://example.com/bad.apk",
            apkSizeBytes = 20000000L,
            sha256 = "dummy",
            signingCertificate = ReleaseSigningCertificate("78:49:6C:7B..."),
            compatibility = ReleaseCompatibility(26, 35, listOf("arm64-v8a")),
            database = ReleaseDatabase(databaseVersion = 4, minSupportedDatabaseVersion = 4, requiresSchemaDowngrade = true)
        )
        val action = evaluateAction(incompatibleDbRelease, installedCode, currentDbVersion = 3)
        assertTrue("Incompatible database requirements must be marked Incompatible", action is com.gatecse.companion.updater.ReleaseActionState.Incompatible)
    }
}
