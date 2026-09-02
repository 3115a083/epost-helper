package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class LetterXpressApiClient {
    private static final String BASE="https://api.letterxpress.de/v3";
    private static final MediaType JSON=MediaType.get("application/json");
    private static final long MAX=50L*1024L*1024L;
    private LetterXpressApiClient(){}

    private static JSONObject auth(Profile p,String mode) throws Exception{
        JSONObject a=new JSONObject();
        a.put("username",p.username);
        a.put("apikey",p.apiKey);
        a.put("mode",mode);
        return a;
    }

    private static JSONObject specification(JobOptions o,int pages) throws Exception{
        JSONObject s=new JSONObject();
        if(pages>0)s.put("pages",pages);
        s.put("color",o.color?"4":"1");
        s.put("mode",o.duplex?"duplex":"simplex");
        s.put("shipping",o.shipping);
        s.put("c4",o.c4?1:0);
        return s;
    }

    private static JSONObject execute(String method,String path,JSONObject body) throws Exception{
        OkHttpClient client=new OkHttpClient();
        RequestBody rb=RequestBody.create(body.toString(),JSON);
        Request.Builder b=new Request.Builder().url(BASE+path).header("Content-Type","application/json");
        if("POST".equals(method))b.post(rb); else b.method(method,rb);
        try(Response r=client.newCall(b.build()).execute()){
            String raw=r.body()==null?"":r.body().string();
            JSONObject out=raw.isBlank()?new JSONObject():new JSONObject(raw);
            if(!r.isSuccessful()||out.optInt("status",r.code())>=400)
                throw new DiagnosticException("LetterXpress-Anmeldung oder Anfrage fehlgeschlagen.",
                        "provider=LetterXpress\ntransport=API\nhttpStatus="+r.code()+"\nendpoint="+path+"\nmessage="+out.optString("message",""));
            return out;
        }
    }

    public static String test(Profile p) throws Exception{
        JSONObject root=new JSONObject();
        root.put("auth",auth(p,"test"));
        JSONObject out=execute("GET","/balance",root);
        JSONObject data=out.optJSONObject("data");
        return data==null?"API erreichbar":"API erreichbar · Guthaben "+data.optDouble("balance",0)+" "+data.optString("currency","EUR");
    }

    public static double price(Profile p,JobOptions o,int pages) throws Exception{
        JSONObject root=new JSONObject();
        root.put("auth",auth(p,"test"));
        JSONObject letter=new JSONObject();
        letter.put("specification",specification(o,pages));
        String reg=o.lxpRegistered(); if(!reg.isBlank())letter.put("registered",reg);
        root.put("letter",letter);
        JSONObject out=execute("GET","/price",root);
        JSONObject data=out.optJSONObject("data");
        if(data==null)throw new IllegalStateException("Keine Preisangabe erhalten");
        return data.optDouble("price",-1);
    }

    private static byte[] readPdf(Context c,Uri uri) throws Exception{
        try(InputStream in=c.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new IllegalStateException("PDF kann nicht geöffnet werden");
            byte[] buf=new byte[64*1024];int n;long total=0;
            while((n=in.read(buf))!=-1){
                total+=n;
                if(total>MAX)throw new IllegalArgumentException("LetterXpress API erlaubt maximal 50 MB pro PDF.");
                out.write(buf,0,n);
            }
            return out.toByteArray();
        }
    }

    public static void send(Context c,Uri pdf,Profile p,JobOptions o) throws Exception{
        byte[] bytes=readPdf(c,pdf);
        String encoded=Base64.encodeToString(bytes,Base64.NO_WRAP);
        MessageDigest md=MessageDigest.getInstance("MD5");
        byte[] sum=md.digest(encoded.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        StringBuilder hex=new StringBuilder(); for(byte b:sum)hex.append(String.format("%02x",b&0xff));

        JSONObject root=new JSONObject();
        root.put("auth",auth(p,"live"));
        JSONObject letter=new JSONObject();
        letter.put("base64_file",encoded);
        letter.put("base64_file_checksum",hex.toString());
        letter.put("filename_original","epost-helper-"+System.currentTimeMillis()+".pdf");
        letter.put("specification",specification(o,0));
        String reg=o.lxpRegistered();if(!reg.isBlank())letter.put("registered",reg);
        root.put("letter",letter);
        execute("POST","/printjobs",root);
    }
}
