package it.armando.appgroups

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private fun release(
        tag: String = "v1.2.0",
        assets: String = """
            {"name":"AppGroups-v1.2.0.apk","size":1234,"browser_download_url":"https://github.com/armando-basile/AppGroups/releases/download/v1.2.0/AppGroups-v1.2.0.apk"},
            {"name":"AppGroups-v1.2.0.apk.sha256","size":90,"browser_download_url":"https://github.com/armando-basile/AppGroups/releases/download/v1.2.0/AppGroups-v1.2.0.apk.sha256"}
        """,
    ) = """{"tag_name":"$tag","body":"Notes","assets":[$assets]}"""

    @Test
    fun newerVersionsAreDetectedAcrossAllParts() {
        assertTrue(UpdateChecker.isNewer("v1.0.1", "1.0.0"))
        assertTrue(UpdateChecker.isNewer("v1.1.0", "1.0.9"))
        assertTrue(UpdateChecker.isNewer("v2.0.0", "1.99.99"))
        assertTrue(UpdateChecker.isNewer("v1.10.0", "1.9.0"))
    }

    @Test
    fun sameOrOlderVersionsAreNotUpdates() {
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0"))
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.1"))
        assertFalse(UpdateChecker.isNewer("v0.9.9", "1.0"))
    }

    @Test
    fun unparsableVersionsNeverTriggerAnUpdate() {
        assertFalse(UpdateChecker.isNewer("latest", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("v1.0.1", "unknown"))
        assertNull(UpdateChecker.parseVersion("nightly"))
    }

    @Test
    fun parsesAReleaseWithApkAndChecksum() {
        val info = UpdateChecker.parseRelease(release())
        assertNotNull(info)
        assertEquals("v1.2.0", info!!.tag)
        assertEquals("Notes", info.notes)
        assertEquals(1234L, info.apkSize)
        assertTrue(info.apkUrl.endsWith("AppGroups-v1.2.0.apk"))
        assertTrue(info.checksumUrl.endsWith("AppGroups-v1.2.0.apk.sha256"))
    }

    @Test
    fun releaseWithoutChecksumIsRejected() {
        val onlyApk = """{"name":"AppGroups-v1.2.0.apk","size":1,"browser_download_url":"https://github.com/a/b/releases/download/v1.2.0/AppGroups-v1.2.0.apk"}"""
        assertNull(UpdateChecker.parseRelease(release(assets = onlyApk)))
    }

    @Test
    fun releaseWithoutAssetsOrWithBadTagIsRejected() {
        assertNull(UpdateChecker.parseRelease(release(assets = "")))
        assertNull(UpdateChecker.parseRelease(release(tag = "nightly")))
        assertNull(UpdateChecker.parseRelease("not json"))
    }

    @Test
    fun downloadUrlsOutsideGitHubAreRejected() {
        val evil = """
            {"name":"a.apk","size":1,"browser_download_url":"https://evil.example.com/a.apk"},
            {"name":"a.apk.sha256","size":1,"browser_download_url":"https://github.com/x/y/releases/download/v1.2.0/a.apk.sha256"}
        """
        assertNull(UpdateChecker.parseRelease(release(assets = evil)))
    }

    @Test
    fun onlyHttpsGitHubHostsAreAllowed() {
        assertTrue(UpdateChecker.isAllowedUrl("https://github.com/a/b"))
        assertTrue(UpdateChecker.isAllowedUrl("https://objects.githubusercontent.com/x"))
        assertTrue(UpdateChecker.isAllowedUrl("https://release-assets.githubusercontent.com/x"))
        assertFalse(UpdateChecker.isAllowedUrl("http://github.com/a/b"))
        assertFalse(UpdateChecker.isAllowedUrl("https://evilgithub.com/a"))
        assertFalse(UpdateChecker.isAllowedUrl("https://github.com.evil.com/a"))
        assertFalse(UpdateChecker.isAllowedUrl("not a url"))
    }
}
