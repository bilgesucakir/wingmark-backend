package com.wingmark.backend.security.ratelimit;

import com.wingmark.backend.config.ClientIpProperties;
import com.wingmark.backend.config.ClientIpProperties.Source;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    private static ClientIp resolver(Source source, int hops) {
        return new ClientIp(new ClientIpProperties(source, hops));
    }

    private static MockHttpServletRequest request(String cf, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        if (cf != null) {
            request.addHeader("CF-Connecting-IP", cf);
        }
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void cloudflareModeUsesOnlyCfConnectingIpAndIgnoresAForgedForwardedFor() {
        ClientIp ip = resolver(Source.CLOUDFLARE, 1);

        assertThat(ip.of(request("198.51.100.9", "6.6.6.6, 7.7.7.7"))).isEqualTo("198.51.100.9");
        // No CF header: the spoofable X-Forwarded-For is not a fallback, the socket address is.
        assertThat(ip.of(request(null, "6.6.6.6"))).isEqualTo("10.0.0.1");
        assertThat(ip.of(request(null, null))).isEqualTo("10.0.0.1");
    }

    @Test
    void forwardedForModeTakesTheEntryAddedByTheTrustedProxyNotTheLeftmost() {
        // The caller wrote "6.6.6.6"; the one trusted proxy appended the address it actually saw.
        assertThat(resolver(Source.FORWARDED_FOR, 1).of(request("1.1.1.1", "6.6.6.6, 203.0.113.7"))).isEqualTo("203.0.113.7");
        // Two trusted proxies: the second from the right.
        assertThat(resolver(Source.FORWARDED_FOR, 2).of(request(null, "6.6.6.6, 203.0.113.7, 10.9.9.9"))).isEqualTo("203.0.113.7");
        // Changing the forged part does not change the key.
        assertThat(resolver(Source.FORWARDED_FOR, 1).of(request(null, "8.8.8.8, 203.0.113.7")))
                .isEqualTo(resolver(Source.FORWARDED_FOR, 1).of(request(null, "9.9.9.9, 1.2.3.4, 203.0.113.7")));
        // A shorter chain than expected uses what is there; none uses the socket.
        assertThat(resolver(Source.FORWARDED_FOR, 3).of(request(null, "203.0.113.7"))).isEqualTo("203.0.113.7");
        assertThat(resolver(Source.FORWARDED_FOR, 1).of(request(null, null))).isEqualTo("10.0.0.1");
        assertThat(resolver(Source.FORWARDED_FOR, 0).of(request(null, "6.6.6.6"))).isEqualTo("10.0.0.1");
    }

    @Test
    void remoteAddrModeIgnoresEveryHeader() {
        assertThat(resolver(Source.REMOTE_ADDR, 1).of(request("198.51.100.9", "6.6.6.6"))).isEqualTo("10.0.0.1");
    }

    @Test
    void ipv6AddressesAreAccepted() {
        assertThat(resolver(Source.CLOUDFLARE, 1).of(request("2001:db8::7", null))).isEqualTo("2001:db8::7");
    }

    @Test
    void aValueThatIsNotAnAddressNeverBecomesAKey() {
        ClientIp ip = resolver(Source.CLOUDFLARE, 1);

        assertThat(ip.of(request("not an ip", null))).isEqualTo("10.0.0.1");
        assertThat(ip.of(request("evil.example.com", null))).isEqualTo("10.0.0.1");
        assertThat(ip.of(request("a".repeat(5000), null))).isEqualTo("10.0.0.1");
        assertThat(ip.of(request("1.2.3.4\u0000x", null))).isEqualTo("10.0.0.1");
    }
}
