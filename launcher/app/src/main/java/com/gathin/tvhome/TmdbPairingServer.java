package com.gathin.tvhome;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Single-use, foreground-only HTTP form. The credential is sent in the form body. */
final class TmdbPairingServer implements AutoCloseable {
    static final long LIFETIME_MS = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_HEADERS = 8192;
    private static final int MAX_FORM = 512;
    private final long expires = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LIFETIME_MS);
    private final ServerSocket server = new ServerSocket();
    private final String authority;
    private final String path;
    private final String template;
    private final Callback callback;
    private Socket active;
    private boolean closed;
    private boolean saved;

    interface Callback {
        boolean validate(String key) throws IOException;
        boolean save(String key);
        void finished(boolean saved);
    }

    TmdbPairingServer(Inet4Address address, String template, Callback callback) throws IOException {
        this.template = template;
        this.callback = callback;
        byte[] token = new byte[16];
        new SecureRandom().nextBytes(token);
        path = "/" + Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        try {
            server.bind(new InetSocketAddress(address, 0), 4);
            server.setSoTimeout(1000);
        } catch (IOException e) {
            close();
            throw e;
        }
        authority = address.getHostAddress() + ":" + server.getLocalPort();
    }

    String url() { return "http://" + authority + path; }

    void start() { new Thread(this::serve, "UnifiedTV-TmdbPairing").start(); }

    private void serve() {
        try {
            while (!saved && System.nanoTime() < expires && !server.isClosed()) {
                Socket connection;
                try {
                    connection = server.accept();
                } catch (SocketTimeoutException e) {
                    continue;
                }
                try (Socket client = connection) {
                    synchronized (this) {
                        if (closed) break;
                        active = client;
                    }
                    client.setSoTimeout(2500);
                    handle(client);
                } catch (IOException ignored) {
                    // A speculative connection or interrupted request must not end the session.
                } finally {
                    synchronized (this) { active = null; }
                }
            }
        } catch (IOException ignored) {
            // Closing the foreground session interrupts accept().
        } finally {
            close();
            callback.finished(saved);
        }
    }

    private void handle(Socket client) throws IOException {
        InputStream in = new BufferedInputStream(client.getInputStream());
        OutputStream out = client.getOutputStream();
        long deadline = Math.min(expires, System.nanoTime() + TimeUnit.SECONDS.toNanos(8));
        int[] remaining = {MAX_HEADERS};
        String request = line(in, remaining, deadline);
        Map<String, String> headers = new HashMap<>();
        for (String header; !(header = line(in, remaining, deadline)).isEmpty();) {
            int colon = header.indexOf(':');
            if (colon <= 0) throw new IOException("Malformed HTTP header");
            String name = header.substring(0, colon).toLowerCase(Locale.ROOT);
            if (headers.putIfAbsent(name, header.substring(colon + 1).trim()) != null)
                throw new IOException("Duplicate HTTP header");
        }
        String[] requestParts = request.split(" ", -1);
        if (requestParts.length != 3 || !requestParts[2].equals("HTTP/1.1")
                || !authority.equals(headers.get("host")) || !path.equals(requestParts[1])) {
            reply(out, "404 Not Found", "Enlace no disponible",
                    "<p>Abre el QR que aparece en los ajustes de UnifiedTV.</p>");
            return;
        }
        if ("GET".equals(requestParts[0])) {
            form(out, "200 OK", "");
            return;
        }
        if (!"POST".equals(requestParts[0]) || headers.containsKey("transfer-encoding")
                || !"application/x-www-form-urlencoded".equals(headers.get("content-type"))
                || !("http://" + authority).equals(headers.getOrDefault("origin", "http://" + authority))
                || "cross-site".equals(headers.get("sec-fetch-site"))) {
            reply(out, "403 Forbidden", "Envío no disponible",
                    "<p>Abre el formulario escaneando el QR de UnifiedTV.</p>");
            return;
        }
        String key = readKey(in, headers.get("content-length"), deadline);
        if (key == null || !key.matches("[0-9a-fA-F]{32}")) {
            form(out, "400 Bad Request", "Pega la clave API v3 de TMDB de 32 caracteres.");
            return;
        }
        try {
            if (!callback.validate(key)) {
                form(out, "422 Unprocessable Content", "TMDB ha rechazado la clave. Compruébala y vuelve a enviarla.");
                return;
            }
        } catch (IOException e) {
            form(out, "502 Bad Gateway", "La TV no ha podido comprobar la clave con TMDB. Inténtalo de nuevo.");
            return;
        }
        synchronized (this) {
            if (closed || System.nanoTime() >= expires) return;
            saved = callback.save(key);
        }
        if (saved) {
            reply(out, "200 OK", "TMDB conectado",
                    "<p class=\"success\" role=\"status\">La clave se ha guardado en tu TV. Ya puedes cerrar esta página.</p>");
        } else {
            form(out, "500 Internal Server Error", "La TV no ha podido guardar la clave. Vuelve a intentarlo.");
        }
    }

    private static String line(InputStream in, int[] remaining, long deadline) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        while (remaining[0]-- > 0 && System.nanoTime() < deadline) {
            int value = in.read();
            if (value < 0) break;
            if (value == '\r') {
                if (remaining[0]-- > 0 && in.read() == '\n')
                    return new String(data.toByteArray(), StandardCharsets.US_ASCII);
                break;
            }
            if (value == '\n') break;
            data.write(value);
        }
        throw new IOException("Incomplete or oversized HTTP headers");
    }

    private static String readKey(InputStream in, String length, long deadline) throws IOException {
        if (length == null || !length.matches("[0-9]{1,3}")) return null;
        int size = Integer.parseInt(length);
        if (size == 0 || size > MAX_FORM) return null;
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            if (System.nanoTime() >= deadline) throw new SocketTimeoutException();
            int value = in.read();
            if (value < 0) throw new IOException("Incomplete form");
            bytes[i] = (byte) value;
        }
        String body = new String(bytes, StandardCharsets.US_ASCII);
        if (!body.startsWith("key=") || body.indexOf('&') >= 0) return null;
        try {
            return URLDecoder.decode(body.substring(4), "UTF-8").trim();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void form(OutputStream out, String status, String error) throws IOException {
        String message = error.isEmpty() ? "" : "<p class=\"error\" role=\"alert\">" + error + "</p>";
        reply(out, status, "Conecta TMDB con tu TV",
                "<p>Pega la clave API v3 de tu cuenta de The Movie Database.</p>" + message
                + "<form method=\"post\" autocomplete=\"off\">"
                + "<label for=\"key\">Clave API v3</label>"
                + "<input id=\"key\" name=\"key\" type=\"password\" required maxlength=\"128\""
                + " autocapitalize=\"none\" spellcheck=\"false\" autocomplete=\"off\">"
                + "<button type=\"submit\">Guardar en la TV</button></form>"
                + "<p class=\"note\">La clave viaja por HTTP en tu red local. Usa tu red de confianza.</p>");
    }

    private void reply(OutputStream out, String status, String title, String content) throws IOException {
        byte[] page = template.replace("%%TITLE%%", title).replace("%%CONTENT%%", content)
                .getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\nContent-Length: " + page.length
                + "\r\nConnection: close\r\nCache-Control: no-store\r\nReferrer-Policy: same-origin"
                + "\r\nX-Content-Type-Options: nosniff"
                + "\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline';"
                + " form-action 'self'; frame-ancestors 'none'; base-uri 'none'\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(page);
        out.flush();
    }

    @Override public synchronized void close() {
        closed = true;
        try { server.close(); } catch (IOException ignored) {}
        if (active != null) {
            try { active.close(); } catch (IOException ignored) {}
        }
    }
}
