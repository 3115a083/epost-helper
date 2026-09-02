package de.eposthelper.app;

import java.nio.charset.StandardCharsets;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class ConnectionTester {
    private static final MediaType IPP=MediaType.get("application/ipp");
    private ConnectionTester(){}

    private static Request.Builder auth(Request.Builder rb,Profile p){
        if(p.username!=null&&!p.username.isBlank()){
            rb.header("Authorization",Credentials.basic(p.username,p.password==null?"":p.password,StandardCharsets.UTF_8));
        }
        return rb;
    }

    public static String test(Profile p) throws Exception{
        String url=Sender.normalizeSecureUrl(p.url);
        OkHttpClient client=NetworkClientFactory.create(p);
        if(Profile.TYPE_IPP.equals(p.type)) return testIpp(client,url,p);
        return testWebDav(client,url,p);
    }

    private static String testWebDav(OkHttpClient client,String url,Profile p) throws Exception{
        String base=url.endsWith("/")?url:url+"/";
        Request probe=auth(new Request.Builder().url(base),p)
                .method("PROPFIND",RequestBody.create(new byte[0],MediaType.get("application/xml")))
                .header("Depth","0").build();
        try(Response r=client.newCall(probe).execute()){
            if(!(r.code()==207||r.isSuccessful())) {
                if(r.code()==401) throw new IllegalStateException(authError("WebDAV",r));
                throw new IllegalStateException("WebDAV antwortet mit HTTP "+r.code());
            }
        }
        String info=readOptional(client,base+"Info.txt",p);
        String readme=readOptional(client,base+"README.txt",p);
        StringBuilder out=new StringBuilder("Sammelkorb erreichbar");
        if(!info.isBlank()) out.append("\n\nVersandoptionen laut Info.txt:\n").append(trim(info));
        if(!readme.isBlank()) out.append("\n\nHinweise/Serienbrieftrenner laut README.txt:\n").append(trim(readme));
        return out.toString();
    }

    private static String readOptional(OkHttpClient client,String url,Profile p){
        try(Response r=client.newCall(auth(new Request.Builder().url(url),p).get().build()).execute()){
            if(!r.isSuccessful()||r.body()==null) return "";
            return r.body().string();
        }catch(Exception e){ return ""; }
    }

    private static String testIpp(OkHttpClient client,String url,Profile p) throws Exception{
        String printerUri=p.url.trim();
        if(printerUri.startsWith("https://")) printerUri="ipps://"+printerUri.substring("https://".length());
        byte[] request=IppEncoder.getPrinterAttributesHeader(printerUri,p.username);
        Request req=auth(new Request.Builder().url(url),p)
                .post(RequestBody.create(request,IPP)).header("Content-Type","application/ipp").build();
        try(Response r=client.newCall(req).execute()){
            if(!r.isSuccessful()) {
                if(r.code()==401) throw new IllegalStateException(authError("IPP",r));
                throw new IllegalStateException("IPP-Ziel antwortet mit HTTP "+r.code());
            }
            byte[] body=r.body()==null?new byte[0]:r.body().bytes();
            if(body.length<4) throw new IllegalStateException("Ungültige IPP-Antwort");
            int status=((body[2]&0xff)<<8)|(body[3]&0xff);
            if(status>=0x0400) throw new IllegalStateException("IPP-Fehler 0x"+Integer.toHexString(status));
        }
        return "Netzwerkdrucker erreichbar\nIPP-Verbindung und Authentifizierung wurden erfolgreich geprüft.";
    }

    private static String authError(String kind,Response r){
        String challenge=r.header("WWW-Authenticate","");
        String hint=kind+" meldet HTTP 401. ";
        if(!challenge.isBlank()) hint+="Server-Challenge: "+challenge+". ";
        hint+="Prüfe Benutzername und Passwort. Beim E-POST Netzwerkdrucker nennt das Handbuch z. B. Benutzername@Firmen-ID.";
        return hint;
    }

    private static String trim(String s){
        String cleaned=s.replace("\r","").trim();
        return cleaned.length()>1800?cleaned.substring(0,1800)+"…":cleaned;
    }
}
