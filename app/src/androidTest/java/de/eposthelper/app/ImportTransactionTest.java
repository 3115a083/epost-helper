package de.eposthelper.app;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ImportTransactionTest {
    private Context isolated(){
        return InstrumentationRegistry.getInstrumentation().getTargetContext().createDeviceProtectedStorageContext();
    }
    @Test public void failedImportRestoresAllStores() throws Exception {
        Context c=isolated();
        c.getSharedPreferences("ui_settings",0).edit().clear().putString("palette","ocean").commit();
        c.getSharedPreferences("secure_profiles",0).edit().clear().putString("profiles","old-encrypted-data").commit();
        c.getSharedPreferences("send_stats",0).edit().clear().putString("events","old-stats").commit();
        try {
            AppDataTransaction.replace(c,()->{
                c.getSharedPreferences("ui_settings",0).edit().putString("palette","rose").commit();
                c.getSharedPreferences("secure_profiles",0).edit().putString("profiles","new-data").commit();
                throw new java.io.IOException("simulated write failure");
            });
            fail("failure expected");
        }catch(java.io.IOException expected){}
        assertEquals("ocean",c.getSharedPreferences("ui_settings",0).getString("palette",""));
        assertEquals("old-encrypted-data",c.getSharedPreferences("secure_profiles",0).getString("profiles",""));
        assertEquals("old-stats",c.getSharedPreferences("send_stats",0).getString("events",""));
    }
    @Test public void interruptedImportRecoversOnNextOpen() throws Exception {
        Context c=isolated();
        c.getSharedPreferences("ui_settings",0).edit().clear().putString("palette","forest").putLong("futureLong",1L).commit();
        try {
            AppDataTransaction.replace(c,()->{
                c.getSharedPreferences("ui_settings",0).edit().clear().putString("palette","rose").commit();
                throw new AssertionError("simulated process interruption");
            });
        }catch(AssertionError expected){}
        AppDataTransaction.recover(c);
        assertEquals("forest",c.getSharedPreferences("ui_settings",0).getString("palette",""));
        assertEquals(1L,c.getSharedPreferences("ui_settings",0).getLong("futureLong",0L));
    }
    @Test public void successfulImportRemainsCommitted() throws Exception {
        Context c=isolated();
        AppDataTransaction.replace(c,()->c.getSharedPreferences("ui_settings",0).edit().putString("palette","sand").commit());
        AppDataTransaction.recover(c);
        assertEquals("sand",c.getSharedPreferences("ui_settings",0).getString("palette",""));
    }
}
