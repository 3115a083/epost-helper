package de.eposthelper.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public final class AddressConfigView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF recipient=new RectF(), sender=new RectF();
    private RectF active;
    private float lastX,lastY,scale=1f,pageLeft=0,pageTop=0;
    private Listener listener;
    public interface Listener{ void onChanged(float rx,float ry,float sx,float sy); }

    public AddressConfigView(Context c){ super(c); setMinimumHeight(UiKit.dp(c,500)); }
    public void setListener(Listener l){ listener=l; }
    public void setPositions(float rx,float ry,float sx,float sy){ post(()->{layoutBoxes(rx,ry,sx,sy);invalidate();}); }

    private void layoutBoxes(float rx,float ry,float sx,float sy){
        float pageW=getWidth()-UiKit.dp(getContext(),36); if(pageW<=0)return;
        scale=pageW/210f; pageLeft=UiKit.dp(getContext(),18); pageTop=UiKit.dp(getContext(),18);
        recipient.set(pageLeft+rx*scale,pageTop+ry*scale,pageLeft+(rx+90)*scale,pageTop+(ry+45)*scale);
        sender.set(pageLeft+sx*scale,pageTop+sy*scale,pageLeft+(sx+90)*scale,pageTop+(sy+18)*scale);
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float pageW=getWidth()-UiKit.dp(getContext(),36), pageH=297f*(pageW/210f);
        scale=pageW/210f; pageLeft=UiKit.dp(getContext(),18); pageTop=UiKit.dp(getContext(),18);

        paint.setStyle(Paint.Style.FILL); paint.setColor(0xFFFFFFFF);
        c.drawRoundRect(new RectF(pageLeft,pageTop,pageLeft+pageW,pageTop+pageH),12,12,paint);

        paint.setColor(0x18D32F2F);
        c.drawRect(pageLeft,pageTop,pageLeft+12*scale,pageTop+pageH,paint);
        c.drawRect(pageLeft+pageW-12*scale,pageTop,pageLeft+pageW,pageTop+pageH,paint);

        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(UiKit.dp(getContext(),2)); paint.setColor(0xFF5C6BC0);
        c.drawRoundRect(new RectF(pageLeft+20*scale,pageTop+45*scale,pageLeft+110*scale,pageTop+90*scale),8,8,paint);

        if(recipient.isEmpty()) layoutBoxes(20,45,20,27);

        paint.setStyle(Paint.Style.FILL); paint.setColor(0x225B5BD6); c.drawRoundRect(recipient,8,8,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(0xFF5B5BD6); c.drawRoundRect(recipient,8,8,paint);
        paint.setStyle(Paint.Style.FILL); paint.setTextSize(UiKit.dp(getContext(),12)); paint.setColor(0xFF5B5BD6);
        c.drawText("Empfängeradresse",recipient.left+8,recipient.top+UiKit.dp(getContext(),18),paint);

        paint.setColor(0x2228A745); c.drawRoundRect(sender,8,8,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(0xFF287A61); c.drawRoundRect(sender,8,8,paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xFF287A61);
        c.drawText("Absender",sender.left+8,sender.top+UiKit.dp(getContext(),15),paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        float x=e.getX(),y=e.getY();
        if(e.getAction()==MotionEvent.ACTION_DOWN){
            active=recipient.contains(x,y)?recipient:(sender.contains(x,y)?sender:null);
            lastX=x;lastY=y;return true;
        }
        if(e.getAction()==MotionEvent.ACTION_MOVE&&active!=null){
            active.offset(x-lastX,y-lastY); clamp(active); lastX=x;lastY=y; invalidate(); notifyChange(); return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){active=null;return true;}
        return true;
    }

    private void clamp(RectF r){
        float pw=210*scale,ph=297*scale;
        if(r.left<pageLeft)r.offset(pageLeft-r.left,0);
        if(r.top<pageTop)r.offset(0,pageTop-r.top);
        if(r.right>pageLeft+pw)r.offset(pageLeft+pw-r.right,0);
        if(r.bottom>pageTop+ph)r.offset(0,pageTop+ph-r.bottom);
    }
    private void notifyChange(){
        if(listener!=null) listener.onChanged((recipient.left-pageLeft)/scale,(recipient.top-pageTop)/scale,(sender.left-pageLeft)/scale,(sender.top-pageTop)/scale);
    }
}
