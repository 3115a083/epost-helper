package de.eposthelper.app;

import android.content.Context;
import org.json.JSONArray;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class PreparedJobStore {
    private static final String PREF="prepared_jobs";
    private static final String KEY="jobs";
    private PreparedJobStore(){}

    public static List<PreparedJob> load(Context c){
        List<PreparedJob> out=new ArrayList<>();
        String raw=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"[]");
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++)out.add(PreparedJob.fromJson(a.getJSONObject(i)));
        }catch(Exception ignored){}
        return out;
    }

    public static void save(Context c,List<PreparedJob> jobs){
        try{
            JSONArray a=new JSONArray();for(PreparedJob j:jobs)a.put(j.toJson());
            c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply();
        }catch(Exception ignored){}
    }

    public static PreparedJob find(Context c,String id){
        if(id==null)return null;for(PreparedJob j:load(c))if(id.equals(j.id))return j;return null;
    }

    public static void upsert(Context c,PreparedJob job){
        List<PreparedJob> jobs=load(c);boolean found=false;
        for(int i=0;i<jobs.size();i++)if(jobs.get(i).id.equals(job.id)){jobs.set(i,job);found=true;break;}
        if(!found)jobs.add(job);save(c,jobs);
    }

    public static File persistUri(Context c,android.net.Uri uri,String id) throws Exception{
        File dir=new File(c.getFilesDir(),"prepared");
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Ausgangsordner konnte nicht erstellt werden");
        File dest=new File(dir,id+".pdf");
        try(java.io.InputStream in=c.getContentResolver().openInputStream(uri);
            FileOutputStream out=new FileOutputStream(dest)){
            if(in==null)throw new IllegalStateException("PDF kann nicht gelesen werden");
            byte[] buf=new byte[64*1024];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
        }
        return dest;
    }

    public static File persistPdf(Context c,File source,String id) throws Exception{
        File dir=new File(c.getFilesDir(),"prepared");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Ausgangsordner konnte nicht erstellt werden");
        File dest=new File(dir,id+".pdf");
        try(FileInputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(dest)){
            byte[] buf=new byte[64*1024];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
        }
        return dest;
    }

    public static File ensureFile(Context c,PreparedJob job) throws Exception{
        if(job==null)throw new IllegalArgumentException("Vorbereiteter Brief fehlt");
        File current=job.filePath==null?null:new File(job.filePath);
        if(current!=null&&current.exists()&&current.length()>0)return current;

        if(job.isMergedGroup()){
            List<PreparedJob> parts=job.mergedParts();
            if(parts.size()>1){
                for(PreparedJob part:parts)ensureFile(c,part);
                File merged=PdfMergeUtil.mergePrepared(c,parts,job.duplex&&job.keepPartsOnSeparateSheets);
                try{
                    File restored=persistPdf(c,merged,job.id);
                    job.filePath=restored.getAbsolutePath();
                    upsert(c,job);
                    return restored;
                }finally{merged.delete();}
            }
        }

        List<String> candidates=new ArrayList<>();
        if(job.sourceUri!=null&&!job.sourceUri.isBlank())candidates.add(job.sourceUri);
        for(String uri:job.sourceUris)if(uri!=null&&!uri.isBlank()&&!candidates.contains(uri))candidates.add(uri);
        if(candidates.size()==1){
            File restored=persistUri(c,android.net.Uri.parse(candidates.get(0)),job.id);
            job.filePath=restored.getAbsolutePath();
            upsert(c,job);
            return restored;
        }
        if(candidates.size()>1){
            List<OutboxItem> items=new ArrayList<>();
            int index=0;
            for(String uri:candidates){
                OutboxItem item=new OutboxItem();
                item.uri=uri;item.name="Briefteil "+(++index);
                items.add(item);
            }
            File merged=PdfMergeUtil.merge(c,items);
            try{
                File restored=persistPdf(c,merged,job.id);
                job.filePath=restored.getAbsolutePath();
                upsert(c,job);
                return restored;
            }finally{merged.delete();}
        }
        throw new IllegalStateException("Vorbereitete PDF fehlt und die Quelldatei ist nicht mehr verfügbar.");
    }

    public static boolean hasSourceUri(Context c,String uri){
        if(uri==null||uri.isBlank())return false;
        for(PreparedJob j:load(c)){
            if(uri.equals(j.sourceUri))return true;
            for(String u:j.sourceUris)if(uri.equals(u))return true;
        }
        return false;
    }

    public static void removeMetadataOnly(Context c,String id){
        List<PreparedJob> jobs=load(c);
        jobs.removeIf(j->id.equals(j.id));
        save(c,jobs);
    }

    public static void delete(Context c,String id){
        List<PreparedJob> jobs=load(c);
        jobs.removeIf(j->{
            if(!id.equals(j.id))return false;
            try{new File(j.filePath).delete();}catch(Exception ignored){}
            for(PreparedJob part:j.mergedParts()){
                try{
                    File partFile=new File(part.filePath);
                    if(!partFile.getAbsolutePath().equals(j.filePath))partFile.delete();
                }catch(Exception ignored){}
            }
            return true;
        });
        save(c,jobs);
    }
}
