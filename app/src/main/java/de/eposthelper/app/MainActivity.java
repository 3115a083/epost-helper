package de.eposthelper.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private LinearLayout content;
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
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(UiKit.resolveSurface(this));

        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(UiKit.dp(this,20),UiKit.dp(this,16),UiKit.dp(this,12),UiKit.dp(this,10));
        LinearLayout brand=new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL);
        TextView title=UiKit.heading(this,"E-POST Helper",24);
        TextView sub=UiKit.body(this,"PDF sicher als Brief versenden"); sub.setTextSize(13);
        brand.addView(title); brand.addView(sub);
        top.addView(brand,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView gear=new TextView(this); gear.setText("⚙"); gear.setTextSize(24); gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Einstellungen öffnen"); gear.setOnClickListener(v->{currentTab=3;render();});
        top.addView(gear,new LinearLayout.LayoutParams(UiKit.dp(this,52),UiKit.dp(this,52)));
        page.addView(top);

        ScrollView scroll=new ScrollView(this);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this,18),UiKit.dp(this,4),UiKit.dp(this,18),UiKit.dp(this,28));
        scroll.addView(content);
        page.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));

        LinearLayout nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(UiKit.dp(this,8),UiKit.dp(this,6),UiKit.dp(this,8),UiKit.dp(this,10));
        String[] labels={"Start","Drucken","Profile","Einstellungen"};
        String[] icons={"⌂","▣","▤","⚙"};
        for(int i=0;i<labels.length;i++){
            final int tab=i;
            LinearLayout item=new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
            TextView ic=new TextView(this); ic.setText(icons[i]); ic.setTextSize(20); ic.setGravity(Gravity.CENTER); ic.setContentDescription(labels[i]);
            TextView tx=new TextView(this); tx.setText(labels[i]); tx.setTextSize(11); tx.setGravity(Gravity.CENTER);
            item.addView(ic,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,28)));
            item.addView(tx,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,20)));
            item.setOnClickListener(v->{currentTab=tab;render();});
            item.setTag(new View[]{ic,tx});
            nav.addView(item,new LinearLayout.LayoutParams(0,UiKit.dp(this,56),1f));
        }
        nav.setTag("nav");
        page.addView(nav);
        setContentView(page);
    }

    private void styleNav(){
        ViewGroup page=(ViewGroup)content.getParent().getParent();
        LinearLayout nav=(LinearLayout)page.getChildAt(2);
        for(int i=0;i<nav.getChildCount();i++){
            LinearLayout item=(LinearLayout)nav.getChildAt(i);
            View[] views=(View[])item.getTag();
            int color=i==currentTab?0xFF2457E6:UiKit.resolveSecondaryText(this);
            ((TextView)views[0]).setTextColor(color); ((TextView)views[1]).setTextColor(color);
            ((TextView)views[1]).setTypeface(Typeface.DEFAULT,i==currentTab?Typeface.BOLD:Typeface.NORMAL);
        }
    }

    private void render(){
        content.removeAllViews(); styleNav();
        if(currentTab==0) renderHome();
        else if(currentTab==1) renderPrint();
        else if(currentTab==2) renderProfiles();
        else renderSettings();
    }

    private void renderHome(){
        List<Profile> profiles=SecureStore.load(this);
        long active=profiles.stream().filter(p->p.active).count();

        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView shield=UiKit.heroTitle(this,"◇",42); shield.setGravity(Gravity.CENTER); hero.addView(shield);
        TextView h=UiKit.heroTitle(this,"Verbindung geschützt",24); h.setGravity(Gravity.CENTER); hero.addView(h);
        TextView b=UiKit.heroBody(this,"Nur TLS 1.2/1.3. Zertifikatsprüfung aktiv. Keine Klartext-Verbindungen.");
        b.setGravity(Gravity.CENTER); b.setPadding(0,UiKit.dp(this,6),0,UiKit.dp(this,10)); hero.addView(b);
        MaterialButton open=UiKit.primary(this,"Sicherheitsdetails"); open.setTextColor(Color.WHITE);
        open.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));
        open.setOnClickListener(v->{currentTab=3;render();}); hero.addView(open);
        content.addView(UiKit.hero(this,hero,0xFF1769C2,0xFF1BC56C));

        LinearLayout metrics=new LinearLayout(this); metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(0,0,0,UiKit.dp(this,6));
        metrics.addView(metric("Profile aktiv",String.valueOf(active),"Versandziele"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(UiKit.dp(this,12),1); metrics.addView(new View(this),gap);
        metrics.addView(metric("Dokument",selectedPdf==null?"Keins":"Bereit",selectedPdf==null?"PDF wählen":"Zum Senden"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        content.addView(metrics);

        content.addView(section("Schnellzugriff"));
        content.addView(actionCard("▣","PDF senden","Dokument auswählen und Versandprofil festlegen",()->{currentTab=1;render();}));
        content.addView(actionCard("▤","Profile verwalten","Sammelkorb und Netzwerkdrucker konfigurieren",()->{currentTab=2;render();}));
        MaterialButton add=UiKit.primary(this,"+  Neues Profil");
        add.setOnClickListener(v->startActivity(new Intent(this,ProfileEditActivity.class)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54)); lp.setMargins(0,UiKit.dp(this,12),0,0); content.addView(add,lp);
    }

    private MaterialCardView metric(String label,String value,String sub){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        TextView l=UiKit.body(this,label); l.setTextSize(12); box.addView(l);
        TextView v=UiKit.heading(this,value,24); v.setPadding(0,UiKit.dp(this,5),0,0); box.addView(v);
        TextView s=UiKit.body(this,sub); s.setTextSize(12); box.addView(s);
        return UiKit.surfaceCard(this,box);
    }

    private TextView section(String text){
        TextView t=UiKit.heading(this,text,18); t.setPadding(0,UiKit.dp(this,14),0,UiKit.dp(this,5)); return t;
    }

    private MaterialCardView actionCard(String icon,String title,String subtitle,Runnable action){
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView ic=new TextView(this); ic.setText(icon); ic.setTextSize(25); ic.setTextColor(0xFF2457E6); ic.setGravity(Gravity.CENTER);
        row.addView(ic,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,48)));
        LinearLayout txt=new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL);
        TextView t=UiKit.heading(this,title,16); TextView s=UiKit.body(this,subtitle); s.setTextSize(13); txt.addView(t); txt.addView(s);
        row.addView(txt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView arrow=UiKit.heading(this,"›",26); row.addView(arrow,new LinearLayout.LayoutParams(UiKit.dp(this,28),UiKit.dp(this,42)));
        MaterialCardView card=UiKit.surfaceCard(this,row); card.setOnClickListener(v->action.run()); card.setClickable(true); return card;
    }

    private void renderPrint(){
        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView icon=UiKit.heroTitle(this,"▣",42); icon.setGravity(Gravity.CENTER); hero.addView(icon);
        TextView t=UiKit.heroTitle(this,selectedPdf==null?"Bereit zum Drucken":"PDF bereit",24); t.setGravity(Gravity.CENTER); hero.addView(t);
        TextView s=UiKit.heroBody(this,selectedPdf==null?"PDF öffnen und Versandart auswählen":safeName(selectedPdf));
        s.setGravity(Gravity.CENTER); s.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,10)); hero.addView(s);
        MaterialButton choose=UiKit.primary(this,selectedPdf==null?"PDF öffnen":"Andere PDF wählen"); choose.setTextColor(Color.WHITE);
        choose.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF)); choose.setOnClickListener(v->picker.launch(new String[]{"application/pdf"})); hero.addView(choose);
        content.addView(UiKit.hero(this,hero,0xFF5146D8,0xFF8B70F1));

        List<Profile> profiles=SecureStore.load(this);
        content.addView(section("Versandprofil wählen"));
        if(profiles.stream().noneMatch(p->p.active)){
            content.addView(UiKit.surfaceCard(this,UiKit.body(this,"Noch kein aktives Versandprofil. Lege zuerst ein Profil an.")));
            MaterialButton add=UiKit.primary(this,"Profil anlegen"); add.setOnClickListener(v->startActivity(new Intent(this,ProfileEditActivity.class))); content.addView(add); return;
        }

        RadioGroup group=new RadioGroup(this);
        for(Profile p:profiles){
            if(!p.active) continue;
            RadioButton rb=new RadioButton(this);
            rb.setId(View.generateViewId()); rb.setTag(p.id); rb.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF2457E6));
            rb.setText(p.name+"\n"+profileSummary(p)); rb.setTextSize(15); rb.setPadding(UiKit.dp(this,6),UiKit.dp(this,10),UiKit.dp(this,6),UiKit.dp(this,10));
            if(selectedProfileId==null) selectedProfileId=p.id;
            rb.setChecked(p.id.equals(selectedProfileId)); group.addView(rb);
        }
        group.setOnCheckedChangeListener((g,id)->{View v=g.findViewById(id); if(v!=null)selectedProfileId=String.valueOf(v.getTag());});
        content.addView(UiKit.surfaceCard(this,group));

        MaterialButton send=UiKit.primary(this,"Drucken / senden"); send.setOnClickListener(v->sendSelected(send));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56)); lp.setMargins(0,UiKit.dp(this,12),0,UiKit.dp(this,7)); content.addView(send,lp);
        MaterialButton service=UiKit.tonal(this,"Android-Druckdienst öffnen"); service.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS))); content.addView(service);
    }

    private String safeName(Uri uri){
        String n=uri.getLastPathSegment(); return n==null?"PDF ausgewählt":n;
    }

    private String profileSummary(Profile p){
        String type=Profile.TYPE_IPP.equals(p.type)?"Netzwerkdrucker":"Sammelkorb";
        return type+"  •  "+(p.duplex?"Beidseitig":"Einseitig")+"  •  "+(p.color?"Farbe":"S/W")+"  •  "+("Nein".equals(p.registeredMail)?"Standard":p.registeredMail);
    }

    private void sendSelected(View anchor){
        if(selectedPdf==null){Snackbar.make(anchor,"Bitte zuerst ein PDF auswählen.",Snackbar.LENGTH_LONG).show();return;}
        Profile p=SecureStore.find(this,selectedProfileId);
        if(p==null){Snackbar.make(anchor,"Versandprofil fehlt.",Snackbar.LENGTH_LONG).show();return;}
        anchor.setEnabled(false); Snackbar.make(anchor,"Sichere Übertragung läuft…",Snackbar.LENGTH_LONG).show();
        Executors.newSingleThreadExecutor().execute(()->{
            try{
                Sender.send(this,selectedPdf,p);
                runOnUiThread(()->{anchor.setEnabled(true);Snackbar.make(anchor,"An E-POST übergeben.",Snackbar.LENGTH_LONG).show();});
            }catch(Exception e){
                runOnUiThread(()->{anchor.setEnabled(true);Snackbar.make(anchor,"Versand fehlgeschlagen: "+e.getMessage(),Snackbar.LENGTH_LONG).show();});
            }
        });
    }

    private void renderProfiles(){
        content.addView(section("Profile verwalten"));
        TextView intro=UiKit.body(this,"Ein Profil entspricht genau einem serverseitig konfigurierten E-POST-Ziel."); intro.setPadding(0,0,0,UiKit.dp(this,8)); content.addView(intro);
        List<Profile> profiles=SecureStore.load(this);
        for(Profile p:profiles){
            LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
            LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
            TextView n=UiKit.heading(this,p.name,17); header.addView(n,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            header.addView(UiKit.pill(this,p.active?"Aktiv":"Inaktiv",p.active));
            box.addView(header);
            TextView summary=UiKit.body(this,profileSummary(p)); summary.setPadding(0,UiKit.dp(this,7),0,UiKit.dp(this,5)); box.addView(summary);
            box.addView(UiKit.mono(this,redactUrl(p.url)));
            MaterialButton edit=UiKit.tonal(this,"Bearbeiten"); edit.setOnClickListener(v->{Intent i=new Intent(this,ProfileEditActivity.class);i.putExtra("profileId",p.id);startActivity(i);});
            LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,48)); elp.setMargins(0,UiKit.dp(this,12),0,0); box.addView(edit,elp);
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
        content.addView(section("Einstellungen"));
        content.addView(settingsCard("Verbindung","HTTPS/IPPS ist verpflichtend. TLS 1.2 und 1.3 sind erlaubt. Android prüft Zertifikat und Hostnamen."));
        content.addView(settingsCard("Sicherheit","Profile und Zugangsdaten sind AES-256-GCM-verschlüsselt. Der Schlüssel liegt nicht exportierbar im Android Keystore."));
        content.addView(settingsCard("Zertifikat-Pinning","Optional kann pro Profil ein SHA-256-SPKI-Pin hinterlegt werden. Zertifikatsfehler können nicht ignoriert werden."));
        content.addView(settingsCard("Adressfenster","Korrekturwerte werden am Profil dokumentiert. Das PDF wird lokal nicht gerastert oder verändert."));
        MaterialButton print=UiKit.tonal(this,"Android-Druckeinstellungen"); print.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS))); content.addView(print);
    }

    private MaterialCardView settingsCard(String title,String text){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.addView(UiKit.heading(this,title,17)); TextView b=UiKit.body(this,text); b.setPadding(0,UiKit.dp(this,7),0,0); box.addView(b);
        return UiKit.surfaceCard(this,box);
    }
}
