package de.eposthelper.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureStore {
    private static final String PREF = "secure_profiles";
    private static final String KEY_ALIAS = "epost_helper_profiles_v1";
    private static final String VALUE = "profiles";
    private SecureStore() {}

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return kg.generateKey();
    }

    public static synchronized List<Profile> load(Context context) {
        List<Profile> result = new ArrayList<>();
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String packed = sp.getString(VALUE, null); if (packed == null) return result;
            String[] parts = packed.split("\\.", 2);
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] ct = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            JSONArray a = new JSONArray(new String(cipher.doFinal(ct), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) result.add(Profile.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return result;
    }

    public static synchronized void save(Context context, List<Profile> profiles) throws Exception {
        JSONArray a = new JSONArray(); for (Profile p : profiles) a.put(p.toJson());
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] ct = cipher.doFinal(a.toString().getBytes(StandardCharsets.UTF_8));
        String packed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." + Base64.encodeToString(ct, Base64.NO_WRAP);
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(VALUE, packed).apply();
    }

    public static Profile find(Context context, String id) {
        for (Profile p : load(context)) if (p.id.equals(id)) return p;
        return null;
    }
}
