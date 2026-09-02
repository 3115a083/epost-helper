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
        page.addView(top);

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this,18),UiKit.dp(this,4),UiKit.dp(this,18),UiKit.dp(this,30));
        scroll.addView(content);
        page.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));

        nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER);
        nav.setPadding(UiKit.dp(this,8),UiKit.dp(this,6),UiKit.dp(this,8),UiKit.dp(this,8));
        String[] labels={"Start","Drucken","Profile","Einstellungen"};
        String[] icons={"⌂","✉","☷","⚙"};
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
        long connected=profiles.stream().filter(p->p.active&&p.connectionVerified).count();
        int[] grad=SettingsStore.gradient(this);

        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView shield=UiKit.heroTitle(this,"◇",42); shield.setGravity(Gravity.CENTER); hero.addView(shield);
        String heroTitle=connected>0?"Verbindung bereit":(active>0?"Profile noch nicht geprüft":"Noch nicht eingerichtet");
        TextView h=UiKit.heroTitle(this,heroTitle,24); h.setGravity(Gravity.CENTER); hero.addView(h);
        String heroText=connected>0?connected+" verifiziertes E-POST-Ziel":(active>0?"Prüfe die E-POST-Verbindung in den Profilen":"Lege zuerst ein Versandprofil an");
        TextView b=UiKit.heroBody(this,heroText+" · TLS geschützt");
        b.setGravity(Gravity.CENTER); b.setPadding(0,UiKit.dp(this,6),0,UiKit.dp(this,12)); hero.addView(b);
        MaterialButton security=UiKit.primary(this,"Sicherheitsdetails");
        security.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));
        security.setOnClickListener(v->{currentTab=3;render();}); hero.addView(security);
        content.addView(UiKit.hero(this,hero,grad[0],grad[1]));

        LinearLayout metrics=new LinearLayout(this); metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("Verbindungen",String.valueOf(connected),"verifiziert"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
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
        new Thread(()->{
            try{Sender.send(this,selectedPdf,p);runOnUiThread(()->{anchor.setEnabled(true);Snackbar.make(anchor,"An E-POST übergeben.",Snackbar.LENGTH_LONG).show();});}
            catch(Exception e){runOnUiThread(()->{anchor.setEnabled(true);DebugUtil.error(this,anchor,"Versand fehlgeschlagen",e);});}
        },"epost-send").start();
    }

    private void renderProfiles(){
        content.addView(section("Profile verwalten"));
        TextView intro=UiKit.body(this,"Ein Profil repräsentiert ein administrativ eingerichtetes E-POST-Ziel. Die tatsächlichen Versandoptionen liegen auf diesem Ziel.");
        intro.setPadding(0,0,0,UiKit.dp(this,8)); content.addView(intro);
        for(Profile p:SecureStore.load(this)){
            LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
            LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(UiKit.heading(this,p.name,17),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            String status=!p.active?"Inaktiv":(p.connectionVerified?"Verbunden":"Nicht geprüft");
            head.addView(UiKit.pill(this,status,p.connectionVerified)); box.addView(head);
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

        LinearLayout modeBox=new LinearLayout(this); modeBox.setOrientation(LinearLayout.VERTICAL);
        modeBox.addView(UiKit.heading(this,"Erscheinungsbild",17));
        TextView mh=UiKit.body(this,"System, Hell oder Dunkel"); mh.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,10)); modeBox.addView(mh);
        com.google.android.material.button.MaterialButtonToggleGroup modes=new com.google.android.material.button.MaterialButtonToggleGroup(this);
        modes.setSingleSelection(true); modes.setSelectionRequired(true);
        String[][] md={{"System","system"},{"Hell","light"},{"Dunkel","dark"}};
        for(String[] m:md){
            MaterialButton bt=UiKit.tonal(this,m[0]); bt.setId(View.generateViewId()); bt.setTag(m[1]);
            modes.addView(bt,new LinearLayout.LayoutParams(0,UiKit.dp(this,48),1f));
            if(m[1].equals(SettingsStore.appearance(this)))modes.check(bt.getId());
        }
        modes.addOnButtonCheckedListener((g,id,checked)->{
            if(checked){View v=g.findViewById(id);if(v!=null)SettingsStore.setAppearance(MainActivity.this,String.valueOf(v.getTag()));}
        });
        modeBox.addView(modes);
        content.addView(UiKit.surfaceCard(this,modeBox));

        LinearLayout paletteBox=new LinearLayout(this); paletteBox.setOrientation(LinearLayout.VERTICAL);
        paletteBox.addView(UiKit.heading(this,"Farbpalette",17));
        com.google.android.material.chip.ChipGroup chips=new com.google.android.material.chip.ChipGroup(this);
        chips.setSingleSelection(true); chips.setSelectionRequired(true);
        String[][] pd={{"Ocean","ocean"},{"Forest","forest"},{"Sunset","sunset"},{"Aurora","aurora"},{"Lavender","lavender"},{"Graphite","graphite"}};
        for(String[] p:pd){
            com.google.android.material.chip.Chip chip=new com.google.android.material.chip.Chip(this);
            chip.setId(View.generateViewId()); chip.setText(p[0]); chip.setTag(p[1]); chip.setCheckable(true);
            chip.setChecked(p[1].equals(SettingsStore.palette(this))); chip.setChipCornerRadius(UiKit.dp(this,18));
            chip.setOnCheckedChangeListener((button,checked)->{
                if(checked){
                    String next=String.valueOf(button.getTag());
                    if(!next.equals(SettingsStore.palette(MainActivity.this))){SettingsStore.setPalette(MainActivity.this,next);render();}
                }
            });
            chips.addView(chip);
        }
        paletteBox.addView(chips);
        content.addView(UiKit.surfaceCard(this,paletteBox));

        content.addView(section("Diagnose"));
        LinearLayout debugBox=new LinearLayout(this); debugBox.setOrientation(LinearLayout.VERTICAL);
        com.google.android.material.materialswitch.MaterialSwitch debug=new com.google.android.material.materialswitch.MaterialSwitch(this);
        debug.setText("Debugmodus"); debug.setChecked(SettingsStore.debugMode(this));
        debug.setOnCheckedChangeListener((button,checked)->SettingsStore.setDebugMode(MainActivity.this,checked));
        debugBox.addView(debug);
        TextView dh=UiKit.body(this,"Im Debugmodus bleiben Fehler sichtbar und werden automatisch in die Zwischenablage kopiert. Ohne Debugmodus verschwinden Fehlermeldungen kurz danach.");
        dh.setTextSize(13); debugBox.addView(dh);
        content.addView(UiKit.surfaceCard(this,debugBox));

        content.addView(section("Werkzeuge"));
        content.addView(actionCard("⌖","Versandfeld-Assistent","PDF laden und den Adressbereich des eigenen Brieflayouts markieren",()->startActivity(new Intent(this,AddressConfigActivity.class))));
        content.addView(actionCard("⚙","Android-Druckdienst","E-POST Helper als Systemdrucker aktivieren",()->startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS))));

        TextView security=UiKit.body(this,"Sicherheit: nur HTTPS/IPPS, TLS 1.2/1.3, Zertifikats- und Hostnameprüfung. Zugangsdaten werden verschlüsselt im Android Keystore gespeichert.");
        security.setTextSize(12); security.setPadding(0,UiKit.dp(this,14),0,0); content.addView(security);
    }

    private MaterialCardView infoCard(String title,String text){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.addView(UiKit.heading(this,title,17)); TextView b=UiKit.body(this,text); b.setPadding(0,UiKit.dp(this,7),0,0); box.addView(b);
        return UiKit.surfaceCard(this,box);
    }
}
