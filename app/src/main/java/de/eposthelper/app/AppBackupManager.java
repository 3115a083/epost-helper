package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class AppBackupManager {
    private static final byte[] MAGIC="EPHB1".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS=220000;
    private AppBackupManager(){}

    public static void exportTo(Context c,Uri uri,char[] password) throws Exception{
        if(password==null||password.length<6)throw new IllegalArgumentException("Das Backup-Passwort muss mindestens 6 Zeichen lang sein.");
        JSONObject root=new JSONObject();
        root.put("format",1);
        root.put("exportedAt",System.currentTimeMillis());
        root.put("settings",SettingsStore.exportJson(c));

        JSONArray profiles=new JSONArray();
        for(Profile p:SecureStore.load(c))if(!DebugProfileManager.isDebug(p))profiles.put(p.toJson());
        root.put("profiles",profiles);

        JSONArray stats=new JSONArray();
        for(SendStat s:SendStatsStore.load(c))stats.put(s.toJson());
        root.put("stats",stats);

        byte[] salt=new byte[16],iv=new byte[12];
        SecureRandom rnd=new SecureRandom();rnd.nextBytes(salt);rnd.nextBytes(iv);
        SecretKeySpec key=derive(password,salt);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
        byte[] ct=cipher.doFinal(root.toString().getBytes(StandardCharsets.UTF_8));

        try(OutputStream out=c.getContentResolver().openOutputStream(uri,"w")){
            if(out==null)throw new IllegalStateException("Backup-Datei konnte nicht geöffnet werden.");
            out.write(MAGIC);out.write(salt);out.write(iv);out.write(ct);
        }
    }

    public static void importFrom(Context c,Uri uri,char[] password) throws Exception{
        if(password==null||password.length<6)throw new IllegalArgumentException("Backup-Passwort fehlt.");
        byte[] data=readAll(c,uri);
        if(data.length<MAGIC.length+16+12+16)throw new IllegalArgumentException("Ungültige Backup-Datei.");
        for(int i=0;i<MAGIC.length;i++)if(data[i]!=MAGIC[i])throw new IllegalArgumentException("Unbekanntes Backup-Format.");

        int pos=MAGIC.length;
        byte[] salt=java.util.Arrays.copyOfRange(data,pos,pos+16);pos+=16;
        byte[] iv=java.util.Arrays.copyOfRange(data,pos,pos+12);pos+=12;
        byte[] ct=java.util.Arrays.copyOfRange(data,pos,data.length);

        SecretKeySpec key=derive(password,salt);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));
        byte[] plain;
        try{plain=cipher.doFinal(ct);}
        catch(Exception e){throw new IllegalArgumentException("Backup-Passwort falsch oder Datei beschädigt.");}

        JSONObject root=new JSONObject(new String(plain,StandardCharsets.UTF_8));
        SettingsStore.importJson(c,root.optJSONObject("settings"));

        ArrayList<Profile> profiles=new ArrayList<>();
        JSONArray pa=root.optJSONArray("profiles");
        if(pa!=null)for(int i=0;i<pa.length();i++){
            JSONObject o=pa.optJSONObject(i);if(o!=null)profiles.add(Profile.fromJson(o));
        }
        SecureStore.save(c,profiles);

        ArrayList<SendStat> stats=new ArrayList<>();
        JSONArray sa=root.optJSONArray("stats");
        if(sa!=null)for(int i=0;i<sa.length();i++){
            JSONObject o=sa.optJSONObject(i);if(o!=null)stats.add(SendStat.fromJson(o));
        }
        SendStatsStore.save(c,stats);
        DebugProfileManager.ensure(c);
        DeviceTransferStore.refresh(c);
    }

    private static SecretKeySpec derive(char[] password,byte[] salt) throws Exception{
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);
        byte[] key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(key,"AES");
    }

    private static byte[] readAll(Context c,Uri uri) throws Exception{
        try(InputStream in=c.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new IllegalStateException("Backup-Datei konnte nicht geöffnet werden.");
            byte[] buf=new byte[32*1024];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
            return out.toByteArray();
        }
    }
}
