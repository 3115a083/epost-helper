package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import com.tom_roush.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public final class PdfMergeUtil {
    private PdfMergeUtil(){}

    public static File merge(Context c,List<OutboxItem> items) throws Exception{
        if(items.isEmpty())throw new IllegalArgumentException("Keine PDF ausgewählt");
        PDFBoxResourceLoader.init(c.getApplicationContext());
        File out=File.createTempFile("letter-outbox-",".pdf",c.getCacheDir());
        PDFMergerUtility merger=new PDFMergerUtility();
        merger.setDestinationFileName(out.getAbsolutePath());
        java.util.ArrayList<InputStream> opened=new java.util.ArrayList<>();
        try{
            for(OutboxItem item:items){
                InputStream in=c.getContentResolver().openInputStream(item.asUri());
                if(in==null)throw new IllegalStateException("PDF kann nicht geöffnet werden: "+item.name);
                opened.add(in);merger.addSource(in);
            }
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
            return out;
        }catch(Exception e){
            out.delete();throw e;
        }finally{
            for(InputStream in:opened)try{in.close();}catch(Exception ignored){}
        }
    }

    public static int countPages(Context c,Uri uri){
        try(ParcelFileDescriptor pfd=c.getContentResolver().openFileDescriptor(uri,"r")){
            if(pfd==null)return 0;
            try(android.graphics.pdf.PdfRenderer r=new android.graphics.pdf.PdfRenderer(pfd)){return r.getPageCount();}
        }catch(Exception e){return 0;}
    }

    public static int countPages(File file){
        try(PDDocument d=PDDocument.load(file)){return d.getNumberOfPages();}
        catch(Exception e){return 0;}
    }
}
