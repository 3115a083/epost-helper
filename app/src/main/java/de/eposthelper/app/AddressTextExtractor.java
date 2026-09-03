package de.eposthelper.app;

import android.content.Context;
import android.graphics.RectF;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripperByArea;
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
                android.graphics.RectF area=new android.graphics.RectF(
                        normalized.left*w,normalized.top*h,
                        normalized.right*w,normalized.bottom*h);
                PDFTextStripperByArea stripper=new PDFTextStripperByArea();
                stripper.addRegion("recipient",area);stripper.extractRegions(doc.getPage(0));
                String text=stripper.getTextForRegion("recipient");
                return text==null?"":text.toLowerCase(Locale.GERMANY).replaceAll("\\s+"," ").trim();
            }
        }catch(Exception ignored){return "";}
    }
}
