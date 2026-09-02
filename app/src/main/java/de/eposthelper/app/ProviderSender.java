package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;

public final class ProviderSender {
    private ProviderSender(){}

    public static void send(Context c,Uri pdf,Profile p,JobOptions o) throws Exception{
        if(Profile.PROVIDER_LETTERXPRESS.equals(p.provider)){
            if(Profile.TYPE_LXP_API.equals(p.type))LetterXpressApiClient.send(c,pdf,p,o);
            else if(Profile.TYPE_LXP_SFTP.equals(p.type))LetterXpressSftpClient.send(c,pdf,p,o);
            else throw new IllegalArgumentException("Unbekannter LetterXpress-Übertragungsweg");
        }else{
            Profile copy=Profile.fromJson(p.toJson());
            copy.duplex=o.duplex;copy.color=o.color;copy.registeredMail=o.registered;
            Sender.send(c,pdf,copy);
        }
    }
}
