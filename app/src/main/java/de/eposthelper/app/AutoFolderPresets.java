package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AutoFolderPresets {
    private AutoFolderPresets(){}

    public static List<String> presetNames(){
        List<String> names=new ArrayList<>();
        String[] colors={"SW","Farbe"};
        String[] sides={"einseitig","duplex"};
        String[] shipping={"national","international"};

        for(String color:colors)for(String side:sides)for(String ship:shipping){
            names.add(color+"_"+side+"_"+ship);
            names.add(color+"_"+side+"_"+ship+"_korrektur");
        }

        // Common registered-mail variants. Return receipt remains provider-specific,
        // so we do not create it by default.
        for(String color:colors)for(String side:sides){
            names.add(color+"_"+side+"_national_einschreiben");
            names.add(color+"_"+side+"_national_einschreiben_korrektur");
            names.add(color+"_"+side+"_national_einwurf");
            names.add(color+"_"+side+"_national_einwurf_korrektur");
        }
        return names;
    }

    public static int createFolders(Context c){
        String uri=SettingsStore.outboxFolder(c);
        if(uri==null||uri.isBlank())return 0;
        DocumentFile root=DocumentFile.fromTreeUri(c,Uri.parse(uri));
        if(root==null||!root.canWrite())return 0;
        java.util.HashSet<String> existing=new java.util.HashSet<>();
        for(DocumentFile f:root.listFiles()){
            if(f.isDirectory()&&f.getName()!=null)existing.add(f.getName().toLowerCase(Locale.ROOT));
        }
        int created=0;
        for(String name:presetNames()){
            String key=name.toLowerCase(Locale.ROOT);
            if(existing.contains(key))continue;
            if(root.createDirectory(name)!=null){
                existing.add(key);
                created++;
            }
        }
        return created;
    }

    public static int importPrepared(Context c){
        String uri=SettingsStore.outboxFolder(c);
        if(uri==null||uri.isBlank())return 0;
        DocumentFile root=DocumentFile.fromTreeUri(c,Uri.parse(uri));
        if(root==null||!root.canRead())return 0;

        int count=0;
        for(DocumentFile dir:root.listFiles()){
            if(!dir.isDirectory())continue;
            String name=dir.getName()==null?"":dir.getName();
            if("debug".equalsIgnoreCase(name))continue;

            JobOptions options=parse(name);
            if(options==null)continue;

            for(DocumentFile f:dir.listFiles()){
                if(!f.isFile()||!isPdf(f))continue;
                if(alreadyImported(c,f.getUri().toString()))continue;

                try{
                    OutboxItem raw=OutboxStore.add(c,f.getUri(),f.getName()==null?"PDF":f.getName(),true,options,name);

                    PreparedJob job=new PreparedJob();
                    job.name=f.getName()==null?"Brief":f.getName();
                    job.color=options.color;
                    job.duplex=options.duplex;
                    job.registered=options.registered;
                    job.c4=options.c4;
                    job.shipping=options.shipping;
                    job.addressCorrection=options.addressCorrection;

                    Profile profile=ProfileCompatibility.findFirst(c,options);
                    if(profile!=null){
                        job.profileId=profile.id;
                        if(options.addressCorrection){
                            job.sourceSender=AddressCorrectionProcessor.decode(profile.senderWindow);
                            job.sourceRecipient=AddressCorrectionProcessor.decode(profile.recipientWindow);
                            if(job.sourceSender.isEmpty())job.sourceSender=AddressLayoutRules.normalSender();
                            if(job.sourceRecipient.isEmpty())job.sourceRecipient=AddressLayoutRules.normalRecipient();
                            job.targetSender=AddressLayoutRules.moveLike(job.sourceSender,AddressLayoutRules.targetSender(profile,options));
                            job.targetRecipient=AddressLayoutRules.moveLike(job.sourceRecipient,AddressLayoutRules.targetRecipient(profile,options));
                        }
                    }

                    File tmp=File.createTempFile("auto-prepared-",".pdf",c.getCacheDir());
                    try(java.io.InputStream in=c.getContentResolver().openInputStream(f.getUri());
                        java.io.FileOutputStream out=new java.io.FileOutputStream(tmp)){
                        if(in==null)throw new IllegalStateException("PDF kann nicht gelesen werden");
                        byte[] buf=new byte[64*1024];int n;
                        while((n=in.read(buf))!=-1)out.write(buf,0,n);
                    }

                    File persisted=PreparedJobStore.persistPdf(c,tmp,job.id);
                    tmp.delete();
                    job.filePath=persisted.getAbsolutePath();
                    job.sourceUri=f.getUri().toString();
                    job.sourceUris.add(job.sourceUri);
                    job.deleteSourceAfterSend=true;
                    job.inputNames.add(job.name);

                    RectFHolder.populateRecipientKey(c,job,profile);
                    PreparedJobStore.upsert(c,job);
                    count++;
                }catch(Exception ignored){}
            }
        }
        return count;
    }

    public static JobOptions parse(String folder){
        if(folder==null)return null;
        String n=normalize(folder);
        if(!(n.startsWith("sw_")||n.startsWith("farbe_")))return null;

        JobOptions o=new JobOptions();
        o.color=n.startsWith("farbe_");
        o.duplex=n.contains("_duplex")||n.contains("_beidseitig")||n.contains("_doppelseitig");
        o.addressCorrection=n.contains("_korrektur");
        o.shipping=n.contains("_international")?"international":"national";
        o.c4=n.contains("_c4");

        if(n.contains("_rueckschein")||n.contains("_ruckschein"))o.registered="Einschreiben Rückschein";
        else if(n.contains("_einwurf"))o.registered="Einschreiben Einwurf";
        else if(n.contains("_einschreiben"))o.registered="Einschreiben";
        else o.registered="Nein";
        return o;
    }

    private static String normalize(String v){
        return v.toLowerCase(Locale.GERMANY)
                .replace("ä","ae").replace("ö","oe").replace("ü","ue").replace("ß","ss");
    }

    private static boolean isPdf(DocumentFile f){
        String fn=f.getName()==null?"":f.getName().toLowerCase(Locale.ROOT);
        String mime=f.getType()==null?"":f.getType();
        return fn.endsWith(".pdf")||"application/pdf".equals(mime);
    }

    private static boolean alreadyImported(Context c,String sourceUri){
        for(PreparedJob j:PreparedJobStore.load(c)){
            if(sourceUri.equals(j.sourceUri))return true;
            for(String u:j.sourceUris)if(sourceUri.equals(u))return true;
        }
        return false;
    }

    private static DocumentFile findDir(DocumentFile root,String name){
        for(DocumentFile f:root.listFiles())
            if(f.isDirectory()&&name.equalsIgnoreCase(f.getName()))return f;
        return null;
    }

    // Keeps OCR/text extraction details out of the folder parser.
    private static final class RectFHolder {
        static void populateRecipientKey(Context c,PreparedJob job,Profile profile){
            try{
                android.graphics.RectF keyArea=!job.sourceRecipient.isEmpty()?job.sourceRecipient:
                        profile==null?AddressLayoutRules.normalRecipient():AddressCorrectionProcessor.decode(profile.recipientWindow);
                job.recipientKey=AddressTextExtractor.recipientKey(c,new File(job.filePath),keyArea);
            }catch(Exception ignored){}
        }
    }
}
