package de.eposthelper.app;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

public class BackupActivity extends AppCompatActivity {
    private EditText password;
    private char[] pendingPassword;

    private final ActivityResultLauncher<String> createBackup=registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"),uri->{
                if(uri==null||pendingPassword==null)return;
                runExport(uri,pendingPassword);
                pendingPassword=null;
            });

    private final ActivityResultLauncher<String[]> openBackup=registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),uri->{
                if(uri==null||pendingPassword==null)return;
                runImport(uri,pendingPassword);
                pendingPassword=null;
            });

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        SettingsStore.applyDynamicColors(this);
        render();
    }

    private void render(){
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(UiKit.resolveSurface(this));

        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,8));
        TextView back=UiKit.heading(this,"‹",34);back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->getOnBackPressedDispatcher().onBackPressed());
        bar.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,54)));
        bar.addView(UiKit.heading(this,"Backup & Gerätewechsel",22),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(bar);

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this,18),UiKit.dp(this,4),UiKit.dp(this,18),UiKit.dp(this,24));

        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        info.addView(UiKit.heading(this,"Passwortgeschütztes Backup",18));
        TextView explain=UiKit.body(this,"Exportiert Einstellungen, Profile inklusive Zugangsdaten sowie lokale Versandstatistiken in eine AES-256-GCM-verschlüsselte Datei. Das Passwort wird nicht gespeichert und kann nicht wiederhergestellt werden.");
        explain.setTextSize(13);explain.setPadding(0,UiKit.dp(this,6),0,UiKit.dp(this,10));info.addView(explain);

        password=new EditText(this);password.setHint("Backup-Passwort, mindestens 6 Zeichen");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        info.addView(password,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56)));

        MaterialButton export=UiKit.primary(this,"Backup exportieren");
        export.setOnClickListener(v->{
            char[] pw=passwordChars();
            if(pw==null)return;
            pendingPassword=pw;
            createBackup.launch("epost-helper-"+BuildConfig.VERSION_NAME+".epostbackup");
        });
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52));ep.setMargins(0,UiKit.dp(this,10),0,0);info.addView(export,ep);

        MaterialButton restore=UiKit.tonal(this,"Backup importieren");
        restore.setOnClickListener(v->{
            char[] pw=passwordChars();
            if(pw==null)return;
            pendingPassword=pw;
            openBackup.launch(new String[]{"application/octet-stream","application/*"});
        });
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52));rp.setMargins(0,UiKit.dp(this,8),0,0);info.addView(restore,rp);
        root.addView(UiKit.surfaceCard(this,info));

        LinearLayout transfer=new LinearLayout(this);transfer.setOrientation(LinearLayout.VERTICAL);
        transfer.addView(UiKit.heading(this,"Android-/Samsung-Geräteübertragung",18));
        TextView t=UiKit.body(this,"Android Backup und Geräteübertragung dürfen eine portable Kopie von Einstellungen, Profilkonfiguration und Statistiken sichern. API-Keys werden dabei ausdrücklich entfernt. Zugangsdaten, die kein API-Key sind, werden nur über die verschlüsselte Plattform-Sicherung übertragen. Vorbereitete PDFs werden nicht synchronisiert. Der Importordner muss auf einem neuen Gerät erneut ausgewählt werden, weil Android SAF-Ordnerrechte nicht zuverlässig übertragbar sind.");
        t.setTextSize(13);t.setPadding(0,UiKit.dp(this,6),0,0);transfer.addView(t);
        root.addView(UiKit.surfaceCard(this,transfer));

        page.addView(root,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(page);SystemUi.apply(this,page);
    }

    private char[] passwordChars(){
        String p=password.getText()==null?"":password.getText().toString();
        if(p.length()<6){password.setError("Mindestens 6 Zeichen");return null;}
        return p.toCharArray();
    }

    private void runExport(Uri uri,char[] pw){
        new Thread(()->{
            try{
                AppBackupManager.exportTo(this,uri,pw);
                java.util.Arrays.fill(pw,'\0');
                runOnUiThread(()->Snackbar.make(password,"Backup gespeichert.",Snackbar.LENGTH_LONG).show());
            }catch(Exception e){
                java.util.Arrays.fill(pw,'\0');
                runOnUiThread(()->DebugUtil.error(this,password,"Backup exportieren",e));
            }
        },"backup-export").start();
    }

    private void runImport(Uri uri,char[] pw){
        new Thread(()->{
            try{
                AppBackupManager.importFrom(this,uri,pw);
                java.util.Arrays.fill(pw,'\0');
                runOnUiThread(()->{
                    Snackbar.make(password,"Backup importiert. Die App wird neu geladen.",Snackbar.LENGTH_LONG).show();
                    recreate();
                });
            }catch(Exception e){
                java.util.Arrays.fill(pw,'\0');
                runOnUiThread(()->DebugUtil.error(this,password,"Backup importieren",e));
            }
        },"backup-import").start();
    }
}
