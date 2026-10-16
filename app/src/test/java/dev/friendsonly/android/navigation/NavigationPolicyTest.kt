package dev.friendsonly.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
    @Test fun instagramPagesStayInside() {
        listOf(NavigationPolicy.HOME, NavigationPolicy.MESSAGES,
            "https://instagram.com/p/abc/", "https://www.instagram.com:443/stories/friend/123/",
            "https://accountscenter.instagram.com/", "https://WWW.INSTAGRAM.COM/")
            .forEach { assertEquals(it, NavigationPolicy.Destination.INSTAGRAM, NavigationPolicy.classify(it)) }
    }

    @Test fun externalWebLinksUseBrowser() {
        listOf("https://example.org/", "http://example.org/", "https://instagram.com.evil.example/",
            "https://evilinstagram.com/", "https://www.facebook.com/login/")
            .forEach { assertEquals(it, NavigationPolicy.Destination.BROWSER, NavigationPolicy.classify(it)) }
    }

    @Test fun unsafeUrlsAreBlocked() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "content://test", "intent://test", "data:text/html,test",
            "http://www.instagram.com/", "https://www.instagram.com:444/", "https://user@www.instagram.com/",
            "https://www.instagram.com@evil.example/", "//www.instagram.com/", "", "https://a b/", "about:blank")
            .forEach { assertEquals(it, NavigationPolicy.Destination.BLOCKED, NavigationPolicy.classify(it)) }
    }

    @Test fun injectionIsRestrictedToMainWebsiteHosts() {
        assertTrue(NavigationPolicy.canFilter(NavigationPolicy.HOME))
        assertTrue(NavigationPolicy.canFilter("https://m.instagram.com/"))
        assertFalse(NavigationPolicy.canFilter("https://accountscenter.instagram.com/"))
        assertFalse(NavigationPolicy.canFilter("https://example.org/"))
        assertFalse(NavigationPolicy.canFilter("http://www.instagram.com/"))
    }

    @Test fun directSelectionRequiresTrustedDirectPath() {
        assertTrue(NavigationPolicy.isMessages("https://www.instagram.com/direct/t/123/"))
        assertFalse(NavigationPolicy.isMessages(NavigationPolicy.HOME))
        assertFalse(NavigationPolicy.isMessages("https://example.org/direct/inbox/"))
    }
}
