package de.eposthelper.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private LinearLayout content;
    private LinearLayout nav;
    private Uri selectedPdf;
    private String selectedProfileId;
    private int currentTab=0;

    private final ActivityResultLauncher<String[]> picker=registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),uri->{
                if(uri!=null){
                    try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
                    catch(SecurityException ignored){}
                    selectedPdf=uri; currentTab=1; render();
                }
            });

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        if(Intent.ACTION_VIEW.equals(getIntent().getAction())&&getIntent().getData()!=null){
            selectedPdf=getIntent().getData(); currentTab=1;
        }
        buildShell(); render();
    }

    @Override protected void onResume(){
        super.onResume();
        if(content!=null) render();
    }

    private void buildShell(){
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(UiKit.resolveSurface(this));

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(UiKit.dp(this,20),UiKit.dp(this,12),UiKit.dp(this,10),UiKit.dp(this,8));
        LinearLayout brand=new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(UiKit.heading(this,"E-POST Helper",24));
        TextView sub=UiKit.body(this,"Sicherer Hybridbriefversand"); sub.setTextSize(13); brand.addView(sub);
        top.addView(brand,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        MaterialButton settingsButton=UiKit.tonal(this,"⚙");
        settingsButton.setContentDescription("Einstellungen öffnen");
        settingsButton.setMinWidth(UiKit.dp(this,50)); settingsButton.setOnClickListener(v->{currentTab=3;render();});
        top.addView(settingsButton,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,48)));
        page.addView(top);

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this,18),UiKit.dp(this,4),UiKit.dp(this,18),UiKit.dp(this,30));
        scroll.addView(content);
        page.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));

        nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER);
        nav.setPadding(UiKit.dp(this,8),UiKit.dp(this,6),UiKit.dp(this,8),UiKit.dp(this,8));
        String[] labels={"Start","Drucken","Profile","Einstellungen"};
        String[] icons={"⌂","▣","▤","⚙"};
        for(int i=0;i<labels.length;i++){
            final int tab=i;
            LinearLayout item=new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
            item.setPadding(UiKit.dp(this,3),UiKit.dp(this,3),UiKit.dp(this,3),UiKit.dp(this,3));
            TextView icon=new TextView(this); icon.setText(icons[i]); icon.setTextSize(20); icon.setGravity(Gravity.CENTER); icon.setContentDescription(labels[i]);
            TextView label=new TextView(this); label.setText(labels[i]); label.setTextSize(11); label.setGravity(Gravity.CENTER);
            item.addView(icon,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,27)));
            item.addView(label,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,19)));
            item.setTag(new View[]{icon,label}); item.setOnClickListener(v->{currentTab=tab;render();});
            nav.addView(item,new LinearLayout.LayoutParams(0,UiKit.dp(this,56),1f));
        }
        page.addView(nav);
        setContentView(page);
        SystemUi.apply(this,page);
    }

    private void styleNav(){
        int primary=SettingsStore.primary(this);
        for(int i=0;i<nav.getChildCount();i++){
            LinearLayout item=(LinearLayout)nav.getChildAt(i); View[] views=(View[])item.getTag();
            boolean selected=i==currentTab; int color=selected?primary:UiKit.resolveSecondaryText(this);
            ((TextView)views[0]).setTextColor(color); ((TextView)views[1]).setTextColor(color);
            ((TextView)views[1]).setTypeface(Typeface.DEFAULT,selected?Typeface.BOLD:Typeface.NORMAL);
            GradientDrawable bg=new GradientDrawable();
            bg.setCornerRadius(UiKit.dp(this,18));
            bg.setColor(selected?ColorUtils.setAlphaComponent(primary,28):Color.TRANSPARENT);
            item.setBackground(bg);
        }
    }

    private void render(){
        content.removeAllViews(); styleNav();
        if(currentTab==0)renderHome();
        else if(currentTab==1)renderPrint();
        else if(currentTab==2)renderProfiles();
        else renderSettings();
    }

    private TextView section(String text){
        TextView t=UiKit.heading(this,text,19); t.setPadding(0,UiKit.dp(this,14),0,UiKit.dp(this,5)); return t;
    }

    private void renderHome(){
        List<Profile> profiles=SecureStore.load(this);
        long active=profiles.stream().filter(p->p.active).count();
        int[] grad=SettingsStore.gradient(this);

        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView shield=UiKit.heroTitle(this,"◇",42); shield.setGravity(Gravity.CENTER); hero.addView(shield);
        TextView h=UiKit.heroTitle(this,"Verbindung geschützt",24); h.setGravity(Gravity.CENTER); hero.addView(h);
        TextView b=UiKit.heroBody(this,"TLS 1.2/1.3 · Zertifikatsprüfung · verschlüsselte Zugangsdaten");
        b.setGravity(Gravity.CENTER); b.setPadding(0,UiKit.dp(this,6),0,UiKit.dp(this,12)); hero.addView(b);
        MaterialButton security=UiKit.primary(this,"Sicherheitsdetails");
        security.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));
        security.setOnClickListener(v->{currentTab=3;render();}); hero.addView(security);
        content.addView(UiKit.hero(this,hero,grad[0],grad[1]));

        LinearLayout metrics=new LinearLayout(this); metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("Profile",String.valueOf(active),"aktiv"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        View gap=new View(this); metrics.addView(gap,new LinearLayout.LayoutParams(UiKit.dp(this,12),1));
        metrics.addView(metric("Dokument",selectedPdf==null?"Keins":"Bereit",selectedPdf==null?"PDF wählen":"versandbereit"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        content.addView(metrics);

        content.addView(section("Schnellzugriff"));
        content.addView(actionCard("▣","PDF senden","Dokument auswählen, Profil wählen und einliefern",()->{currentTab=1;render();}));
        content.addView(actionCard("▤","Profile verwalten","Sammelkorb oder Netzwerkdrucker einrichten",()->{currentTab=2;render();}));
        MaterialButton add=UiKit.primary(this,"+  Neues Profil"); add.setOnClickListener(v->startActivity(new Intent(this,ProfileEditActivity.class)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54)); lp.setMargins(0,UiKit.dp(this,12),0,0); content.addView(add,lp);
    }

    private MaterialCardView metric(String label,String value,String sub){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        TextView l=UiKit.body(this,label); l.setTextSize(12); box.addView(l);
        TextView v=UiKit.heading(this,value,24); v.setPadding(0,UiKit.dp(this,5),0,0); box.addView(v);
        TextView s=UiKit.body(this,sub); s.setTextSize(12); box.addView(s);
        return UiKit.surfaceCard(this,box);
    }

    private MaterialCardView actionCard(String icon,String title,String subtitle,Runnable action){
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic=new TextView(this); ic.setText(icon); ic.setTextSize(24); ic.setGravity(Gravity.CENTER); ic.setTextColor(SettingsStore.primary(this));
        row.addView(ic,new LinearLayout.LayoutParams(UiKit.dp(this,50),UiKit.dp(this,50)));
        LinearLayout txt=new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(UiKit.heading(this,title,16)); TextView s=UiKit.body(this,subtitle); s.setTextSize(13); txt.addView(s);
        row.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView arrow=UiKit.heading(this,"›",26); row.addView(arrow,new LinearLayout.LayoutParams(UiKit.dp(this,28),UiKit.dp(this,42)));
        MaterialCardView card=UiKit.surfaceCard(this,row); card.setClickable(true); card.setOnClickListener(v->action.run()); return card;
    }

    private void renderPrint(){
        int[] grad=SettingsStore.gradient(this);
        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView icon=UiKit.heroTitle(this,"▣",42); icon.setGravity(Gravity.CENTER); hero.addView(icon);
        TextView title=UiKit.heroTitle(this,selectedPdf==null?"Bereit zum Drucken":"PDF bereit",24); title.setGravity(Gravity.CENTER); hero.addView(title);
        TextView detail=UiKit.heroBody(this,selectedPdf==null?"PDF öffnen und Versandprofil wählen":safeName(selectedPdf));
        detail.setGravity(Gravity.CENTER); detail.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,12)); hero.addView(detail);
        MaterialButton choose=UiKit.primary(this,selectedPdf==null?"PDF öffnen":"Andere PDF wählen");
        choose.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF)); choose.setOnClickListener(v->picker.launch(new String[]{"application/pdf"})); hero.addView(choose);
        content.addView(UiKit.hero(this,hero,grad[0],grad[1]));

        List<Profile> profiles=SecureStore.load(this); content.addView(section("Versandprofil wählen"));
        if(profiles.stream().noneMatch(p->p.active)){
            content.addView(UiKit.surfaceCard(this,UiKit.body(this,"Kein aktives Profil vorhanden.")));
            MaterialButton add=UiKit.primary(this,"Profil anlegen"); add.setOnClickListener(v->startActivity(new Intent(this,ProfileEditActivity.class))); content.addView(add); return;
        }

        RadioGroup group=new RadioGroup(this);
        for(Profile p:profiles){
            if(!p.active)continue;
            RadioButton rb=new RadioButton(this); rb.setId(View.generateViewId()); rb.setTag(p.id);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(SettingsStore.primary(this)));
            rb.setText(p.name+"\n"+profileSummary(p)); rb.setTextSize(15);
            rb.setPadding(UiKit.dp(this,6),UiKit.dp(this,10),UiKit.dp(this,6),UiKit.dp(this,10));
            if(selectedProfileId==null)selectedProfileId=p.id; rb.setChecked(p.id.equals(selectedProfileId)); group.addView(rb);
        }
        group.setOnCheckedChangeListener((g,id)->{View v=g.findViewById(id);if(v!=null)selectedProfileId=String.valueOf(v.getTag());});
        content.addView(UiKit.surfaceCard(this,group));

        MaterialButton send=UiKit.primary(this,"Jetzt senden"); send.setOnClickListener(v->sendSelected(send));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56)); lp.setMargins(0,UiKit.dp(this,12),0,UiKit.dp(this,7)); content.addView(send,lp);
        MaterialButton printService=UiKit.tonal(this,"Android-Druckdienst öffnen"); printService.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS))); content.addView(printService);
    }

    private String safeName(Uri uri){String n=uri.getLastPathSegment();return n==null?"PDF ausgewählt":n;}
    private String profileSummary(Profile p){
        return (Profile.TYPE_IPP.equals(p.type)?"Netzwerkdrucker":"Sammelkorb")+"  •  "+(p.duplex?"Beidseitig":"Einseitig")+"  •  "+(p.color?"Farbe":"S/W")+"  •  "+("Nein".equals(p.registeredMail)?"Standard":p.registeredMail);
    }

    private void sendSelected(View anchor){
        if(selectedPdf==null){Snackbar.make(anchor,"Bitte zuerst ein PDF auswählen.",Snackbar.LENGTH_LONG).show();return;}
        Profile p=SecureStore.find(this,selectedProfileId);
        if(p==null){Snackbar.make(anchor,"Versandprofil fehlt.",Snackbar.LENGTH_LONG).show();return;}
        anchor.setEnabled(false); Snackbar.make(anchor,"Sichere Übertragung läuft…",Snackbar.LENGTH_LONG).show();
        Executors.newSingleThreadExecutor().execute(()->{
            try{Sender.send(this,selectedPdf,p);runOnUiThread(()->{anchor.setEnabled(true);Snackbar.make(anchor,"An E-POST übergeben.",Snackbar.LENGTH_LONG).show();});}
            catch(Exception e){runOnUiThread(()->{anchor.setEnabled(true);Snackbar.make(anchor,"Versand fehlgeschlagen: "+e.getMessage(),Snackbar.LENGTH_LONG).show();});}
        });
    }

    private void renderProfiles(){
        content.addView(section("Profile verwalten"));
        TextView intro=UiKit.body(this,"Ein Profil repräsentiert ein administrativ eingerichtetes E-POST-Ziel. Die tatsächlichen Versandoptionen liegen auf diesem Ziel.");
        intro.setPadding(0,0,0,UiKit.dp(this,8)); content.addView(intro);
        for(Profile p:SecureStore.load(this)){
            LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
            LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(UiKit.heading(this,p.name,17),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            head.addView(UiKit.pill(this,p.active?"Aktiv":"Inaktiv",p.active)); box.addView(head);
            TextView summary=UiKit.body(this,profileSummary(p)); summary.setPadding(0,UiKit.dp(this,7),0,UiKit.dp(this,5)); box.addView(summary);
            box.addView(UiKit.mono(this,redactUrl(p.url)));
            MaterialButton edit=UiKit.tonal(this,"Bearbeiten & prüfen"); edit.setOnClickListener(v->{Intent i=new Intent(this,ProfileEditActivity.class);i.putExtra("profileId",p.id);startActivity(i);});
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,48)); ep.setMargins(0,UiKit.dp(this,12),0,0); box.addView(edit,ep);
            content.addView(UiKit.surfaceCard(this,box));
        }
        MaterialButton add=UiKit.primary(this,"+  Profil hinzufügen"); add.setOnClickListener(v->startActivity(new Intent(this,ProfileEditActivity.class)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54)); lp.setMargins(0,UiKit.dp(this,12),0,0); content.addView(add,lp);
    }

    private String redactUrl(String u){
        try{Uri x=Uri.parse(Sender.normalizeSecureUrl(u));return x.getScheme()+"://"+x.getHost()+"/…";}
        catch(Exception e){return "Noch nicht konfiguriert";}
    }

    private void renderSettings(){
        content.addView(section("Darstellung"));
        LinearLayout appearance=new LinearLayout(this); appearance.setOrientation(LinearLayout.VERTICAL);
        appearance.addView(UiKit.body(this,"Modus"));
        RadioGroup modes=new RadioGroup(this);
        String[][] modeData={{"System","system"},{"Hell","light"},{"Dunkel","dark"}};
        String selected=SettingsStore.appearance(this);
        for(String[] m:modeData){
            RadioButton rb=new RadioButton(this); rb.setText(m[0]); rb.setTag(m[1]); rb.setId(View.generateViewId()); rb.setChecked(m[1].equals(selected)); modes.addView(rb);
        }
        modes.setOnCheckedChangeListener((g,id)->{View v=g.findViewById(id);if(v!=null)SettingsStore.setAppearance(this,String.valueOf(v.getTag()));});
        appearance.addView(modes);
        appearance.addView(UiKit.body(this,"Farbpalette"));
        Spinner palette=new Spinner(this);
        String[] labels={"Ocean","Forest","Sunset","Aurora","Lavender","Graphite"};
        String[] values={"ocean","forest","sunset","aurora","lavender","graphite"};
        palette.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        int pi=0; for(int i=0;i<values.length;i++)if(values[i].equals(SettingsStore.palette(this)))pi=i; palette.setSelection(pi);
        palette.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                String next=values[pos]; if(!next.equals(SettingsStore.palette(MainActivity.this))){SettingsStore.setPalette(MainActivity.this,next);render();}
            }
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        appearance.addView(palette,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));
        content.addView(UiKit.surfaceCard(this,appearance));

        content.addView(section("E-POST"));
        content.addView(actionCard("▤","Versandprofile","Sammelkorb- und IPP-Ziele bearbeiten und Verbindung prüfen",()->{currentTab=2;render();}));
        content.addView(actionCard("▣","Android-Druckdienst","E-POST Helper als Systemdrucker aktivieren",()->startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS))));

        content.addView(section("Sicherheit"));
        content.addView(infoCard("Transport","Nur HTTPS/IPPS. TLS 1.2/1.3, System-Truststore, Hostname-Prüfung und optionales SPKI-Pinning."));
        content.addView(infoCard("Lokale Daten","Zugangsdaten und Profile werden AES-256-GCM-verschlüsselt. Der Schlüssel bleibt im Android Keystore."));
        content.addView(infoCard("Adressfenster","Adresskorrekturen folgen den E-POST-Werkzeugen und Vorlagen. Die App erfindet keine eigenen X/Y-Verschiebungen."));
    }

    private MaterialCardView infoCard(String title,String text){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.addView(UiKit.heading(this,title,17)); TextView b=UiKit.body(this,text); b.setPadding(0,UiKit.dp(this,7),0,0); box.addView(b);
        return UiKit.surfaceCard(this,box);
    }
}
