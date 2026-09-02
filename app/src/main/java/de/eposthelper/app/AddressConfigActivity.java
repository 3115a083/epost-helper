package de.eposthelper.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddressConfigActivity extends AppCompatActivity {
    private Profile profile;
    private AddressConfigView preview;
    private TextView values;
    private float rx=20,ry=45,sx=20,sy=27;

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        String id=getIntent().getStringExtra("profileId");
        profile=id==null?null:SecureStore.find(this,id);
        parseStored();
        render();
    }

    private void parseStored(){
        if(profile==null)return;
        try{
            String[] r=profile.recipientWindow.split(",");
            if(r.length==2){rx=Float.parseFloat(r[0]);ry=Float.parseFloat(r[1]);}
            String[] s=profile.senderWindow.split(",");
            if(s.length==2){sx=Float.parseFloat(s[0]);sy=Float.parseFloat(s[1]);}
        }catch(Exception ignored){}
    }

    private void render(){
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(UiKit.resolveSurface(this));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,8));
        TextView back=UiKit.heading(this,"‹",34);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->finish());
        back.setContentDescription("Zurück");
        top.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,54)));
        top.addView(UiKit.heading(this,"Versandfeld-Assistent",22),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(top);

        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this,18),0,UiKit.dp(this,18),UiKit.dp(this,28));
        scroll.addView(root);

        int[] g=SettingsStore.gradient(this);
        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.addView(UiKit.heroTitle(this,"Adresse neu positionieren",22));
        hero.addView(UiKit.heroBody(this,"Ziehe Empfänger- und Absenderbereich in die passende Position. Sichtfenster und Sperrflächen bleiben sichtbar."));
        root.addView(UiKit.hero(this,hero,g[0],g[1]));

        preview=new AddressConfigView(this);
        preview.setListener((a,b,c,d)->{rx=a;ry=b;sx=c;sy=d;updateValues();});
        preview.setPositions(rx,ry,sx,sy);
        root.addView(UiKit.surfaceCard(this,preview));

        LinearLayout valuesBox=new LinearLayout(this);
        valuesBox.setOrientation(LinearLayout.VERTICAL);
        valuesBox.addView(UiKit.heading(this,"Korrekturvorlage",17));
        values=UiKit.mono(this,"");
        updateValues();
        valuesBox.addView(values);
        TextView note=UiKit.body(this,"Die Vorlage dokumentiert die gewünschte E-POST-Adresspositionierung. Sie verändert die PDF-Datei nicht automatisch.");
        note.setPadding(0,UiKit.dp(this,8),0,0);
        valuesBox.addView(note);
        root.addView(UiKit.surfaceCard(this,valuesBox));

        MaterialButton reset=UiKit.tonal(this,"Standardposition");
        reset.setOnClickListener(v->{rx=20;ry=45;sx=20;sy=27;preview.setPositions(rx,ry,sx,sy);updateValues();});
        root.addView(reset,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));

        MaterialButton save=UiKit.primary(this,profile==null?"Hinweis anzeigen":"Als Profilvorlage speichern");
        save.setOnClickListener(v->save(save));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56));
        slp.setMargins(0,UiKit.dp(this,10),0,0);
        root.addView(save,slp);

        page.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(page);
        SystemUi.apply(this,page);
    }

    private void updateValues(){
        if(values!=null) values.setText(String.format(Locale.GERMANY,
                "Empfänger: X %.1f mm, Y %.1f mm\nAbsender: X %.1f mm, Y %.1f mm",rx,ry,sx,sy));
    }

    private void save(MaterialButton anchor){
        if(profile==null){
            Snackbar.make(anchor,"Öffne den Assistenten aus einem Versandprofil, um die Vorlage zu speichern.",Snackbar.LENGTH_LONG).show();
            return;
        }
        try{
            profile.recipientWindow=String.format(Locale.US,"%.2f,%.2f",rx,ry);
            profile.senderWindow=String.format(Locale.US,"%.2f,%.2f",sx,sy);
            List<Profile> list=new ArrayList<>(SecureStore.load(this));
            for(int i=0;i<list.size();i++) if(list.get(i).id.equals(profile.id)){list.set(i,profile);break;}
            SecureStore.save(this,list);
            Snackbar.make(anchor,"Versandfeld-Vorlage gespeichert.",Snackbar.LENGTH_LONG).show();
        }catch(Exception e){
            Snackbar.make(anchor,"Speichern fehlgeschlagen.",Snackbar.LENGTH_LONG).show();
        }
    }
}
