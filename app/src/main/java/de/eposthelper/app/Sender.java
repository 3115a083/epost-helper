package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class Sender {
    private static final MediaType PDF = MediaType.get("application/pdf");
    private static final MediaType IPP = MediaType.get("application/ipp");
    private Sender() {}

    public static String normalizeSecureUrl(String raw) {
        if (raw == null) throw new IllegalArgumentException("URL fehlt");
        String u = raw.trim();
        if (u.startsWith("ipps://")) return "https://" + u.substring("ipps://".length());
        if (u.startsWith("https://")) return u;
        throw new IllegalArgumentException("Nur HTTPS/IPPS ist erlaubt. Unverschlüsselte Ziele werden blockiert.");
    }

    private static byte[] readAll(Context context, Uri uri) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("PDF kann nicht geöffnet werden");
            byte[] buf = new byte[64 * 1024]; int n; while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    public static void send(Context context, Uri pdf, Profile profile) throws Exception {
        if (profile == null) throw new IllegalArgumentException("Versandprofil fehlt");
        byte[] bytes = readAll(context, pdf);
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F')
            throw new IllegalArgumentException("Die Datei ist kein gültiges PDF");
        String secure = normalizeSecureUrl(profile.url);
        OkHttpClient client = NetworkClientFactory.create(profile);
        if (Profile.TYPE_IPP.equals(profile.type)) sendIpp(client, secure, bytes, profile);
        else sendWebDav(client, secure, bytes, profile);
    }

    private static Request.Builder auth(Request.Builder rb, Profile p) {
        if (p.username != null && !p.username.isBlank())
            rb.header("Authorization", Credentials.basic(p.username, p.password == null ? "" : p.password, StandardCharsets.UTF_8));
        return rb;
    }

    private static void sendWebDav(OkHttpClient client, String url, byte[] bytes, Profile p) throws Exception {
        String base = url.endsWith("/") ? url : url + "/";
        String name = "epost-helper-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0,8) + ".pdf";
        Request req = auth(new Request.Builder().url(base + name), p).put(RequestBody.create(bytes, PDF))
                .header("Content-Type","application/pdf").build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IllegalStateException("Sammelkorb meldet HTTP " + r.code());
        }
    }

    private static void sendIpp(OkHttpClient client, String httpsUrl, byte[] pdf, Profile p) throws Exception {
        String printerUri = p.url.trim();
        if (printerUri.startsWith("https://")) printerUri = "ipps://" + printerUri.substring("https://".length());
        byte[] head = IppEncoder.printJobHeader(printerUri, p.username, "E-POST Helper");
        byte[] body = new byte[head.length + pdf.length];
        System.arraycopy(head,0,body,0,head.length); System.arraycopy(pdf,0,body,head.length,pdf.length);
        Request req = auth(new Request.Builder().url(httpsUrl), p).post(RequestBody.create(body, IPP))
                .header("Content-Type","application/ipp").build();
        try (Response r = client.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new IllegalStateException("IPP-Ziel meldet HTTP " + r.code());
            byte[] response = r.body() == null ? new byte[0] : r.body().bytes();
            if (response.length >= 4) {
                int status = ((response[2]&0xff)<<8) | (response[3]&0xff);
                if (status >= 0x0400) throw new IllegalStateException("IPP-Fehler 0x" + Integer.toHexString(status));
            }
        }
    }
}
