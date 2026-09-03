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
        String[] sides={"einseitig","beidseitig"};
        for(String c:colors)for(String s:sides){
            names.add(c+"_"+s);
            names.add(c+"_"+s+"_korrektur");
            names.add(c+"_"+s+"_international");
            names.add(c+"_"+s+"_international_korrektur");
            names.add(c+"_"+s+"_einschreiben");
            names.add(c+"_"+s+"_einschreiben_korrektur");
        }
        return names;
    }

    public static int createFolders(Context c){
        String uri=SettingsStore.outboxFolder(c);
        if(uri==null||uri.isBlank())return 0;
        DocumentFile root=DocumentFile.fromTreeUri(c,Uri.parse(uri));
        if(root==null||!root.canWrite())return 0;
        int created=0;
        for(String name:presetNames()){
            if(findDir(root,name)==null&&root.createDirectory(name)!=null)created++;
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
                if(!f.isFile())continue;
                String fn=f.getName()==null?"":f.getName();
                String mime=f.getType()==null?"":f.getType();
                if(!fn.toLowerCase(Locale.ROOT).endsWith(".pdf")&&!"application/pdf".equals(mime))continue;
                if(alreadyImported(c,f.getUri().toString()))continue;

                try{
                    PreparedJob job=new PreparedJob();
                    job.name=fn;
                    job.color=options.color;job.duplex=options.duplex;job.registered=options.registered;
                    job.c4=options.c4;job.shipping=options.shipping;job.addressCorrection=options.addressCorrection;
                    File tmp=File.createTempFile("auto-prepared-",".pdf",c.getCacheDir());
                    try(java.io.InputStream in=c.getContentResolver().openInputStream(f.getUri());
                        java.io.FileOutputStream out=new java.io.FileOutputStream(tmp)){
                        if(in==null)throw new IllegalStateException("PDF kann nicht gelesen werden");
                        byte[] buf=new byte[64*1024];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                    }
                    File persisted=PreparedJobStore.persistPdf(c,tmp,job.id);tmp.delete();
                    job.filePath=persisted.getAbsolutePath();
                    job.sourceUri=f.getUri().toString();
                    job.sourceUris.add(job.sourceUri);
                    job.deleteSourceAfterSend=true;
                    job.inputNames.add(fn);
                    PreparedJobStore.upsert(c,job);
                    count++;
                }catch(Exception ignored){}
            }
        }
        return count;
    }

    public static JobOptions parse(String folder){
        if(folder==null)return null;
        String n=folder.toLowerCase(Locale.GERMANY);
        if(!(n.startsWith("sw_")||n.startsWith("farbe_")))return null;
        JobOptions o=new JobOptions();
        o.color=n.startsWith("farbe_");
        o.duplex=n.contains("_beidseitig");
        o.addressCorrection=n.contains("_korrektur");
        o.shipping=n.contains("_international")?"international":"national";
        o.registered=n.contains("_einschreiben")?"Einschreiben":"Nein";
        return o;
    }

    private static boolean alreadyImported(Context c,String sourceUri){
        for(PreparedJob j:PreparedJobStore.load(c))if(sourceUri.equals(j.sourceUri))return true;
        return false;
    }

    private static DocumentFile findDir(DocumentFile root,String name){
        for(DocumentFile f:root.listFiles())if(f.isDirectory()&&name.equalsIgnoreCase(f.getName()))return f;
        return null;
    }
}
