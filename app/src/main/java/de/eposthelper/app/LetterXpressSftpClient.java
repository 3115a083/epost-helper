package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import java.io.InputStream;
import java.util.Properties;

public final class LetterXpressSftpClient {
    public static final String HOST="sftp.letterxpress.de";
    public static final int PORT=279;
    private LetterXpressSftpClient(){}

    private static String normalize(String x){return x==null?"":x.replace(":","").replace(" ","").toLowerCase();}

    private static final class FingerprintRepo implements HostKeyRepository {
        private final JSch jsch;
        private final String expected;
        String captured="";
        FingerprintRepo(JSch jsch,String expected){this.jsch=jsch;this.expected=normalize(expected);}
        @Override public int check(String host,byte[] key){
            try{
                HostKey hk=new HostKey(host,key);
                captured=hk.getFingerPrint(jsch);
                if(expected.isBlank())return NOT_INCLUDED;
                return normalize(captured).equals(expected)?OK:CHANGED;
            }catch(Exception e){return CHANGED;}
        }
        @Override public void add(HostKey hostkey,UserInfo ui){}
        @Override public void remove(String host,String type){}
        @Override public void remove(String host,String type,byte[] key){}
        @Override public String getKnownHostsRepositoryID(){return "E-POST Helper pinned host key";}
        @Override public HostKey[] getHostKey(){return null;}
        @Override public HostKey[] getHostKey(String host,String type){return null;}
    }

    private static Session session(Profile p,FingerprintRepo repo) throws Exception{
        JSch jsch=new JSch();
        jsch.setHostKeyRepository(repo);
        Session s=jsch.getSession(p.username,HOST,PORT);
        s.setPassword(p.password);
        Properties cfg=new Properties();
        cfg.put("StrictHostKeyChecking","yes");
        s.setConfig(cfg);
        s.connect(15000);
        return s;
    }

    public static String discoverFingerprint(Profile p) throws Exception{
        JSch jsch=new JSch();
        FingerprintRepo repo=new FingerprintRepo(jsch,"");
        jsch.setHostKeyRepository(repo);
        Session s=jsch.getSession(p.username==null?"":p.username,HOST,PORT);
        Properties cfg=new Properties();cfg.put("StrictHostKeyChecking","yes");s.setConfig(cfg);
        try{s.connect(8000);}
        catch(Exception expected){
            if(!repo.captured.isBlank())return repo.captured;
            throw expected;
        }finally{if(s.isConnected())s.disconnect();}
        if(repo.captured.isBlank())throw new IllegalStateException("SSH Host-Key konnte nicht ermittelt werden");
        return repo.captured;
    }

    private static Session verifiedSession(Profile p) throws Exception{
        if(p.sshHostKey==null||p.sshHostKey.isBlank())
            throw new IllegalArgumentException("SFTP Host-Key-Fingerprint fehlt.");
        JSch jsch=new JSch();
        FingerprintRepo repo=new FingerprintRepo(jsch,p.sshHostKey);
        jsch.setHostKeyRepository(repo);
        Session s=jsch.getSession(p.username,HOST,PORT);
        s.setPassword(p.password);
        Properties cfg=new Properties();cfg.put("StrictHostKeyChecking","yes");s.setConfig(cfg);
        try{s.connect(15000);return s;}
        catch(Exception e){
            if(!repo.captured.isBlank()&&!normalize(repo.captured).equals(normalize(p.sshHostKey)))
                throw new SecurityException("SFTP Host-Key stimmt nicht mit dem gespeicherten Fingerprint überein.");
            throw e;
        }
    }

    public static String test(Profile p) throws Exception{
        Session s=verifiedSession(p);
        try{
            ChannelSftp ch=(ChannelSftp)s.openChannel("sftp");ch.connect(10000);
            try{return "SFTP erreichbar · "+ch.pwd();}finally{ch.disconnect();}
        }finally{s.disconnect();}
    }

    private static String fileCode(JobOptions o){
        String mode=o.duplex?"D":"S";
        String color=o.color?"4":"1";
        String ship="international".equals(o.shipping)?"I":"N";
        String r=o.lxpRegistered();
        String reg="r1".equals(r)?"R1":"r2".equals(r)?"R2":"R0";
        return mode+"_"+color+"_"+ship+"_"+(o.c4?"1":"0")+"_"+reg+"_U0_FR_0";
    }

    public static void send(Context c,Uri pdf,Profile p,JobOptions o) throws Exception{
        Session s=verifiedSession(p);
        try{
            ChannelSftp ch=(ChannelSftp)s.openChannel("sftp");ch.connect(10000);
            try(InputStream in=c.getContentResolver().openInputStream(pdf)){
                if(in==null)throw new IllegalStateException("PDF kann nicht geöffnet werden");
                String name=fileCode(o)+"#epost-helper-"+System.currentTimeMillis()+".pdf";
                ch.put(in,name,ChannelSftp.OVERWRITE);
            }finally{ch.disconnect();}
        }finally{s.disconnect();}
    }
}
