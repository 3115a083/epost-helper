package de.eposthelper.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public final class DigestAuthenticator implements Authenticator {
    private final Profile profile;
    public DigestAuthenticator(Profile profile){ this.profile=profile; }

    @Override public Request authenticate(Route route, Response response) throws java.io.IOException {
        if(profile.username==null||profile.username.isBlank()) return null;
        if(responseCount(response)>=3) return null;

        String header=response.header("WWW-Authenticate");
        if(header==null) return null;

        if(header.regionMatches(true,0,"Basic",0,5)){
            return response.request().newBuilder()
                    .header("Authorization",Credentials.basic(profile.username,profile.password==null?"":profile.password,StandardCharsets.UTF_8))
                    .build();
        }
        if(!header.regionMatches(true,0,"Digest",0,6)) return null;

        Map<String,String> p=parse(header.substring(6));
        String realm=p.get("realm"), nonce=p.get("nonce");
        if(realm==null||nonce==null) return null;
        String algorithm=p.getOrDefault("algorithm","MD5");
        String qop=p.get("qop");
        if(qop!=null&&qop.contains(",")) qop="auth";
        if(qop!=null&&!qop.isBlank()&&!qop.toLowerCase().contains("auth")) return null;

        String method=response.request().method();
        String uri=response.request().url().encodedPath();
        if(response.request().url().encodedQuery()!=null) uri+="?"+response.request().url().encodedQuery();

        String cnonce=UUID.randomUUID().toString().replace("-","").substring(0,16);
        String nc="00000001";
        String ha1=hash(algorithm,profile.username+":"+realm+":"+(profile.password==null?"":profile.password));
        if("MD5-sess".equalsIgnoreCase(algorithm)||"SHA-256-sess".equalsIgnoreCase(algorithm))
            ha1=hash(baseAlgorithm(algorithm),ha1+":"+nonce+":"+cnonce);
        String ha2=hash(baseAlgorithm(algorithm),method+":"+uri);
        String digest;
        if(qop!=null&&!qop.isBlank()) digest=hash(baseAlgorithm(algorithm),ha1+":"+nonce+":"+nc+":"+cnonce+":auth:"+ha2);
        else digest=hash(baseAlgorithm(algorithm),ha1+":"+nonce+":"+ha2);

        StringBuilder auth=new StringBuilder("Digest ");
        auth.append("username=\"").append(escape(profile.username)).append("\", ");
        auth.append("realm=\"").append(escape(realm)).append("\", ");
        auth.append("nonce=\"").append(escape(nonce)).append("\", ");
        auth.append("uri=\"").append(escape(uri)).append("\", ");
        auth.append("response=\"").append(digest).append("\"");
        if(algorithm!=null) auth.append(", algorithm=").append(algorithm);
        if(qop!=null&&!qop.isBlank()) auth.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\"");
        if(p.get("opaque")!=null) auth.append(", opaque=\"").append(escape(p.get("opaque"))).append("\"");

        return response.request().newBuilder().header("Authorization",auth.toString()).build();
    }

    private static int responseCount(Response response){
        int n=1; while((response=response.priorResponse())!=null)n++; return n;
    }
    private static String baseAlgorithm(String a){
        return a!=null&&a.toUpperCase().startsWith("SHA-256")?"SHA-256":"MD5";
    }
    private static String hash(String algorithm,String value) throws java.io.IOException{
        try{
            MessageDigest md=MessageDigest.getInstance(baseAlgorithm(algorithm));
            byte[] out=md.digest(value.getBytes(StandardCharsets.ISO_8859_1));
            StringBuilder s=new StringBuilder();
            for(byte b:out)s.append(String.format("%02x",b&0xff));
            return s.toString();
        }catch(Exception e){ throw new java.io.IOException("Digest-Authentifizierung nicht unterstützt",e); }
    }
    private static String escape(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }

    private static Map<String,String> parse(String s){
        Map<String,String> out=new HashMap<>();
        int i=0;
        while(i<s.length()){
            while(i<s.length()&&(s.charAt(i)==' '||s.charAt(i)==','))i++;
            int eq=s.indexOf('=',i); if(eq<0)break;
            String k=s.substring(i,eq).trim(); i=eq+1;
            String v;
            if(i<s.length()&&s.charAt(i)=='\"'){
                i++; StringBuilder b=new StringBuilder();
                while(i<s.length()){
                    char c=s.charAt(i++);
                    if(c=='\\'&&i<s.length()) b.append(s.charAt(i++));
                    else if(c=='\"') break;
                    else b.append(c);
                }
                v=b.toString();
            }else{
                int comma=s.indexOf(',',i); if(comma<0)comma=s.length();
                v=s.substring(i,comma).trim(); i=comma;
            }
            out.put(k,v);
        }
        return out;
    }
}
