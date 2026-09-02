package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.InputStream;
import java.util.Properties;

public final class LetterXpressSftpClient {
    public static final String HOST="sftp.letterxpress.de";
    public static final int PORT=279;
    private LetterXpressSftpClient(){}

    private static Session session(Profile p,boolean strict) throws Exception{
        JSch jsch=new JSch();
        if(strict){
            if(p.sshHostKey==null||p.sshHostKey.isBlank())
                throw new IllegalArgumentException("SFTP-Host-Key-Fingerprint fehlt. Ermittle ihn zuerst mit der Verbindungsprüfung und bestätige ihn anschließend im Profil.");
        }
        Session s=jsch.getSession(p.username,HOST,PORT);
        s.setPassword(p.password);
        Properties cfg=new Properties();
        cfg.put("StrictHostKeyChecking",strict?"yes":"no");
        s.setConfig(cfg);
        s.connect(15000);
        if(strict){
            HostKey hk=s.getHostKey();
            String fp=hk.getFingerPrint(jsch);
            if(!normalize(fp).equals(normalize(p.sshHostKey))){
                s.disconnect();
                throw new SecurityException("SFTP Host-Key stimmt nicht mit dem gespeicherten Fingerprint überein.");
            }
        }
        return s;
    }

    private static String normalize(String x){return x==null?"":x.replace(":","").replace(" ","").toLowerCase();}

    public static String discoverFingerprint(Profile p) throws Exception{
        Session s=session(p,false);
        try{
            HostKey hk=s.getHostKey();
            return hk.getFingerPrint(new JSch());
        }finally{s.disconnect();}
    }

    public static String test(Profile p) throws Exception{
        Session s=session(p,true);
        try{
            ChannelSftp ch=(ChannelSftp)s.openChannel("sftp");
            ch.connect(10000);
            try{return "SFTP erreichbar · "+ch.pwd();}
            finally{ch.disconnect();}
        }finally{s.disconnect();}
    }

    private static String fileCode(JobOptions o){
        String mode=o.duplex?"D":"S";
        String color=o.color?"4":"1";
        String ship="international".equals(o.shipping)?"I":"N";
        String reg="";
        String r=o.lxpRegistered();
        if("r1".equals(r))reg="R1"; else if("r2".equals(r))reg="R2"; else reg="R0";
        return mode+"_"+color+"_"+ship+"_"+(o.c4?"1":"0")+"_"+reg+"_U0_FR_0";
    }

    public static void send(Context c,Uri pdf,Profile p,JobOptions o) throws Exception{
        Session s=session(p,true);
        try{
            ChannelSftp ch=(ChannelSftp)s.openChannel("sftp");
            ch.connect(10000);
            try(InputStream in=c.getContentResolver().openInputStream(pdf)){
                if(in==null)throw new IllegalStateException("PDF kann nicht geöffnet werden");
                String name=fileCode(o)+"#epost-helper-"+System.currentTimeMillis()+".pdf";
                ch.put(in,name,ChannelSftp.OVERWRITE);
            }finally{ch.disconnect();}
        }finally{s.disconnect();}
    }
}
