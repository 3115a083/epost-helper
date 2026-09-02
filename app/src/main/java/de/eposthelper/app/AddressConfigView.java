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
    private static final float TOP_FRACTION=0.34f;
    private float visibleFraction=TOP_FRACTION;
    private static final float SNAP_X=0.105f;
    private static final float HANDLE=0.035f;

    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap pageBitmap;
    private final RectF imageRect=new RectF();
    private final RectF sender=new RectF(0.105f,0.055f,0.535f,0.105f);
    private final RectF recipient=new RectF(0.105f,0.115f,0.535f,0.235f);

    private RectF active;
    private final RectF reserved=new RectF();
    private boolean showReserved=false;
    private String reservedLabel="Reservierter Bereich";
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
        setMinimumHeight(UiKit.dp(c,360));
        setContentDescription("Oberes Drittel des Briefes mit verschiebbaren Feldern für Absender und Empfänger.");
    }

    public void setListener(Listener l){ listener=l; }

    public void setBitmap(Bitmap bitmap){
        if(pageBitmap!=null&&pageBitmap!=bitmap&&!pageBitmap.isRecycled()) pageBitmap.recycle();
        pageBitmap=bitmap;
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

    public boolean isSnapEnabled(){ return snapEnabled; }
    public void setFullPage(boolean fullPage){visibleFraction=fullPage?1f:TOP_FRACTION;requestLayout();invalidate();}
    public boolean isFullPage(){return visibleFraction>=0.99f;}
    public void setInteractive(boolean enabled){interactive=enabled;invalidate();}

    public void setReservedArea(RectF area,String label){
        if(area==null||area.isEmpty()){reserved.setEmpty();showReserved=false;}
        else{reserved.set(area);showReserved=true;}
        reservedLabel=label==null?"Reservierter Bereich":label;
        invalidate();
    }

    public boolean hasCollision(){
        return showReserved&&(RectF.intersects(sender,reserved)||RectF.intersects(recipient,reserved));
    }

    public void setBoxes(RectF senderBox,RectF recipientBox){
        if(senderBox!=null&&!senderBox.isEmpty()) sender.set(senderBox);
        if(recipientBox!=null&&!recipientBox.isEmpty()) recipient.set(recipientBox);
        constrain(sender);
        constrain(recipient);
        invalidate();
    }

    public RectF senderBox(){ return new RectF(sender); }
    public RectF recipientBox(){ return new RectF(recipient); }

    public void clearBitmap(){
        if(pageBitmap!=null&&!pageBitmap.isRecycled()) pageBitmap.recycle();
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
            canvas.drawText(visibleFraction>=0.99f?"PDF wird geladen…":"PDF auswählen, um den Briefkopf anzuzeigen.",UiKit.dp(getContext(),18),UiKit.dp(getContext(),40),paint);
            return;
        }

        float maxW=getWidth()-UiKit.dp(getContext(),20);
        float sourceH=pageBitmap.getHeight()*visibleFraction;
        float ratio=maxW/pageBitmap.getWidth();
        float drawH=sourceH*ratio;
        float left=(getWidth()-maxW)/2f;
        float top=UiKit.dp(getContext(),10);
        imageRect.set(left,top,left+maxW,top+drawH);

        paint.setColor(0x22000000);
        canvas.drawRoundRect(new RectF(left-3,top-3,left+maxW+3,top+drawH+3),10,10,paint);

        Rect src=new Rect(0,0,pageBitmap.getWidth(),Math.max(1,Math.round(sourceH)));
        canvas.drawBitmap(pageBitmap,src,imageRect,null);

        if(snapEnabled){
            float sx=imageRect.left+SNAP_X*imageRect.width();
            paint.setColor(0x335B5BD6);
            paint.setStrokeWidth(UiKit.dp(getContext(),1));
            canvas.drawLine(sx,imageRect.top,sx,imageRect.bottom,paint);
        }

        if(showReserved){
            RectF rp=toPixels(reserved);
            boolean collision=RectF.intersects(sender,reserved)||RectF.intersects(recipient,reserved);
            int rc=collision?0xFFD32F2F:0xFFB26A00;
            paint.setStyle(Paint.Style.FILL);paint.setColor((rc&0x00FFFFFF)|0x33000000);canvas.drawRect(rp,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(UiKit.dp(getContext(),2));paint.setColor(rc);canvas.drawRect(rp,paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(UiKit.dp(getContext(),11));canvas.drawText(reservedLabel,rp.left+UiKit.dp(getContext(),5),rp.top+UiKit.dp(getContext(),14),paint);
        }

        drawBox(canvas,sender,"Absender",0xFF287A61);
        drawBox(canvas,recipient,"Empfänger",SettingsStore.primary(getContext()));
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
                imageRect.left+n.left*imageRect.width(),
                imageRect.top+(n.top/visibleFraction)*imageRect.height(),
                imageRect.left+n.right*imageRect.width(),
                imageRect.top+(n.bottom/visibleFraction)*imageRect.height());
    }

    private RectF toNormalized(RectF px){
        return new RectF(
                (px.left-imageRect.left)/imageRect.width(),
                ((px.top-imageRect.top)/imageRect.height())*visibleFraction,
                (px.right-imageRect.left)/imageRect.width(),
                ((px.bottom-imageRect.top)/imageRect.height())*visibleFraction);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(pageBitmap==null||imageRect.isEmpty()||!interactive) return true;

        if(e.getAction()==MotionEvent.ACTION_DOWN){
            RectF senderPx=toPixels(sender);
            RectF recipientPx=toPixels(recipient);
            active=senderPx.contains(e.getX(),e.getY())?sender:
                    (recipientPx.contains(e.getX(),e.getY())?recipient:null);
            if(active==null)return true;

            getParent().requestDisallowInterceptTouchEvent(true);
            RectF activePx=toPixels(active);
            resizing=!snapEnabled&&distance(e.getX(),e.getY(),activePx.right,activePx.bottom)<UiKit.dp(getContext(),28);
            downX=e.getX(); downY=e.getY();
            startLeft=active.left; startTop=active.top; startRight=active.right; startBottom=active.bottom;
            return true;
        }

        if(e.getAction()==MotionEvent.ACTION_MOVE&&active!=null){
            float dx=(e.getX()-downX)/Math.max(1f,imageRect.width());
            float dy=((e.getY()-downY)/Math.max(1f,imageRect.height()))*visibleFraction;

            if(resizing){
                active.right=Math.max(active.left+HANDLE,startRight+dx);
                active.bottom=Math.max(active.top+0.025f,startBottom+dy);
            }else{
                float w=startRight-startLeft,h=startBottom-startTop;
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
            active=null; resizing=false;
            return true;
        }
        return true;
    }

    private static float distance(float x1,float y1,float x2,float y2){
        float dx=x1-x2,dy=y1-y2;
        return (float)Math.sqrt(dx*dx+dy*dy);
    }

    private void snapBox(RectF box){
        float w=box.width();
        box.left=SNAP_X;
        box.right=SNAP_X+w;
    }

    private void constrain(RectF box){
        float w=box.width(),h=box.height();
        if(box.left<0){box.left=0;box.right=w;}
        if(box.right>1){box.right=1;box.left=1-w;}
        if(box.top<0){box.top=0;box.bottom=h;}
        if(box.bottom>TOP_FRACTION){box.bottom=TOP_FRACTION;box.top=TOP_FRACTION-h;}
    }

    private void notifyChange(){
        if(listener!=null)listener.onChanged(new RectF(sender),new RectF(recipient));
    }
}
