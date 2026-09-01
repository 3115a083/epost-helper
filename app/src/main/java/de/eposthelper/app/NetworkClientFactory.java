package de.eposthelper.app;

import java.net.URI;
import java.util.Arrays;
import okhttp3.CertificatePinner;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

public final class NetworkClientFactory {
    private NetworkClientFactory() {}
    public static OkHttpClient create(Profile profile) throws Exception {
        ConnectionSpec secureTls = new ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
                .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2).build();
        OkHttpClient.Builder b = new OkHttpClient.Builder().connectionSpecs(Arrays.asList(secureTls));
        String pin = profile.certificatePin == null ? "" : profile.certificatePin.trim();
        if (!pin.isEmpty()) {
            String normalized = pin.startsWith("sha256/") ? pin : "sha256/" + pin;
            String host = new URI(Sender.normalizeSecureUrl(profile.url)).getHost();
            b.certificatePinner(new CertificatePinner.Builder().add(host, normalized).build());
        }
        return b.build();
    }
}
