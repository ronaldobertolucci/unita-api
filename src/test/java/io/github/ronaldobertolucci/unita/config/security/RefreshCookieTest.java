package io.github.ronaldobertolucci.unita.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class RefreshCookieTest {
    @Test void httpsCookieUsesConfiguredScopeOnIssueAndDeletion() {
        var cookies = new RefreshCookie();
        ReflectionTestUtils.setField(cookies, "secure", true);
        ReflectionTestUtils.setField(cookies, "sameSite", "None");
        cookies.validate();
        var request = new MockHttpServletRequest(); request.setContextPath("/custom");
        String issued = cookies.issue(request, "token", Instant.now().plusSeconds(60));
        String cleared = cookies.clear(request);
        for (String attribute : new String[]{"Path=/custom/auth", "Secure", "HttpOnly", "SameSite=None"}) {
            assertTrue(issued.contains(attribute)); assertTrue(cleared.contains(attribute));
        }
        assertTrue(cleared.contains("Max-Age=0")); assertFalse(issued.contains("Domain="));
    }
    @Test void noneWithoutHttpsAndUnknownSameSiteFailConfiguration() {
        var cookies = new RefreshCookie();
        ReflectionTestUtils.setField(cookies, "secure", false);
        ReflectionTestUtils.setField(cookies, "sameSite", "None");
        assertThrows(IllegalStateException.class, cookies::validate);
        ReflectionTestUtils.setField(cookies, "sameSite", "unknown");
        assertThrows(IllegalStateException.class, cookies::validate);
    }
}
