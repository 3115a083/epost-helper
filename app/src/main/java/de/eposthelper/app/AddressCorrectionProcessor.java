package de.eposthelper.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.File;

public final class AddressCorrectionProcessor {
    private AddressCorrectionProcessor(){}

    public static RectF decode(String value){
        try{
            if(value!=null&&value.startsWith("rect:")){
                String[] p=value.substring(5).split(",");
                if(p.length==4)return new RectF(Float.parseFloat(p[0]),Float.parseFloat(p[1]),Float.parseFloat(p[2]),Float.parseFloat(p[3]));
            }
        }catch(Exception ignored){}
        return new RectF();
    }

    public static boolean configured(Profile p){
        return p!=null&&!decode(p.senderWindow).isEmpty()&&!decode(p.recipientWindow).isEmpty();
    }

    public static File apply(Context c,File input,Profile p,RectF targetSender,RectF targetRecipient) throws Exception{
        RectF sourceSender=decode(p.senderWindow);
        RectF sourceRecipient=decode(p.recipientWindow);
        if(sourceSender.isEmpty()||sourceRecipient.isEmpty())throw new IllegalStateException("Adressbereiche sind im Profil noch nicht konfiguriert.");

        PDFBoxResourceLoader.init(c.getApplicationContext());
        File output=File.createTempFile("address-corrected-",".pdf",c.getCacheDir());

        Bitmap pageBitmap=null;
        try(ParcelFileDescriptor pfd=ParcelFileDescriptor.open(input,ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer=new PdfRenderer(pfd);
            PdfRenderer.Page page=renderer.openPage(0)){
            float scale=300f/72f;
            int w=Math.max(1,Math.round(page.getWidth()*scale));
            int h=Math.max(1,Math.round(page.getHeight()*scale));
            pageBitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            page.render(pageBitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
        }

        try(PDDocument doc=PDDocument.load(input)){
            PDPage page=doc.getPage(0);
            float pageW=page.getMediaBox().getWidth();
            float pageH=page.getMediaBox().getHeight();

            byte[] senderPng=cropPng(pageBitmap,sourceSender);
            byte[] recipientPng=cropPng(pageBitmap,sourceRecipient);
            PDImageXObject senderImg=PDImageXObject.createFromByteArray(doc,senderPng,"sender");
            PDImageXObject recipientImg=PDImageXObject.createFromByteArray(doc,recipientPng,"recipient");

            try(PDPageContentStream cs=new PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true)){
                whiteOut(cs,pageW,pageH,sourceSender);
                whiteOut(cs,pageW,pageH,sourceRecipient);
                draw(cs,senderImg,pageW,pageH,targetSender);
                draw(cs,recipientImg,pageW,pageH,targetRecipient);
            }
            doc.save(output);
        }catch(Exception e){
            output.delete();
            throw e;
        }finally{
            if(pageBitmap!=null&&!pageBitmap.isRecycled())pageBitmap.recycle();
        }
        return output;
    }

    private static byte[] cropPng(Bitmap page,RectF r) throws Exception{
        int left=Math.max(0,Math.min(page.getWidth()-1,Math.round(r.left*page.getWidth())));
        int top=Math.max(0,Math.min(page.getHeight()-1,Math.round(r.top*page.getHeight())));
        int right=Math.max(left+1,Math.min(page.getWidth(),Math.round(r.right*page.getWidth())));
        int bottom=Math.max(top+1,Math.min(page.getHeight(),Math.round(r.bottom*page.getHeight())));
        Bitmap crop=Bitmap.createBitmap(page,left,top,right-left,bottom-top);
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            crop.compress(Bitmap.CompressFormat.PNG,100,out);
            return out.toByteArray();
        }finally{crop.recycle();}
    }

    private static void whiteOut(PDPageContentStream cs,float w,float h,RectF r) throws Exception{
        float x=r.left*w;
        float y=h-r.bottom*h;
        float rw=r.width()*w;
        float rh=r.height()*h;
        cs.setNonStrokingColor(255,255,255);
        cs.addRect(x,y,rw,rh);
        cs.fill();
    }

    private static void draw(PDPageContentStream cs,PDImageXObject image,float w,float h,RectF r) throws Exception{
        float x=r.left*w;
        float y=h-r.bottom*h;
        cs.drawImage(image,x,y,r.width()*w,r.height()*h);
    }
}
