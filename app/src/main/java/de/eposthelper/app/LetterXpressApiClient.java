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

    public static String balance(Profile p) throws Exception{
        JSONObject root=new JSONObject();root.put("auth",auth(p,"live"));
        JSONObject out=getWithJsonBody("/balance",root);
        JSONObject data=out.optJSONObject("data");
        if(data==null)return "";
        return String.format(java.util.Locale.GERMANY,"%.2f %s",data.optDouble("balance",0),data.optString("currency","EUR"));
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

    public static java.util.List<RecentLetter> recentJobs(Profile p,int limit) throws Exception{
        JSONObject root=new JSONObject();root.put("auth",auth(p,"live"));
        JSONObject out=getWithJsonBody("/printjobs",root);
        java.util.ArrayList<RecentLetter> result=new java.util.ArrayList<>();
        JSONObject data=out.optJSONObject("data");
        org.json.JSONArray jobs=data==null?null:data.optJSONArray("printjobs");
        if(jobs==null)return result;
        for(int i=0;i<jobs.length()&&result.size()<limit;i++){
            JSONObject job=jobs.optJSONObject(i);if(job==null)continue;
            org.json.JSONArray items=job.optJSONArray("items");
            if(items==null||items.length()==0){
                RecentLetter x=new RecentLetter();
                x.id=job.optString("id");
                x.filename=job.optString("filename_original","");
                x.status=job.optString("status","");
                x.createdAt=job.optString("created_at","");
                result.add(x);
                continue;
            }
            for(int j=0;j<items.length()&&result.size()<limit;j++){
                JSONObject item=items.optJSONObject(j);if(item==null)continue;
                RecentLetter x=new RecentLetter();
                x.id=job.optString("id");
                x.filename=job.optString("filename_original","");
                x.status=item.optString("status",job.optString("status",""));
                x.address=item.optString("address","");
                x.createdAt=job.optString("created_at","");
                x.amount=item.optDouble("amount",0);
                x.vat=item.optDouble("vat",0);
                String reg=item.optString("registered","");
                if("r1".equalsIgnoreCase(reg))x.registered="Einschreiben Einwurf";
                else if("r2".equalsIgnoreCase(reg))x.registered="Einschreiben";
                JSONObject spec=item.optJSONObject("specification");
                if(spec==null)spec=job.optJSONObject("specification");
                if(spec!=null){
                    x.color="4".equals(spec.optString("color"))||spec.optBoolean("color",false);
                    x.duplex="duplex".equalsIgnoreCase(spec.optString("mode"))||spec.optBoolean("duplex",false);
                    x.shipping=spec.optString("shipping","national");
                }
                result.add(x);
            }
        }
        return result;
    }

    private static final class NullOutput extends java.io.OutputStream{
        @Override public void write(int b){}
        @Override public void write(byte[] b,int off,int len){}
    }

    private static String base64Md5AndValidate(Context c,Uri uri) throws Exception{
        MessageDigest md=MessageDigest.getInstance("MD5");
        java.security.DigestOutputStream digest=new java.security.DigestOutputStream(new NullOutput(),md);
        android.util.Base64OutputStream b64=new android.util.Base64OutputStream(digest,Base64.NO_WRAP|Base64.NO_CLOSE);
        long total=0;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IllegalStateException("PDF kann nicht geöffnet werden");
            byte[] buf=new byte[64*1024];int n;
            while((n=in.read(buf))!=-1){
                total+=n;
                if(total>MAX)throw new IllegalArgumentException("LetterXpress API erlaubt maximal 50 MB pro PDF.");
                b64.write(buf,0,n);
            }
            b64.close();
        }
        byte[] sum=md.digest();
        StringBuilder hex=new StringBuilder();for(byte b:sum)hex.append(String.format("%02x",b&0xff));
        return hex.toString();
    }

    public static void send(Context c,Uri pdf,Profile p,JobOptions o) throws Exception{
        final String checksum=base64Md5AndValidate(c,pdf);
        final String filename="epost-helper-"+System.currentTimeMillis()+".pdf";
        final JSONObject authObject=auth(p,"live");
        final JSONObject spec=specification(o,0);
        final String reg=o.lxpRegistered();

        RequestBody streaming=new RequestBody(){
            @Override public MediaType contentType(){return JSON;}
            @Override public long contentLength(){return -1L;}
            @Override public void writeTo(okio.BufferedSink sink) throws java.io.IOException{
                try{
                    String prefix="{\"auth\":"+authObject.toString()+",\"letter\":{\"base64_file\":\"";
                    sink.writeUtf8(prefix);
                    android.util.Base64OutputStream b64=new android.util.Base64OutputStream(sink.outputStream(),Base64.NO_WRAP|Base64.NO_CLOSE);
                    try(InputStream in=c.getContentResolver().openInputStream(pdf)){
                        if(in==null)throw new java.io.IOException("PDF kann nicht geöffnet werden");
                        byte[] buf=new byte[64*1024];int n;
                        while((n=in.read(buf))!=-1)b64.write(buf,0,n);
                    }
                    // Base64OutputStream emits the final quartet/padding only when closed.
                    // NO_CLOSE keeps OkHttp's underlying sink open so the JSON suffix can follow.
                    b64.close();
                    String suffix="\",\"base64_file_checksum\":\""+checksum+"\",\"filename_original\":"+JSONObject.quote(filename)+
                            ",\"specification\":"+spec.toString()+
                            (reg.isBlank()?"":",\"registered\":"+JSONObject.quote(reg))+
                            "}}";
                    sink.writeUtf8(suffix);
                }catch(java.io.IOException e){throw e;}
                catch(Exception e){throw new java.io.IOException(e);}
            }
        };

        OkHttpClient client=new OkHttpClient();
        Request req=new Request.Builder().url(BASE+"/printjobs").post(streaming).header("Content-Type","application/json").build();
        try(Response response=client.newCall(req).execute()){
            String raw=response.body()==null?"":response.body().string();
            JSONObject out=raw.isBlank()?new JSONObject():new JSONObject(raw);
            if(!response.isSuccessful()||out.optInt("status",response.code())>=400)
                throw new DiagnosticException("LetterXpress-Anfrage fehlgeschlagen.",
                        "provider=LetterXpress\ntransport=API\nhttpStatus="+response.code()+"\nendpoint=/printjobs\nmessage="+out.optString("message",""));
        }
    }

}
