package de.eposthelper.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;

public final class PdfPreviewRenderer {
    private PdfPreviewRenderer(){}

    public static Bitmap renderFirstPage(Context c,Uri uri,int width,int mode) throws Exception{
        try(ParcelFileDescriptor pfd=c.getContentResolver().openFileDescriptor(uri,"r")){
            if(pfd==null)throw new IllegalStateException("PDF kann nicht geöffnet werden");
            return renderFirstPage(pfd,width,mode);
        }
    }

    public static Bitmap renderFirstPage(File file,int width,int mode) throws Exception{
        try(ParcelFileDescriptor pfd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)){
            return renderFirstPage(pfd,width,mode);
        }
    }

    private static Bitmap renderFirstPage(ParcelFileDescriptor pfd,int width,int mode) throws Exception{
        try(PdfRenderer renderer=new PdfRenderer(pfd);
            PdfRenderer.Page page=renderer.openPage(0)){
            int height=Math.max(1,Math.round(width*(page.getHeight()/(float)page.getWidth())));
            Bitmap rendered=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            page.render(rendered,null,null,mode);

            Bitmap opaque=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(opaque);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(rendered,0,0,null);
            rendered.recycle();
            return opaque;
        }
    }
}
