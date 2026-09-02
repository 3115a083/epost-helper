package de.eposthelper.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public final class AddressConfigView extends View {
    private static final float SNAP_X=0.105f;
    private static final float HANDLE=0.035f;

    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap pageBitmap;
    private final RectF imageRect=new RectF();
    private final RectF viewport=new RectF(0f,0f,1f,0.34f);

    private final RectF sender=AddressLayoutRules.normalSender();
    private final RectF recipient=AddressLayoutRules.normalRecipient();
    private final RectF reserved=new RectF();
    private final RectF recipientSafety=new RectF();

    private RectF active;
    private boolean showReserved=false;
    private boolean showRecipientSafety=false;
    private String reservedLabel="Reservierter Bereich";
    private String recipientSafetyLabel="Adress-Sicherheitsbereich";
    private boolean resizing=false;
    private boolean snapEnabled=true;
    private boolean interactive=true;

    private float downX,downY;
    private float startLeft,startTop,startRight,startBottom;
    private Listener listener;

    public interface Listener{
        void onChanged(RectF sender,RectF recipient);
    }

    public AddressConfigView(Context c){
        super(c);
        setMinimumHeight(UiKit.dp(c,240));
        setContentDescription("PDF-Ausschnitt mit verschiebbaren Bereichen für Absender und Empfänger.");
    }

    public void setListener(Listener l){listener=l;}

    public void setBitmap(Bitmap bitmap){
        if(pageBitmap!=null&&pageBitmap!=bitmap&&!pageBitmap.isRecycled())pageBitmap.recycle();
        pageBitmap=bitmap;
        invalidate();
    }

    public void setViewport(RectF pageViewport){
        if(pageViewport==null||pageViewport.isEmpty())return;
        float l=clamp(pageViewport.left,0f,0.99f);
        float t=clamp(pageViewport.top,0f,0.99f);
        float r=clamp(pageViewport.right,l+0.01f,1f);
        float b=clamp(pageViewport.bottom,t+0.01f,1f);
        viewport.set(l,t,r,b);
        constrain(sender);
        constrain(recipient);
        requestLayout();
        invalidate();
    }

    public RectF viewport(){return new RectF(viewport);}

    public void setFullPage(boolean fullPage){
        setViewport(fullPage?new RectF(0f,0f,1f,1f):new RectF(0f,0f,1f,0.34f));
    }

    public boolean isFullPage(){
        return viewport.left<=0.001f&&viewport.top<=0.001f&&viewport.right>=0.999f&&viewport.bottom>=0.999f;
    }

    public void setInteractive(boolean enabled){
        interactive=enabled;
        invalidate();
    }

    public void setSnapEnabled(boolean enabled){
        snapEnabled=enabled;
        if(enabled){
            snapBox(sender);
            snapBox(recipient);
            notifyChange();
        }
        invalidate();
    }

    public boolean isSnapEnabled(){return snapEnabled;}

    public void setReservedArea(RectF area,String label){
        if(area==null||area.isEmpty()){
            reserved.setEmpty();
            showReserved=false;
        }else{
            reserved.set(area);
            showReserved=true;
        }
        reservedLabel=label==null?"Reservierter Bereich":label;
        invalidate();
    }

    public void setRecipientSafetyArea(RectF area,String label){
        if(area==null||area.isEmpty()){
            recipientSafety.setEmpty();
            showRecipientSafety=false;
        }else{
            recipientSafety.set(area);
            showRecipientSafety=true;
        }
        recipientSafetyLabel=label==null?"Adress-Sicherheitsbereich":label;
        invalidate();
    }

    public boolean hasCollision(){
        return showReserved&&(RectF.intersects(sender,reserved)||RectF.intersects(recipient,reserved));
    }

    public void setBoxes(RectF senderBox,RectF recipientBox){
        if(senderBox!=null&&!senderBox.isEmpty())sender.set(senderBox);
        if(recipientBox!=null&&!recipientBox.isEmpty())recipient.set(recipientBox);
        constrain(sender);
        constrain(recipient);
        invalidate();
    }

    public RectF senderBox(){return new RectF(sender);}
    public RectF recipientBox(){return new RectF(recipient);}

    public void clearBitmap(){
        if(pageBitmap!=null&&!pageBitmap.isRecycled())pageBitmap.recycle();
        pageBitmap=null;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(UiKit.resolveSurface(getContext()));
        canvas.drawRect(0,0,getWidth(),getHeight(),paint);

        if(pageBitmap==null){
            paint.setColor(UiKit.resolveSecondaryText(getContext()));
            paint.setTextSize(UiKit.dp(getContext(),15));
            canvas.drawText("PDF wird geladen…",UiKit.dp(getContext(),18),UiKit.dp(getContext(),40),paint);
            return;
        }

        int srcLeft=Math.max(0,Math.min(pageBitmap.getWidth()-1,Math.round(viewport.left*pageBitmap.getWidth())));
        int srcTop=Math.max(0,Math.min(pageBitmap.getHeight()-1,Math.round(viewport.top*pageBitmap.getHeight())));
        int srcRight=Math.max(srcLeft+1,Math.min(pageBitmap.getWidth(),Math.round(viewport.right*pageBitmap.getWidth())));
        int srcBottom=Math.max(srcTop+1,Math.min(pageBitmap.getHeight(),Math.round(viewport.bottom*pageBitmap.getHeight())));
        Rect src=new Rect(srcLeft,srcTop,srcRight,srcBottom);

        float maxW=Math.max(1,getWidth()-UiKit.dp(getContext(),20));
        float sourceRatio=src.height()/(float)src.width();
        float drawH=maxW*sourceRatio;
        float maxH=Math.max(1,getHeight()-UiKit.dp(getContext(),20));
        if(drawH>maxH){
            drawH=maxH;
            maxW=drawH/sourceRatio;
        }

        float left=(getWidth()-maxW)/2f;
        float top=(getHeight()-drawH)/2f;
        imageRect.set(left,top,left+maxW,top+drawH);

        paint.setColor(0x22000000);
        canvas.drawRoundRect(new RectF(left-3,top-3,left+maxW+3,top+drawH+3),10,10,paint);
        canvas.drawBitmap(pageBitmap,src,imageRect,null);

        if(snapEnabled&&SNAP_X>=viewport.left&&SNAP_X<=viewport.right){
            float sx=imageRect.left+((SNAP_X-viewport.left)/viewport.width())*imageRect.width();
            paint.setColor(0x335B5BD6);
            paint.setStrokeWidth(UiKit.dp(getContext(),1));
            canvas.drawLine(sx,imageRect.top,sx,imageRect.bottom,paint);
        }

        if(showRecipientSafety&&RectF.intersects(viewport,recipientSafety)){
            RectF sp=toPixels(recipientSafety);
            int sc=0xFF6A5ACD;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((sc&0x00FFFFFF)|0x26000000);
            canvas.drawRect(sp,paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(UiKit.dp(getContext(),2));
            paint.setColor(sc);
            canvas.drawRect(sp,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(UiKit.dp(getContext(),10));
            canvas.drawText(recipientSafetyLabel,sp.left+UiKit.dp(getContext(),5),sp.top+UiKit.dp(getContext(),13),paint);
        }

        if(showReserved&&RectF.intersects(viewport,reserved)){
            RectF rp=toPixels(reserved);
            boolean collision=RectF.intersects(sender,reserved)||RectF.intersects(recipient,reserved);
            int rc=collision?0xFFD32F2F:0xFFB26A00;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((rc&0x00FFFFFF)|0x33000000);
            canvas.drawRect(rp,paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(UiKit.dp(getContext(),2));
            paint.setColor(rc);
            canvas.drawRect(rp,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(UiKit.dp(getContext(),11));
            canvas.drawText(reservedLabel,rp.left+UiKit.dp(getContext(),5),rp.top+UiKit.dp(getContext(),14),paint);
        }

        if(RectF.intersects(viewport,sender))drawBox(canvas,sender,"Absender",0xFF287A61);
        if(RectF.intersects(viewport,recipient))drawBox(canvas,recipient,"Empfänger",SettingsStore.primary(getContext()));
    }

    private void drawBox(Canvas canvas,RectF normalized,String label,int color){
        RectF px=toPixels(normalized);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor((color&0x00FFFFFF)|0x26000000);
        canvas.drawRoundRect(px,8,8,paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(UiKit.dp(getContext(),2));
        paint.setColor(color);
        canvas.drawRoundRect(px,8,8,paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(UiKit.dp(getContext(),12));
        canvas.drawText(label,px.left+UiKit.dp(getContext(),6),px.top+UiKit.dp(getContext(),16),paint);

        if(!snapEnabled){
            float h=UiKit.dp(getContext(),12);
            canvas.drawCircle(px.right-h,px.bottom-h,h/2f,paint);
        }
    }

    private RectF toPixels(RectF n){
        return new RectF(
                imageRect.left+((n.left-viewport.left)/viewport.width())*imageRect.width(),
                imageRect.top+((n.top-viewport.top)/viewport.height())*imageRect.height(),
                imageRect.left+((n.right-viewport.left)/viewport.width())*imageRect.width(),
                imageRect.top+((n.bottom-viewport.top)/viewport.height())*imageRect.height()
        );
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(pageBitmap==null||imageRect.isEmpty()||!interactive)return true;

        if(e.getAction()==MotionEvent.ACTION_DOWN){
            RectF senderPx=toPixels(sender);
            RectF recipientPx=toPixels(recipient);
            active=senderPx.contains(e.getX(),e.getY())?sender:
                    (recipientPx.contains(e.getX(),e.getY())?recipient:null);
            if(active==null)return true;

            getParent().requestDisallowInterceptTouchEvent(true);
            RectF activePx=toPixels(active);
            resizing=!snapEnabled&&distance(e.getX(),e.getY(),activePx.right,activePx.bottom)<UiKit.dp(getContext(),28);
            downX=e.getX();
            downY=e.getY();
            startLeft=active.left;
            startTop=active.top;
            startRight=active.right;
            startBottom=active.bottom;
            return true;
        }

        if(e.getAction()==MotionEvent.ACTION_MOVE&&active!=null){
            float dx=(e.getX()-downX)/Math.max(1f,imageRect.width())*viewport.width();
            float dy=(e.getY()-downY)/Math.max(1f,imageRect.height())*viewport.height();

            if(resizing){
                active.right=Math.max(active.left+HANDLE,startRight+dx);
                active.bottom=Math.max(active.top+0.025f,startBottom+dy);
            }else{
                float w=startRight-startLeft;
                float h=startBottom-startTop;
                active.left=startLeft+dx;
                active.top=startTop+dy;
                active.right=active.left+w;
                active.bottom=active.top+h;
                if(snapEnabled)snapBox(active);
            }

            constrain(active);
            invalidate();
            notifyChange();
            return true;
        }

        if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){
            getParent().requestDisallowInterceptTouchEvent(false);
            active=null;
            resizing=false;
            return true;
        }

        return true;
    }

    private void snapBox(RectF box){
        float w=box.width();
        float x=clamp(SNAP_X,viewport.left,viewport.right-w);
        box.left=x;
        box.right=x+w;
    }

    private void constrain(RectF box){
        float w=Math.min(box.width(),viewport.width());
        float h=Math.min(box.height(),viewport.height());

        if(box.left<viewport.left){
            box.left=viewport.left;
            box.right=box.left+w;
        }
        if(box.right>viewport.right){
            box.right=viewport.right;
            box.left=box.right-w;
        }
        if(box.top<viewport.top){
            box.top=viewport.top;
            box.bottom=box.top+h;
        }
        if(box.bottom>viewport.bottom){
            box.bottom=viewport.bottom;
            box.top=box.bottom-h;
        }
    }

    private void notifyChange(){
        if(listener!=null)listener.onChanged(new RectF(sender),new RectF(recipient));
    }

    private static float distance(float x1,float y1,float x2,float y2){
        float dx=x1-x2,dy=y1-y2;
        return (float)Math.sqrt(dx*dx+dy*dy);
    }

    private static float clamp(float v,float min,float max){
        return Math.max(min,Math.min(max,v));
    }
}
