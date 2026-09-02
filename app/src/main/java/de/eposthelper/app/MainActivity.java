package de.eposthelper.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private LinearLayout content,nav,recentContainer;
    private int currentTab=0;
    private final ArrayDeque<Integer> tabHistory=new ArrayDeque<>();
    private long lastBackAt=0L;
    private boolean historyLoading=false;
    private List<RecentLetter> recentCache=new ArrayList<>();

    private final ActivityResultLauncher<Uri> folderPicker=registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),uri->{
                if(uri==null)return;
                try{
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }catch(Exception ignored){}
                SettingsStore.setOutboxFolder(this,uri.toString());
                int imported=OutboxStore.importFolder(this);
                if(currentTab==3)render();
                Snackbar.make(content,imported>0?imported+" PDF(s) importiert.":"Druckausgangsordner gespeichert.",Snackbar.LENGTH_SHORT).show();
            });

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        SettingsStore.applyDynamicColors(this);
        if(b!=null)currentTab=b.getInt("currentTab",0);
        Uri incoming=getIntent().getData();
        if(Intent.ACTION_VIEW.equals(getIntent().getAction())&&incoming!=null){
            try{getContentResolver().takePersistableUriPermission(incoming,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            OutboxStore.add(this,incoming,incoming.getLastPathSegment(),false);
            currentTab=1;
        }
        buildShell();
        setupBackNavigation();
        OutboxStore.importFolder(this);
        render();
    }

    @Override protected void onResume(){
        super.onResume();
        OutboxStore.importFolder(this);
        if(content!=null)render();
    }

    @Override protected void onSaveInstanceState(Bundle out){
        out.putInt("currentTab",currentTab);
        super.onSaveInstanceState(out);
    }

    private void navigateTo(int tab){
        if(tab==currentTab)return;
        tabHistory.push(currentTab);
        currentTab=tab;
        render();
    }

    private void setupBackNavigation(){
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){
                if(!tabHistory.isEmpty()){
                    currentTab=tabHistory.pop();render();return;
                }
                if(currentTab!=0){currentTab=0;render();return;}
                long now=System.currentTimeMillis();
                if(now-lastBackAt<2000)finish();
                else{
                    lastBackAt=now;
                    Snackbar.make(content,"Zum Beenden erneut Zurück drücken",Snackbar.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void buildShell(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(UiKit.resolveSurface(this));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(UiKit.dp(this,20),UiKit.dp(this,12),UiKit.dp(this,16),UiKit.dp(this,8));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(UiKit.heading(this,"Briefversand",24));
        TextView sub=UiKit.body(this,"Deutsche Post & LetterXpress");sub.setTextSize(13);brand.addView(sub);
        top.addView(brand,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(top);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this,18),UiKit.dp(this,4),UiKit.dp(this,18),UiKit.dp(this,30));
        scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));

        nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(UiKit.dp(this,8),UiKit.dp(this,5),UiKit.dp(this,8),UiKit.dp(this,8));
        String[] labels={"Start","Drucken","Profile","Einstellungen"};
        int[] icons={R.drawable.ic_nav_home,R.drawable.ic_nav_print,R.drawable.ic_nav_profiles,R.drawable.ic_nav_settings};
        for(int i=0;i<labels.length;i++){
            final int tab=i;
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);
            ImageView icon=new ImageView(this);icon.setImageResource(icons[i]);icon.setContentDescription(labels[i]);
            TextView label=new TextView(this);label.setText(labels[i]);label.setTextSize(11);label.setGravity(Gravity.CENTER);
            item.addView(icon,new LinearLayout.LayoutParams(UiKit.dp(this,25),UiKit.dp(this,25)));
            item.addView(label,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,20)));
            item.setTag(new View[]{icon,label});item.setOnClickListener(v->navigateTo(tab));
            nav.addView(item,new LinearLayout.LayoutParams(0,UiKit.dp(this,58),1f));
        }
        page.addView(nav);
        setContentView(page);SystemUi.apply(this,page);
    }

    private void styleNav(){
        int primary=SettingsStore.primary(this);
        for(int i=0;i<nav.getChildCount();i++){
            LinearLayout item=(LinearLayout)nav.getChildAt(i);View[] views=(View[])item.getTag();
            boolean selected=i==currentTab;int color=selected?primary:UiKit.resolveSecondaryText(this);
            ((ImageView)views[0]).setColorFilter(color);
            ((TextView)views[1]).setTextColor(color);
            ((TextView)views[1]).setTypeface(Typeface.DEFAULT,selected?Typeface.BOLD:Typeface.NORMAL);
            GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(UiKit.dp(this,18));
            bg.setColor(selected?ColorUtils.setAlphaComponent(primary,28):Color.TRANSPARENT);item.setBackground(bg);
        }
    }

    private void render(){
        content.removeAllViews();styleNav();
        if(currentTab==0)renderHome();
        else if(currentTab==1)renderPrint();
        else if(currentTab==2)renderProfiles();
        else renderSettings();
    }

    private TextView section(String text){
        TextView t=UiKit.heading(this,text,19);t.setPadding(0,UiKit.dp(this,14),0,UiKit.dp(this,5));return t;
    }

    private void renderHome(){
        List<Profile> profiles=SecureStore.load(this);
        long connected=profiles.stream().filter(p->p.active&&p.connectionVerified).count();
        int queued=OutboxStore.load(this).size();

        if(connected==0){
            int[] g=SettingsStore.gradient(this);
            LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
            hero.addView(UiKit.heroTitle(this,"Versand noch nicht eingerichtet",23));
            hero.addView(UiKit.heroBody(this,"Lege ein Profil bei Deutsche Post oder LetterXpress an und prüfe die Verbindung."));
            content.addView(UiKit.hero(this,hero,g[0],g[1]));
        }

        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("Verbindungen",String.valueOf(connected),"verifiziert"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        metrics.addView(new View(this),new LinearLayout.LayoutParams(UiKit.dp(this,12),1));
        metrics.addView(metric("Druckausgang",String.valueOf(queued),queued==1?"PDF":"PDFs"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        content.addView(metrics);

        Profile api=firstLetterXpressApi(profiles);
        if(api!=null){
            LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.addView(section("Letzte LetterXpress-Sendungen"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            MaterialButton refresh=UiKit.tonal(this,"Aktualisieren");refresh.setOnClickListener(v->{recentCache.clear();loadRecent(api,true);});
            titleRow.addView(refresh,new LinearLayout.LayoutParams(UiKit.dp(this,118),UiKit.dp(this,44)));content.addView(titleRow);
            recentContainer=new LinearLayout(this);recentContainer.setOrientation(LinearLayout.VERTICAL);content.addView(recentContainer);
            if(recentCache.isEmpty())loadRecent(api,false);else showRecent(recentCache);
        }
    }

    private Profile firstLetterXpressApi(List<Profile> profiles){
        for(Profile p:profiles)if(p.active&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&Profile.TYPE_LXP_API.equals(p.type))return p;
        return null;
    }

    private void loadRecent(Profile p,boolean force){
        if(historyLoading)return;
        historyLoading=true;
        if(recentContainer!=null){recentContainer.removeAllViews();recentContainer.addView(UiKit.body(this,"Sendungen werden geladen…"));}
        new Thread(()->{
            try{
                List<RecentLetter> jobs=LetterXpressApiClient.recentJobs(p,5);
                runOnUiThread(()->{historyLoading=false;recentCache=jobs;if(currentTab==0&&recentContainer!=null)showRecent(jobs);});
            }catch(Exception e){
                runOnUiThread(()->{historyLoading=false;if(currentTab==0&&recentContainer!=null){recentContainer.removeAllViews();recentContainer.addView(UiKit.body(this,"Sendungshistorie derzeit nicht verfügbar."));}});
            }
        },"lxp-history").start();
    }

    private void showRecent(List<RecentLetter> jobs){
        recentContainer.removeAllViews();
        if(jobs.isEmpty()){recentContainer.addView(UiKit.body(this,"Noch keine Sendungen gefunden."));return;}
        for(RecentLetter j:jobs){
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
            TextView title=UiKit.heading(this,j.filename==null||j.filename.isBlank()?"Auftrag #"+j.id:j.filename,15);
            head.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            head.addView(UiKit.pill(this,statusLabel(j.status),"sent".equalsIgnoreCase(j.status)||"done".equalsIgnoreCase(j.status)));
            box.addView(head);
            String details=(j.address==null?"":j.address)+(j.createdAt==null||j.createdAt.isBlank()?"":"\n"+j.createdAt);
            TextView d=UiKit.body(this,details);d.setTextSize(12);d.setPadding(0,UiKit.dp(this,5),0,0);box.addView(d);
            if(j.amount>0){
                TextView price=UiKit.heading(this,String.format(java.util.Locale.GERMANY,"%.2f €",j.amount+j.vat),15);
                price.setPadding(0,UiKit.dp(this,5),0,0);box.addView(price);
            }
            recentContainer.addView(UiKit.surfaceCard(this,box));
        }
    }

    private String statusLabel(String status){
        if(status==null||status.isBlank())return "Unbekannt";
        switch(status.toLowerCase(java.util.Locale.ROOT)){
            case "sent":return "Versendet";
            case "done":return "Verarbeitet";
            case "queue":return "Warteschlange";
            case "hold":return "Angehalten";
            case "canceled":return "Storniert";
            case "draft":return "Postbox";
            default:return status;
        }
    }

    private MaterialCardView metric(String label,String value,String sub){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        TextView l=UiKit.body(this,label);l.setTextSize(12);box.addView(l);
        TextView v=UiKit.heading(this,value,24);v.setPadding(0,UiKit.dp(this,5),0,0);box.addView(v);
        TextView s=UiKit.body(this,sub);s.setTextSize(12);box.addView(s);
        return UiKit.surfaceCard(this,box);
    }

    private MaterialCardView actionCard(int iconRes,String title,String subtitle,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(this);icon.setImageResource(iconRes);icon.setColorFilter(SettingsStore.primary(this));
        row.addView(icon,new LinearLayout.LayoutParams(UiKit.dp(this,42),UiKit.dp(this,42)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(UiKit.dp(this,10),0,0,0);
        text.addView(UiKit.heading(this,title,16));TextView sub=UiKit.body(this,subtitle);sub.setTextSize(13);text.addView(sub);
        row.addView(text,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView arrow=UiKit.heading(this,"›",26);row.addView(arrow,new LinearLayout.LayoutParams(UiKit.dp(this,28),UiKit.dp(this,42)));
        MaterialCardView card=UiKit.surfaceCard(this,row);card.setClickable(true);card.setOnClickListener(v->action.run());return card;
    }

    private void renderPrint(){
        int queued=OutboxStore.load(this).size();
        content.addView(section("Druckausgang"));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.addView(UiKit.heading(this,queued==0?"Bereit für PDFs":queued+" PDF"+(queued==1?"":"s")+" warten",20));
        TextView help=UiKit.body(this,"Mehrere PDFs verbinden, Reihenfolge festlegen, Vorschau prüfen und erst danach Versandprofil und Kosten vergleichen.");
        help.setPadding(0,UiKit.dp(this,6),0,UiKit.dp(this,12));box.addView(help);
        MaterialButton open=UiKit.primary(this,queued==0?"PDFs hinzufügen":"Druckausgang öffnen");
        open.setOnClickListener(v->startActivity(new Intent(this,OutboxActivity.class)));box.addView(open);
        content.addView(UiKit.surfaceCard(this,box));

        String folder=SettingsStore.outboxFolder(this);
        TextView folderInfo=UiKit.body(this,folder.isBlank()?"Kein Auto-Import-Ordner eingerichtet.":"Auto-Import-Ordner aktiv. PDFs werden beim Öffnen der App übernommen und nach erfolgreichem Versand gelöscht.");
        folderInfo.setTextSize(13);folderInfo.setPadding(0,UiKit.dp(this,8),0,0);content.addView(folderInfo);
    }

    private void renderProfiles(){
        content.addView(section("Profile"));
        for(Profile p:SecureStore.load(this)){
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon=new ImageView(this);icon.setImageResource(Profile.PROVIDER_LETTERXPRESS.equals(p.provider)?R.drawable.ic_provider_lxp:R.drawable.ic_provider_post);
            icon.setContentDescription(Profile.PROVIDER_LETTERXPRESS.equals(p.provider)?"LetterXpress":"Deutsche Post");
            head.addView(icon,new LinearLayout.LayoutParams(UiKit.dp(this,40),UiKit.dp(this,40)));
            TextView name=UiKit.heading(this,p.name,17);name.setPadding(UiKit.dp(this,10),0,0,0);
            head.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            String status=!p.active?"Inaktiv":p.connectionVerified?"Verbunden":"Nicht geprüft";
            head.addView(UiKit.pill(this,status,p.connectionVerified));box.addView(head);
            TextView route=UiKit.body(this,profileSummary(p));route.setPadding(0,UiKit.dp(this,7),0,UiKit.dp(this,8));box.addView(route);
            MaterialButton edit=UiKit.tonal(this,"Bearbeiten & prüfen");edit.setOnClickListener(v->{Intent i=new Intent(this,ProfileEditActivity.class);i.putExtra("profileId",p.id);startActivity(i);});box.addView(edit);
            content.addView(UiKit.surfaceCard(this,box));
        }
        MaterialButton add=UiKit.primary(this,"+ Profil hinzufügen");add.setOnClickListener(v->startActivity(new Intent(this,ProfileEditActivity.class)));
        content.addView(add,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54)));
    }

    private String profileSummary(Profile p){
        String route=Profile.TYPE_IPP.equals(p.type)?"IPP":Profile.TYPE_WEBDAV.equals(p.type)?"WebDAV":Profile.TYPE_LXP_API.equals(p.type)?"API":"SFTP";
        return (Profile.PROVIDER_LETTERXPRESS.equals(p.provider)?"LetterXpress":"Deutsche Post")+" · "+route;
    }

    private void renderSettings(){
        content.addView(section("Darstellung"));
        LinearLayout modeBox=new LinearLayout(this);modeBox.setOrientation(LinearLayout.VERTICAL);
        com.google.android.material.button.MaterialButtonToggleGroup modes=new com.google.android.material.button.MaterialButtonToggleGroup(this);
        modes.setSingleSelection(true);modes.setSelectionRequired(true);
        String[][] md={{"System","system"},{"Hell","light"},{"Dunkel","dark"}};
        for(String[] m:md){
            MaterialButton bt=UiKit.tonal(this,m[0]);bt.setId(View.generateViewId());bt.setTag(m[1]);
            modes.addView(bt,new LinearLayout.LayoutParams(0,UiKit.dp(this,48),1f));
            if(m[1].equals(SettingsStore.appearance(this)))modes.check(bt.getId());
        }
        modes.addOnButtonCheckedListener((g,id,checked)->{if(checked){View v=g.findViewById(id);if(v!=null)SettingsStore.setAppearance(this,String.valueOf(v.getTag()));}});
        modeBox.addView(modes);content.addView(UiKit.surfaceCard(this,modeBox));

        content.addView(section("Druckausgang"));
        LinearLayout folderBox=new LinearLayout(this);folderBox.setOrientation(LinearLayout.VERTICAL);
        TextView folder=UiKit.body(this,SettingsStore.outboxFolder(this).isBlank()?"Kein Ordner gewählt":"Auto-Import ist eingerichtet");
        folderBox.addView(folder);
        TextView explain=UiKit.body(this,"PDFs in diesem Ordner werden beim Start und Öffnen der App automatisch in den Druckausgang übernommen. Nach erfolgreichem Versand löscht die App nur diese automatisch importierten Quelldateien.");
        explain.setTextSize(13);explain.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,10));folderBox.addView(explain);
        MaterialButton choose=UiKit.tonal(this,"Ordner auswählen");choose.setOnClickListener(v->folderPicker.launch(null));folderBox.addView(choose);
        content.addView(UiKit.surfaceCard(this,folderBox));

        content.addView(section("Diagnose"));
        LinearLayout debugBox=new LinearLayout(this);debugBox.setOrientation(LinearLayout.VERTICAL);
        com.google.android.material.materialswitch.MaterialSwitch debug=new com.google.android.material.materialswitch.MaterialSwitch(this);
        debug.setText("Debugmodus");debug.setChecked(SettingsStore.debugMode(this));debug.setOnCheckedChangeListener((b,c)->SettingsStore.setDebugMode(this,c));debugBox.addView(debug);
        TextView dh=UiKit.body(this,"Technische Fehler bleiben sichtbar und werden automatisch in die Zwischenablage kopiert.");dh.setTextSize(13);debugBox.addView(dh);
        content.addView(UiKit.surfaceCard(this,debugBox));

        content.addView(section("Werkzeuge"));
        content.addView(actionCard(R.drawable.ic_nav_profiles,"Versandfeld-Assistent","Adressbereiche am eigenen PDF-Brieflayout festlegen",()->startActivity(new Intent(this,AddressConfigActivity.class))));
        content.addView(actionCard(R.drawable.ic_nav_print,"Android-Druckdienst","Briefversand als Android-Systemdrucker aktivieren",()->startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS))));

        TextView security=UiKit.body(this,"TLS-geschützte Übertragung, Zertifikatsprüfung und verschlüsselte lokale Zugangsdaten.");
        security.setTextSize(12);security.setPadding(0,UiKit.dp(this,14),0,UiKit.dp(this,18));content.addView(security);

        TextView vibe=UiKit.heading(this,"Vibecoded with ❤️",14);vibe.setGravity(Gravity.CENTER);content.addView(vibe);
        TextView github=UiKit.body(this,"github.com/3115a083/epost-helper");github.setGravity(Gravity.CENTER);github.setTextColor(SettingsStore.primary(this));github.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,12));
        github.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/3115a083/epost-helper"))));content.addView(github);
    }
}
