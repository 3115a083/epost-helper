package de.eposthelper.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public final class AddressConfigView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap pageBitmap;
    private final RectF imageRect=new RectF();
    private final RectF selection=new RectF();
    private float downX,downY;
    private Listener listener;

    public interface Listener{ void onSelection(float left,float top,float right,float bottom); }

    public AddressConfigView(Context c){
        super(c);
        setMinimumHeight(UiKit.dp(c,520));
        setContentDescription("PDF-Vorschau. Ziehen Sie über den Adressbereich.");
    }

    public void setListener(Listener listener){ this.listener=listener; }

    public void setBitmap(Bitmap bitmap){
        if(pageBitmap!=null&&pageBitmap!=bitmap&&!pageBitmap.isRecycled()) pageBitmap.recycle();
        pageBitmap=bitmap;
        selection.setEmpty();
        invalidate();
    }

    public void clearBitmap(){
        if(pageBitmap!=null&&!pageBitmap.isRecycled()) pageBitmap.recycle();
        pageBitmap=null;
        selection.setEmpty();
        invalidate();
    }

    public RectF normalizedSelection(){
        if(imageRect.isEmpty()||selection.isEmpty()) return new RectF();
        return new RectF(
                (selection.left-imageRect.left)/imageRect.width(),
                (selection.top-imageRect.top)/imageRect.height(),
                (selection.right-imageRect.left)/imageRect.width(),
                (selection.bottom-imageRect.top)/imageRect.height());
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(UiKit.resolveSurface(getContext()));
        canvas.drawRect(0,0,getWidth(),getHeight(),paint);

        if(pageBitmap==null){
            paint.setColor(UiKit.resolveSecondaryText(getContext()));
            paint.setTextSize(UiKit.dp(getContext(),15));
            canvas.drawText("PDF auswählen, um die erste Seite anzuzeigen.",UiKit.dp(getContext(),18),UiKit.dp(getContext(),40),paint);
            return;
        }

        float maxW=getWidth()-UiKit.dp(getContext(),24);
        float maxH=getHeight()-UiKit.dp(getContext(),24);
        float scale=Math.min(maxW/pageBitmap.getWidth(),maxH/pageBitmap.getHeight());
        float w=pageBitmap.getWidth()*scale, h=pageBitmap.getHeight()*scale;
        float left=(getWidth()-w)/2f, top=UiKit.dp(getContext(),12);
        imageRect.set(left,top,left+w,top+h);

        paint.setColor(0x22000000);
        canvas.drawRoundRect(new RectF(left-4,top-4,left+w+4,top+h+4),10,10,paint);
        canvas.drawBitmap(pageBitmap,null,imageRect,null);

        if(!selection.isEmpty()){
            paint.setStyle(Paint.Style.FILL); paint.setColor(0x335B5BD6);
            canvas.drawRect(selection,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(UiKit.dp(getContext(),2)); paint.setColor(SettingsStore.primary(getContext()));
            canvas.drawRect(selection,paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(UiKit.dp(getContext(),12)); paint.setColor(SettingsStore.primary(getContext()));
            canvas.drawText("Adressbereich",selection.left+UiKit.dp(getContext(),6),Math.max(selection.top-UiKit.dp(getContext(),6),top+UiKit.dp(getContext(),14)),paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event){
        if(pageBitmap==null||imageRect.isEmpty()) return true;
        float x=Math.max(imageRect.left,Math.min(event.getX(),imageRect.right));
        float y=Math.max(imageRect.top,Math.min(event.getY(),imageRect.bottom));

        if(event.getAction()==MotionEvent.ACTION_DOWN){
            if(!imageRect.contains(event.getX(),event.getY())) return true;
            downX=x; downY=y; selection.set(x,y,x,y); invalidate(); return true;
        }
        if(event.getAction()==MotionEvent.ACTION_MOVE){
            selection.set(Math.min(downX,x),Math.min(downY,y),Math.max(downX,x),Math.max(downY,y));
            invalidate(); return true;
        }
        if(event.getAction()==MotionEvent.ACTION_UP){
            selection.set(Math.min(downX,x),Math.min(downY,y),Math.max(downX,x),Math.max(downY,y));
            invalidate();
            RectF n=normalizedSelection();
            if(listener!=null&&!n.isEmpty()) listener.onSelection(n.left,n.top,n.right,n.bottom);
            return true;
        }
        return true;
    }
}
