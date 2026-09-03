package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import okio.BufferedSink;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class Sender {
    private static final MediaType PDF=MediaType.get("application/pdf");
    private static final MediaType IPP=MediaType.get("application/ipp");
    private Sender(){}

    public static String normalizeSecureUrl(String raw){
        if(raw==null)throw new IllegalArgumentException("URL fehlt");
        String u=raw.trim();
        if(u.startsWith("ipps://"))return "https://"+u.substring("ipps://".length());
        if(u.startsWith("https://"))return u;
        throw new IllegalArgumentException("Nur HTTPS/IPPS ist erlaubt. Unverschlüsselte Ziele werden blockiert.");
    }

    private static void validatePdf(Context context,Uri uri) throws Exception{
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IllegalStateException("PDF kann nicht geöffnet werden");
            byte[] h=new byte[5];
            int n=in.read(h);
            if(n<5||h[0]!='%'||h[1]!='P'||h[2]!='D'||h[3]!='F'||h[4]!='-')
                throw new IllegalArgumentException("Die Datei ist kein gültiges PDF");
        }
    }

    private static RequestBody pdfBody(Context context,Uri uri,MediaType type,byte[] prefix){
        return new RequestBody(){
            @Override public MediaType contentType(){return type;}
            @Override public long contentLength(){return -1L;}
            @Override public void writeTo(BufferedSink sink) throws java.io.IOException{
                if(prefix!=null&&prefix.length>0)sink.write(prefix);
                try(InputStream in=context.getContentResolver().openInputStream(uri)){
                    if(in==null)throw new java.io.IOException("PDF kann nicht geöffnet werden");
                    byte[] buffer=new byte[64*1024];
                    int n;
                    while((n=in.read(buffer))!=-1)sink.write(buffer,0,n);
                }
            }
        };
    }

    public static void send(Context context,Uri pdf,Profile profile) throws Exception{
        if(profile==null)throw new IllegalArgumentException("Versandprofil fehlt");
        validatePdf(context,pdf);
        String secure=normalizeSecureUrl(profile.url);
        OkHttpClient client=NetworkClientFactory.create(profile);
        if(Profile.TYPE_IPP.equals(profile.type))
            throw new IllegalStateException("Direkter E-POST Netzwerkdrucker-Versand wird nicht unterstützt. Bitte ein Sammelkorb/WebDAV-Profil verwenden.");
        sendWebDav(context,client,secure,pdf,profile);
    }

    private static Request.Builder auth(Request.Builder rb,Profile p){
        if(p.username!=null&&!p.username.isBlank())
            rb.header("Authorization",Credentials.basic(p.username,p.password==null?"":p.password,StandardCharsets.UTF_8));
        return rb;
    }

    private static void sendWebDav(Context context,OkHttpClient client,String url,Uri pdf,Profile p) throws Exception{
        String collection=p.webDavCollection==null?"":p.webDavCollection.trim().replaceAll("^/+|/+$","");
        if(collection.isBlank())
            throw new IllegalStateException("Kein Sammelkorb ausgewählt. Das E-POST WebDAV-Hauptverzeichnis ist nicht beschreibbar.");
        String root=url.endsWith("/")?url:url+"/";
        okhttp3.HttpUrl rootUrl=okhttp3.HttpUrl.get(root);
        String base=rootUrl.newBuilder().addPathSegment(collection).addPathSegment("").build().toString();
        String name="epost-helper-"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8)+".pdf";
        Request req=auth(new Request.Builder().url(base+name),p)
                .put(pdfBody(context,pdf,PDF,null))
                .header("Content-Type","application/pdf").build();
        try(Response r=client.newCall(req).execute()){
            if(!r.isSuccessful()){
                if(r.code()==401)throw ConnectionTester.auth401("WebDAV","PUT",url,r);
                throw new IllegalStateException("Sammelkorb meldet HTTP "+r.code());
            }
        }
    }

    private static void sendIpp(Context context,OkHttpClient client,String httpsUrl,Uri pdf,Profile p) throws Exception{
        String printerUri=p.url.trim();
        if(printerUri.startsWith("https://"))printerUri="ipps://"+printerUri.substring("https://".length());
        byte[] head=IppEncoder.printJobHeader(printerUri,p.username,"E-POST Helper");
        Request req=new Request.Builder().url(httpsUrl)
                .post(pdfBody(context,pdf,IPP,head))
                .header("Content-Type","application/ipp").build();
        try(Response r=client.newCall(req).execute()){
            if(!r.isSuccessful()){
                if(r.code()==401)throw ConnectionTester.auth401("IPP","POST",httpsUrl,r);
                throw new IllegalStateException("IPP-Ziel meldet HTTP "+r.code());
            }
            byte[] response=r.body()==null?new byte[0]:r.body().bytes();
            if(response.length>=4){
                int status=((response[2]&0xff)<<8)|(response[3]&0xff);
                if(status>=0x0400)throw new IllegalStateException("IPP-Fehler 0x"+Integer.toHexString(status));
            }
        }
    }
}
