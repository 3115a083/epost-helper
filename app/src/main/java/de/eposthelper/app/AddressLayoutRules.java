package de.eposthelper.app;

import android.graphics.RectF;

public final class AddressLayoutRules {
    private AddressLayoutRules(){}

    public static RectF lxpRegisteredSender(){
        return new RectF(20f/210f,45f/297f,105f/210f,50f/297f);
    }
    public static RectF lxpRegisteredReserved(){
        return new RectF(20f/210f,50f/297f,105f/210f,67f/297f);
    }
    public static RectF lxpRegisteredRecipient(){
        return new RectF(20f/210f,67f/297f,105f/210f,90f/297f);
    }

    public static RectF normalSender(){
        return new RectF(20f/210f,27f/297f,110f/210f,42f/297f);
    }
    public static RectF normalRecipient(){
        return new RectF(20f/210f,45f/297f,110f/210f,90f/297f);
    }

    public static boolean registered(JobOptions o){
        return o!=null&&o.registered!=null&&!"Nein".equals(o.registered);
    }

    public static RectF reserved(Profile p,JobOptions o){
        if(p==null||!registered(o))return new RectF();
        if(Profile.PROVIDER_LETTERXPRESS.equals(p.provider))return lxpRegisteredReserved();
        if(Profile.PROVIDER_POST.equals(p.provider)&&p.addressCorrection)return new RectF();
        return new RectF();
    }

    public static RectF targetSender(Profile p,JobOptions o){
        if(p!=null&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&registered(o))return lxpRegisteredSender();
        return normalSender();
    }

    public static RectF targetRecipient(Profile p,JobOptions o){
        if(p!=null&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&registered(o))return lxpRegisteredRecipient();
        return normalRecipient();
    }
}
