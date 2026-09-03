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
            for(int i=0;i<a.length();i++){
                PreparedJob j=PreparedJob.fromJson(a.getJSONObject(i));
                if(new File(j.filePath).exists())out.add(j);
            }
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

    public static void delete(Context c,String id){
        List<PreparedJob> jobs=load(c);
        jobs.removeIf(j->{if(id.equals(j.id)){try{new File(j.filePath).delete();}catch(Exception ignored){}return true;}return false;});
        save(c,jobs);
    }
}
