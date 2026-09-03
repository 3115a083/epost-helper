package de.eposthelper.app;

import android.graphics.RectF;

public final class AddressLayoutRules {
    private AddressLayoutRules(){}

    // Conservative cross-provider defaults based on the E-POST MAILER limits:
    // sender <= 85 x 8 mm, recipient <= 85 x 24 mm.
    // Both fit comfortably inside a standard window and avoid unnecessary scaling.
    public static RectF normalSender(){
        return mm(20f,45f,85f,8f);
    }

    public static RectF normalRecipient(){
        return mm(20f,53f,85f,24f);
    }

    // Deutsche Post describes a typical 90 x 45 mm window and at least 85 x 30 mm
    // when only the recipient address is present. The compact default keeps a 5 mm
    // sender line plus a 30 mm recipient block.
    public static RectF postWindow(){
        return mm(20f,45f,90f,45f);
    }

    public static RectF postSenderTarget(){
        return mm(23f,46.5f,85f,5.5f);
    }

    public static RectF postReserved(){
        return mm(23f,52f,85f,16f);
    }

    public static RectF postRecipientTarget(){
        return mm(23f,68f,85f,21f);
    }

    public static RectF postRecipientCompact(){
        return postRecipientTarget();
    }

    public static RectF lxpWindow(){
        return mm(20f,44f,85f,44f);
    }

    // LetterXpress official registered-mail template:
    // sender y=44..49 mm, DV franking y=49..66 mm, recipient y=66..88 mm.
    public static RectF lxpRegisteredSender(){
        return mm(20f,44f,83f,5f);
    }

    public static RectF lxpRegisteredReserved(){
        return mm(20f,49f,85f,17f);
    }

    public static RectF lxpRegisteredRecipient(){
        return mm(22f,66f,83f,22f);
    }

    // Visual safety extension: the conservative 24 mm recipient default is 2 mm
    // taller than the official 22 mm LetterXpress registered-mail recipient zone.
    // This strip warns that the lowest address content would otherwise be clipped.
    public static RectF lxpRegisteredRecipientSafetyExtension(){
        return mm(22f,88f,83f,2f);
    }

    public static boolean registered(JobOptions o){
        return o!=null&&o.registered!=null&&!"Nein".equals(o.registered);
    }

    public static RectF reserved(Profile p,JobOptions o){
        if(p==null||!registered(o))return new RectF();
        if(Profile.PROVIDER_LETTERXPRESS.equals(p.provider))return lxpRegisteredReserved();
        if(Profile.PROVIDER_POST.equals(p.provider))return postReserved();
        return new RectF();
    }

    public static RectF recipientSafety(Profile p,JobOptions o){
        if(p!=null&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&registered(o))
            return lxpRegisteredRecipientSafetyExtension();
        return new RectF();
    }

    public static RectF targetSender(Profile p,JobOptions o){
        if(p!=null&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&registered(o))
            return lxpRegisteredSender();
        if(p!=null&&Profile.PROVIDER_POST.equals(p.provider)&&registered(o))
            return postSenderTarget();
        return normalSender();
    }

    public static RectF targetRecipient(Profile p,JobOptions o){
        if(p!=null&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&registered(o))
            return lxpRegisteredRecipient();
        if(p!=null&&Profile.PROVIDER_POST.equals(p.provider)&&registered(o))
            return postRecipientTarget();
        return normalRecipient();
    }

    public static RectF window(Profile p,JobOptions o){
        if(p==null)return new RectF();
        if(Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&registered(o))return lxpWindow();
        if(Profile.PROVIDER_POST.equals(p.provider)&&registered(o))return postWindow();
        return new RectF();
    }

    private static RectF mm(float x,float y,float w,float h){
        return new RectF(x/210f,y/297f,(x+w)/210f,(y+h)/297f);
    }
}
