package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;
import java.io.File;

public final class PreparedJobSender {
    private PreparedJobSender(){}

    public static void send(Context c,PreparedJob job) throws Exception{
        Profile p=SecureStore.find(c,job.profileId);
        if(p==null)throw new IllegalStateException("Versandprofil nicht mehr vorhanden.");
        File source=PreparedJobStore.ensureFile(c,job);
        File corrected=null;
        try{
            File outgoing=source;
            if(job.addressCorrection){
                corrected=AddressCorrectionProcessor.apply(c,source,job.sourceSender,job.sourceRecipient,job.targetSender,job.targetRecipient);
                outgoing=corrected;
            }
            if(DebugProfileManager.isDebug(p)){
                DebugSender.send(c,Uri.fromFile(outgoing),job.options(),"preparedJob="+job.id+"\nrecipientKey="+job.recipientKey);
            }else{
                ProviderSender.send(c,Uri.fromFile(outgoing),p,job.options());
            }
        }finally{if(corrected!=null)corrected.delete();}
    }
}
