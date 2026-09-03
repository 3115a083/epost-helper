package de.eposthelper.app;

import java.nio.charset.StandardCharsets;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
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
        if(Profile.TYPE_IPP.equals(p.type))
            throw new IllegalStateException("Direkter Netzwerkdrucker-Versand wird nicht unterstützt. E-POST Netzwerkdrucker werden laut Deutsche Post ausschließlich über die StarterApp eingerichtet.");
        return testWebDav(client,url,p);
    }

    public static List<String> webDavCollections(Profile p) throws Exception{
        String url=Sender.normalizeSecureUrl(p.url);
        String base=url.endsWith("/")?url:url+"/";
        OkHttpClient client=NetworkClientFactory.create(p);
        String body="<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:displayname/></d:prop></d:propfind>";
        Request probe=auth(new Request.Builder().url(base),p)
                .method("PROPFIND",RequestBody.create(body,MediaType.get("application/xml; charset=utf-8")))
                .header("Depth","1").build();
        try(Response r=client.newCall(probe).execute()){
            if(r.code()==401)throw auth401("WebDAV","PROPFIND",url,r);
            if(r.code()!=207&&!r.isSuccessful())throw new IllegalStateException("WebDAV antwortet mit HTTP "+r.code());
            String xml=r.body()==null?"":r.body().string();
            return parseCollections(base,xml);
        }
    }

    private static List<String> parseCollections(String base,String xml) throws Exception{
        List<String> result=new ArrayList<>();
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        org.w3c.dom.Document doc=f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        org.w3c.dom.NodeList responses=doc.getElementsByTagNameNS("DAV:","response");
        String basePath=new URI(base).getPath();
        for(int i=0;i<responses.getLength();i++){
            org.w3c.dom.Element response=(org.w3c.dom.Element)responses.item(i);
            org.w3c.dom.NodeList types=response.getElementsByTagNameNS("DAV:","collection");
            if(types.getLength()==0)continue;
            org.w3c.dom.NodeList hrefs=response.getElementsByTagNameNS("DAV:","href");
            if(hrefs.getLength()==0)continue;
            String href=hrefs.item(0).getTextContent();
            String path=new URI(href).getPath();
            if(path==null||path.equals(basePath)||path.equals(basePath.endsWith("/")?basePath.substring(0,basePath.length()-1):basePath))continue;
            String relative=path.startsWith(basePath)?path.substring(basePath.length()):path;
            relative=URLDecoder.decode(relative,StandardCharsets.UTF_8).replaceAll("^/+|/+$","");
            if(!relative.isBlank()&&!relative.contains("/")&&!result.contains(relative))result.add(relative);
        }
        return result;
    }

    private static String testWebDav(OkHttpClient client,String url,Profile p) throws Exception{
        List<String> collections=webDavCollections(p);
        String base=url.endsWith("/")?url:url+"/";
        String info=readOptional(client,base+"Info.txt",p);
        String readme=readOptional(client,base+"README.txt",p);
        StringBuilder out=new StringBuilder("E-POST WebDAV erreichbar");
        if(collections.isEmpty())out.append("\nKeine Sammelkorb-Unterordner gefunden.");
        else out.append("\n").append(collections.size()).append(" Sammelkorb").append(collections.size()==1?"":"-Unterordner").append(" gefunden.");
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
        Request req=new Request.Builder().url(url)
                .post(RequestBody.create(request,IPP)).header("Content-Type","application/ipp").build();
        try(Response r=client.newCall(req).execute()){
            if(!r.isSuccessful()) {
                if(r.code()==401) throw auth401("IPP","POST",url,r);
                throw new IllegalStateException("IPP-Ziel antwortet mit HTTP "+r.code());
            }
            byte[] body=r.body()==null?new byte[0]:r.body().bytes();
            if(body.length<4) throw new IllegalStateException("Ungültige IPP-Antwort");
            int status=((body[2]&0xff)<<8)|(body[3]&0xff);
            if(status>=0x0400) throw new IllegalStateException("IPP-Fehler 0x"+Integer.toHexString(status));
        }
        return "Netzwerkdrucker erreichbar\nIPP-Verbindung und Authentifizierung wurden erfolgreich geprüft.";
    }

    public static DiagnosticException auth401(String protocol,String method,String url,Response r){
        String challenge=r.header("WWW-Authenticate","");
        String host=r.request().url().host();
        String path=r.request().url().encodedPath();
        String debug="protocol="+protocol+
                "\nhttpStatus=401"+
                "\nmethod="+method+
                "\nhost="+host+
                "\npath="+path+
                "\nwwwAuthenticate="+(challenge.isBlank()?"<missing>":challenge);
        return new DiagnosticException("Anmeldung abgelehnt (HTTP 401).",debug);
    }

    private static String trim(String s){
        String cleaned=s.replace("\r","").trim();
        return cleaned.length()>1800?cleaned.substring(0,1800)+"…":cleaned;
    }
}
