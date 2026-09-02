package de.eposthelper.app;

public final class JobOptions {
    public boolean duplex;
    public boolean color;
    public String registered="Nein";
    public boolean addressCorrection=false;
    public boolean c4=false;
    public String shipping="national";

    public static JobOptions fromProfile(Profile p){
        JobOptions o=new JobOptions();
        o.duplex=p.duplex;
        o.color=p.color;
        o.registered=p.registeredMail;
        o.addressCorrection=p.addressCorrection;
        return o;
    }

    public String lxpRegistered(){
        if("Einschreiben Einwurf".equals(registered))return "r1";
        if("Einschreiben".equals(registered)||"Einschreiben Übergabe".equals(registered))return "r2";
        return "";
    }
}
