package de.eposthelper.app;

import android.graphics.RectF;

public final class AddressLayoutRules {
    private AddressLayoutRules(){}

    // Conservative source-selection defaults from E-POST MAILER correction profiles.
    public static RectF normalSender(){ return mm(20f,45f,85f,8f); }
    public static RectF normalRecipient(){ return mm(20f,53f,85f,24f); }

    // Deutsche Post / E-POST production geometry.
    // Admin documentation: sender starts at x=20/y=45 mm, DV zone x=20/y=52 mm,
    // 85 x 16.5 mm, recipient x=20/y=69 mm, 85 x 21 mm.
    public static RectF postWindow(){ return mm(20f,45f,85f,45f); }
    public static RectF postSender(){ return mm(20f,45f,85f,5.5f); }
    public static RectF postPostage(){ return mm(20f,52f,85f,16.5f); }
    public static RectF postRecipient(){ return mm(20f,69f,85f,21f); }

    // LetterXpress registered-mail template.
    public static RectF lxpWindow(){ return mm(20f,44f,85f,45f); }
    public static RectF lxpRegisteredSender(){ return mm(20f,44f,83f,5f); }
    public static RectF lxpRegisteredReserved(){ return mm(20f,49f,85f,17f); }
    public static RectF lxpRegisteredRecipient(){ return mm(22f,66f,83f,22f); }

    public static boolean registered(JobOptions o){
        return o!=null&&o.registered!=null&&!"Nein".equals(o.registered);
    }

    public static RectF window(Profile p,JobOptions o){
        if(p==null||DebugProfileManager.isDebug(p))return new RectF();
        if(Profile.PROVIDER_POST.equals(p.provider))return postWindow();
        if(Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&registered(o))return lxpWindow();
        return new RectF();
    }

    public static RectF postage(Profile p,JobOptions o){
        if(p==null||DebugProfileManager.isDebug(p))return new RectF();
        if(Profile.PROVIDER_POST.equals(p.provider))return postPostage();
        if(Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&registered(o))return lxpRegisteredReserved();
        return new RectF();
    }

    public static RectF reserved(Profile p,JobOptions o){ return postage(p,o); }

    public static RectF targetSender(Profile p,JobOptions o){
        if(p!=null&&Profile.PROVIDER_POST.equals(p.provider))return postSender();
        if(p!=null&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&registered(o))return lxpRegisteredSender();
        return normalSender();
    }

    public static RectF targetRecipient(Profile p,JobOptions o){
        if(p!=null&&Profile.PROVIDER_POST.equals(p.provider))return postRecipient();
        if(p!=null&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&registered(o))return lxpRegisteredRecipient();
        return normalRecipient();
    }

    // Move without resizing. This makes any clipping or overlap visible to the user.
    public static RectF moveLike(RectF source,RectF anchor){
        if(source==null||source.isEmpty())return new RectF(anchor);
        float w=source.width(),h=source.height();
        return new RectF(anchor.left,anchor.top,anchor.left+w,anchor.top+h);
    }

    public static RectF lockAbovePostage(RectF box,RectF postage){
        if(box==null||box.isEmpty()||postage==null||postage.isEmpty())return box==null?new RectF():new RectF(box);
        float h=box.height();
        return new RectF(box.left,postage.top-h,box.right,postage.top);
    }

    public static RectF lockBelowPostage(RectF box,RectF postage){
        if(box==null||box.isEmpty()||postage==null||postage.isEmpty())return box==null?new RectF():new RectF(box);
        float h=box.height();
        return new RectF(box.left,postage.bottom,box.right,postage.bottom+h);
    }

    // Portion that would lie below the provider's intended recipient zone when moved
    // without scaling. This is only a warning overlay; it does not alter the PDF.
    public static RectF recipientOverflow(RectF movedRecipient,RectF allowedRecipient){
        if(movedRecipient==null||allowedRecipient==null||movedRecipient.isEmpty()||allowedRecipient.isEmpty())return new RectF();
        float left=Math.max(movedRecipient.left,allowedRecipient.left);
        float right=Math.min(movedRecipient.right,allowedRecipient.right);
        float top=Math.max(allowedRecipient.bottom,movedRecipient.top);
        float bottom=movedRecipient.bottom;
        if(right<=left||bottom<=top)return new RectF();
        return new RectF(left,top,right,bottom);
    }

    private static RectF mm(float x,float y,float w,float h){
        return new RectF(x/210f,y/297f,(x+w)/210f,(y+h)/297f);
    }
}
