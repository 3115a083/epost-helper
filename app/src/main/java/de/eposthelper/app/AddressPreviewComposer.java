package de.eposthelper.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

public final class AddressPreviewComposer {
    private AddressPreviewComposer(){}

    public static Bitmap compose(Bitmap source,RectF sourceSender,RectF sourceRecipient,RectF targetSender,RectF targetRecipient){
        if(source==null||source.isRecycled())throw new IllegalArgumentException("PDF-Vorschau fehlt");
        Bitmap out=source.copy(Bitmap.Config.ARGB_8888,true);
        Canvas canvas=new Canvas(out);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);

        Bitmap sender=crop(source,sourceSender);
        Bitmap recipient=crop(source,sourceRecipient);
        try{
            paint.setColor(Color.WHITE);
            canvas.drawRect(px(source,sourceSender),paint);
            canvas.drawRect(px(source,sourceRecipient),paint);
            canvas.drawBitmap(sender,null,px(source,targetSender),paint);
            canvas.drawBitmap(recipient,null,px(source,targetRecipient),paint);
            return out;
        }finally{
            sender.recycle();
            recipient.recycle();
        }
    }

    private static Bitmap crop(Bitmap page,RectF r){
        Rect p=pxInt(page,r);
        return Bitmap.createBitmap(page,p.left,p.top,p.width(),p.height());
    }

    private static RectF px(Bitmap page,RectF r){
        return new RectF(r.left*page.getWidth(),r.top*page.getHeight(),r.right*page.getWidth(),r.bottom*page.getHeight());
    }

    private static Rect pxInt(Bitmap page,RectF r){
        int left=Math.max(0,Math.min(page.getWidth()-1,Math.round(r.left*page.getWidth())));
        int top=Math.max(0,Math.min(page.getHeight()-1,Math.round(r.top*page.getHeight())));
        int right=Math.max(left+1,Math.min(page.getWidth(),Math.round(r.right*page.getWidth())));
        int bottom=Math.max(top+1,Math.min(page.getHeight(),Math.round(r.bottom*page.getHeight())));
        return new Rect(left,top,right,bottom);
    }
}
