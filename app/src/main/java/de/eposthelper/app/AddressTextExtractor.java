package de.eposthelper.app;

import android.content.Context;
import android.graphics.RectF;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripperByArea;
import java.awt.Rectangle;
import java.io.File;
import java.util.Locale;

public final class AddressTextExtractor {
    private AddressTextExtractor(){}

    public static String recipientKey(Context c,File pdf,RectF normalized){
        if(normalized==null||normalized.isEmpty())return "";
        try{
            PDFBoxResourceLoader.init(c.getApplicationContext());
            try(PDDocument doc=PDDocument.load(pdf)){
                float w=doc.getPage(0).getMediaBox().getWidth();
                float h=doc.getPage(0).getMediaBox().getHeight();
                Rectangle area=new Rectangle(
                        Math.round(normalized.left*w),Math.round(normalized.top*h),
                        Math.round(normalized.width()*w),Math.round(normalized.height()*h));
                PDFTextStripperByArea stripper=new PDFTextStripperByArea();
                stripper.addRegion("recipient",area);stripper.extractRegions(doc.getPage(0));
                String text=stripper.getTextForRegion("recipient");
                return text==null?"":text.toLowerCase(Locale.GERMANY).replaceAll("\\s+"," ").trim();
            }
        }catch(Exception ignored){return "";}
    }
}
