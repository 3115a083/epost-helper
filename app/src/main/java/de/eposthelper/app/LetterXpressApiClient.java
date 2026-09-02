package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class LetterXpressApiClient {
    private static final String HOST="api.letterxpress.de";
    private static final String BASE="https://"+HOST+"/v3";
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

    private static JSONObject post(String path,JSONObject body) throws Exception{
        OkHttpClient client=new OkHttpClient();
        Request req=new Request.Builder().url(BASE+path).post(RequestBody.create(body.toString(),JSON))
                .header("Content-Type","application/json").build();
        try(Response r=client.newCall(req).execute()){
            String raw=r.body()==null?"":r.body().string();
            JSONObject out=raw.isBlank()?new JSONObject():new JSONObject(raw);
            if(!r.isSuccessful()||out.optInt("status",r.code())>=400)
                throw new DiagnosticException("LetterXpress-Anfrage fehlgeschlagen.",
                        "provider=LetterXpress\ntransport=API\nhttpStatus="+r.code()+"\nendpoint="+path+"\nmessage="+out.optString("message",""));
            return out;
        }
    }

    private static JSONObject getWithJsonBody(String path,JSONObject body) throws Exception{
        byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);
        SSLSocketFactory sf=(SSLSocketFactory)SSLSocketFactory.getDefault();
        try(SSLSocket socket=(SSLSocket)sf.createSocket(HOST,443)){
            socket.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});
            socket.startHandshake();
            if(!HttpsURLConnection.getDefaultHostnameVerifier().verify(HOST,socket.getSession()))
                throw new SecurityException("Hostname-Prüfung für LetterXpress fehlgeschlagen");

            BufferedOutputStream out=new BufferedOutputStream(socket.getOutputStream());
            String head="GET /v3"+path+" HTTP/1.1\r\n"+
                    "Host: "+HOST+"\r\n"+
                    "Content-Type: application/json\r\n"+
                    "Content-Length: "+payload.length+"\r\n"+
                    "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(payload);out.flush();

            BufferedInputStream in=new BufferedInputStream(socket.getInputStream());
            String status=readLine(in);
            int code=parseStatus(status);
            int contentLength=-1;boolean chunked=false;
            String line;
            while(!(line=readLine(in)).isEmpty()){
                String lower=line.toLowerCase(java.util.Locale.ROOT);
                if(lower.startsWith("content-length:"))contentLength=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
                if(lower.startsWith("transfer-encoding:")&&lower.contains("chunked"))chunked=true;
            }
            byte[] bytes=chunked?readChunked(in):readBody(in,contentLength);
            String raw=new String(bytes,StandardCharsets.UTF_8);
            JSONObject result=raw.isBlank()?new JSONObject():new JSONObject(raw);
            if(code<200||code>=300||result.optInt("status",code)>=400)
                throw new DiagnosticException("LetterXpress-Anfrage fehlgeschlagen.",
                        "provider=LetterXpress\ntransport=API\nhttpStatus="+code+"\nendpoint="+path+"\nmessage="+result.optString("message",""));
            return result;
        }
    }

    private static int parseStatus(String s){
        try{return Integer.parseInt(s.split(" ")[1]);}catch(Exception e){return 0;}
    }
    private static String readLine(InputStream in) throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        int prev=-1,c;
        while((c=in.read())!=-1){
            if(prev=='\r'&&c=='\n'){
                byte[] a=b.toByteArray();
                int len=Math.max(0,a.length-1);
                return new String(a,0,len,StandardCharsets.ISO_8859_1);
            }
            b.write(c);prev=c;
        }
        return new String(b.toByteArray(),StandardCharsets.ISO_8859_1);
    }
    private static byte[] readBody(InputStream in,int length) throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];int n,total=0;
        while((n=in.read(buf,0,length>=0?Math.min(buf.length,length-total):buf.length))!=-1){
            if(n==0)break;b.write(buf,0,n);total+=n;if(length>=0&&total>=length)break;
        }
        return b.toByteArray();
    }
    private static byte[] readChunked(InputStream in) throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        while(true){
            String sizeLine=readLine(in).trim();
            int sem=sizeLine.indexOf(';');if(sem>=0)sizeLine=sizeLine.substring(0,sem);
            int size=Integer.parseInt(sizeLine,16);if(size==0){readLine(in);break;}
            byte[] chunk=readBody(in,size);b.write(chunk);readLine(in);
        }
        return b.toByteArray();
    }

    public static String test(Profile p) throws Exception{
        JSONObject root=new JSONObject();root.put("auth",auth(p,"test"));
        JSONObject out=getWithJsonBody("/balance",root);
        JSONObject data=out.optJSONObject("data");
        return data==null?"API erreichbar":"API erreichbar · Guthaben "+data.optDouble("balance",0)+" "+data.optString("currency","EUR");
    }

    public static double price(Profile p,JobOptions o,int pages) throws Exception{
        JSONObject root=new JSONObject();root.put("auth",auth(p,"test"));
        JSONObject letter=new JSONObject();letter.put("specification",specification(o,pages));
        String reg=o.lxpRegistered();if(!reg.isBlank())letter.put("registered",reg);
        root.put("letter",letter);
        JSONObject out=getWithJsonBody("/price",root);
        JSONObject data=out.optJSONObject("data");
        if(data==null)throw new IllegalStateException("Keine Preisangabe erhalten");
        return data.optDouble("price",-1);
    }

    private static byte[] readPdf(Context c,Uri uri) throws Exception{
        try(InputStream in=c.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new IllegalStateException("PDF kann nicht geöffnet werden");
            byte[] buf=new byte[64*1024];int n;long total=0;
            while((n=in.read(buf))!=-1){
                total+=n;if(total>MAX)throw new IllegalArgumentException("LetterXpress API erlaubt maximal 50 MB pro PDF.");
                out.write(buf,0,n);
            }
            return out.toByteArray();
        }
    }

    public static void send(Context c,Uri pdf,Profile p,JobOptions o) throws Exception{
        byte[] bytes=readPdf(c,pdf);
        String encoded=Base64.encodeToString(bytes,Base64.NO_WRAP);
        MessageDigest md=MessageDigest.getInstance("MD5");
        byte[] sum=md.digest(encoded.getBytes(StandardCharsets.US_ASCII));
        StringBuilder hex=new StringBuilder();for(byte b:sum)hex.append(String.format("%02x",b&0xff));

        JSONObject root=new JSONObject();root.put("auth",auth(p,"live"));
        JSONObject letter=new JSONObject();
        letter.put("base64_file",encoded);letter.put("base64_file_checksum",hex.toString());
        letter.put("filename_original","epost-helper-"+System.currentTimeMillis()+".pdf");
        letter.put("specification",specification(o,0));
        String reg=o.lxpRegistered();if(!reg.isBlank())letter.put("registered",reg);
        root.put("letter",letter);
        post("/printjobs",root);
    }
}
