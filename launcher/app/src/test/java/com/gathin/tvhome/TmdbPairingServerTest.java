package com.gathin.tvhome;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TmdbPairingServerTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test public void onlyValidSubmissionSavesAndConsumesSession() throws Exception {
        AtomicReference<String> stored = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        Inet4Address local = (Inet4Address) InetAddress.getByName("127.0.0.1");
        try (TmdbPairingServer server = new TmdbPairingServer(local, "%%TITLE%%\n%%CONTENT%%",
                new TmdbPairingServer.Callback() {
                    @Override public boolean validate(String key) { return KEY.equals(key); }
                    @Override public boolean save(String key) { stored.set(key); return true; }
                    @Override public void finished(boolean saved) { finished.countDown(); }
                })) {
            server.start();
            URL session = new URL(server.url());
            assertEquals(404, status(new URL(session, "/wrong"), null, null));
            assertEquals(200, status(session, null, null));
            assertEquals(403, status(session, KEY, "http://other.invalid"));
            assertEquals(400, status(session, "invalid", null));
            assertEquals(422, status(session, "00000000000000000000000000000000", null));
            assertNull(stored.get());
            assertEquals(200, status(session, KEY, null));
            assertTrue(finished.await(3, TimeUnit.SECONDS));
            assertEquals(KEY, stored.get());
            assertThrows(IOException.class, () -> status(session, KEY, null));
        }
    }

    private static int status(URL url, String key, String origin) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(url.getHost(), url.getPort()), 2000);
            socket.setSoTimeout(2000);
            String headers = (key == null ? "GET " : "POST ") + url.getPath() + " HTTP/1.1\r\n"
                    + "Host: " + url.getAuthority() + "\r\n";
            String body = "";
            if (key != null) {
                body = "key=" + key;
                headers += "Content-Type: application/x-www-form-urlencoded\r\n"
                        + "Content-Length: " + body.length() + "\r\n";
            }
            if (origin != null) headers += "Origin: " + origin + "\r\n";
            socket.getOutputStream().write((headers + "\r\n" + body).getBytes(StandardCharsets.US_ASCII));
            BufferedReader response = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.US_ASCII));
            return Integer.parseInt(response.readLine().split(" ")[1]);
        }
    }
}
