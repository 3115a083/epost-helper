package de.eposthelper.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.RectF;
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

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private LinearLayout content,nav,recentContainer;
    private int currentTab=0;
    private final ArrayDeque<Integer> tabHistory=new ArrayDeque<>();
    private long lastBackAt=0L;
    private boolean historyLoading=false;
    private int vibeTapCount=0;
    private long vibeTapWindowStart=0L;
    private LinearLayout hiddenDebugBox;
    private List<RecentLetter> recentCache=new ArrayList<>();
    private String balanceCache="";

    private final ActivityResultLauncher<Uri> folderPicker=registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),uri->{
                if(uri==null)return;
                try{
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }catch(Exception ignored){}
                SettingsStore.setOutboxFolder(this,uri.toString());
                if(currentTab==3)render();
                Snackbar.make(content,"Ordner gespeichert. Import erfolgt nur noch manuell über den Ausgang.",Snackbar.LENGTH_SHORT).show();
            });

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        SettingsStore.applyDynamicColors(this);
        DebugProfileManager.ensure(this);
        if(b!=null)currentTab=b.getInt("currentTab",0);
        Uri incoming=getIntent().getData();
        if(Intent.ACTION_VIEW.equals(getIntent().getAction())&&incoming!=null){
            try{getContentResolver().takePersistableUriPermission(incoming,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            OutboxStore.add(this,incoming,incoming.getLastPathSegment(),false);
            currentTab=1;
        }
        buildShell();
        setupBackNavigation();
        render();
    }

    @Override protected void onResume(){
        super.onResume();
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
        String[] labels={"Start","Drucken","Ausgang","Einstellungen"};
        int[] icons={R.drawable.ic_nav_home,R.drawable.ic_nav_print,R.drawable.ic_nav_outbox,R.drawable.ic_nav_settings};
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
        else if(currentTab==2)renderPreparedQueue();
        else renderSettings();
    }

    private TextView section(String text){
        TextView t=UiKit.heading(this,text,19);t.setPadding(0,UiKit.dp(this,14),0,UiKit.dp(this,5));return t;
    }

    private void renderHome(){
        List<Profile> profiles=SecureStore.load(this);
        long connected=profiles.stream().filter(p->p.active&&p.connectionVerified&&!DebugProfileManager.isDebug(p)).count();
        int queued=OutboxStore.load(this).size();
        int prepared=PreparedJobStore.load(this).size();
        int[] g=SettingsStore.gradient(this);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.addView(UiKit.heroTitle(this,prepared>0?prepared+" vorbereitete"+(prepared==1?"r Brief":" Briefe"):(queued>0?queued+" PDF"+(queued==1?" wartet":"s warten"):"Bereit zum Briefversand"),24));
        String heroText=connected==0?"Noch kein verifiziertes Versandprofil.":connected+" verifizierte"+(connected==1?"s Profil":" Profile")+" · "+prepared+" im Ausgang";
        hero.addView(UiKit.heroBody(this,heroText));
        MaterialButton heroAction=UiKit.primary(this,prepared>0?"Ausgang öffnen":(queued>0?"PDF-Eingang öffnen":"PDFs hinzufügen"));
        heroAction.setBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        heroAction.setOnClickListener(v->{if(prepared>0)navigateTo(2);else startActivity(new Intent(this,OutboxActivity.class));});
        LinearLayout.LayoutParams hap=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,50));hap.setMargins(0,UiKit.dp(this,12),0,0);hero.addView(heroAction,hap);
        content.addView(UiKit.hero(this,hero,g[0],g[1]));

        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(metric("Verbindungen",String.valueOf(connected),"verifiziert"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        metrics.addView(new View(this),new LinearLayout.LayoutParams(UiKit.dp(this,12),1));
        metrics.addView(metric("Ausgang",String.valueOf(prepared),prepared==1?"Brief":"Briefe"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        content.addView(metrics);

        Profile api=firstLetterXpressApi(profiles);
        if(api!=null){
            LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView title=section("Letzte LetterXpress-Sendungen");
            titleRow.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            MaterialButton refresh=UiKit.tonal(this,"Aktualisieren");
            refresh.setMinWidth(0);refresh.setPadding(UiKit.dp(this,12),0,UiKit.dp(this,12),0);
            refresh.setOnClickListener(v->{recentCache.clear();balanceCache="";loadRecent(api,true);});
            titleRow.addView(refresh,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,UiKit.dp(this,44)));
            content.addView(titleRow);

            LinearLayout balanceCard=new LinearLayout(this);balanceCard.setOrientation(LinearLayout.VERTICAL);
            balanceCard.addView(UiKit.body(this,"LetterXpress Guthaben"));
            TextView balanceValue=UiKit.heading(this,balanceCache.isBlank()?"Wird geladen…":balanceCache,22);
            balanceValue.setTag("balanceValue");
            balanceCard.addView(balanceValue);
            content.addView(UiKit.surfaceCard(this,balanceCard));

            recentContainer=new LinearLayout(this);recentContainer.setOrientation(LinearLayout.VERTICAL);content.addView(recentContainer);
            if(balanceCache.isBlank())loadRecent(api,false);else showRecent(recentCache);
        }

        boolean hasPost=profiles.stream().anyMatch(p->p.active&&Profile.PROVIDER_POST.equals(p.provider));
        if(hasPost){
            TextView note=UiKit.body(this,"Deutsche Post: Das E-POST-Journal ist derzeit nur im Geschäftskundenportal dokumentiert. Es gibt keinen veröffentlichten Journal-Endpunkt für eine sichere gemischte In-App-Historie.");
            note.setTextSize(12);note.setPadding(0,UiKit.dp(this,12),0,0);content.addView(note);
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
                String balance=LetterXpressApiClient.balance(p);
                runOnUiThread(()->{
                    historyLoading=false;recentCache=jobs;balanceCache=balance;
                    if(currentTab==0){render();}
                });
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
            ImageView providerIcon=new ImageView(this);providerIcon.setImageResource(R.drawable.ic_provider_lxp);providerIcon.setContentDescription("LetterXpress");
            head.addView(providerIcon,new LinearLayout.LayoutParams(UiKit.dp(this,30),UiKit.dp(this,30)));
            TextView title=UiKit.heading(this,j.filename==null||j.filename.isBlank()?"Auftrag #"+j.id:j.filename,15);
            title.setPadding(UiKit.dp(this,8),0,0,0);
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
        int[] g=SettingsStore.gradient(this);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.addView(UiKit.heroTitle(this,queued==0?"PDFs für den Versand sammeln":queued+" PDF"+(queued==1?" im PDF-Eingang":"s im PDF-Eingang"),23));
        hero.addView(UiKit.heroBody(this,"Auswählen, zusammenführen, Vorschau prüfen, Versandoptionen festlegen und Profilkosten vergleichen."));
        MaterialButton open=UiKit.primary(this,queued==0?"PDFs hinzufügen":"PDF-Eingang bearbeiten");
        open.setBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        open.setOnClickListener(v->startActivity(new Intent(this,OutboxActivity.class)));
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52));op.setMargins(0,UiKit.dp(this,12),0,0);hero.addView(open,op);
        content.addView(UiKit.hero(this,hero,g[0],g[1]));

        String folder=SettingsStore.outboxFolder(this);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        info.addView(UiKit.heading(this,"Automatischer Import",16));
        TextView folderInfo=UiKit.body(this,folder.isBlank()?"Kein Importordner eingerichtet.":"Aktiver Ordner: "+folderDisplayName(folder));
        folderInfo.setPadding(0,UiKit.dp(this,5),0,0);info.addView(folderInfo);
        content.addView(UiKit.surfaceCard(this,info));
    }

    private void renderPreparedQueue(){
        List<PreparedJob> jobs=PreparedJobStore.load(this);
        int[] g=SettingsStore.gradient(this);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.addView(UiKit.heroTitle(this,jobs.isEmpty()?"Ausgang ist leer":jobs.size()+" vorbereitete"+(jobs.size()==1?"r Brief":" Briefe"),23));
        hero.addView(UiKit.heroBody(this,"Vorbereitete Briefe bearbeiten, zusammenführen, löschen oder direkt versenden."));
        MaterialButton add=UiKit.primary(this,"Neuen Brief vorbereiten");
        add.setBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        add.setOnClickListener(v->startActivity(new Intent(this,OutboxActivity.class)));
        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,50));alp.setMargins(0,UiKit.dp(this,10),0,0);hero.addView(add,alp);

        MaterialButton importNow=UiKit.tonal(this,"Ordner jetzt importieren");
        importNow.setBackgroundTintList(ColorStateList.valueOf(0x22FFFFFF));
        importNow.setTextColor(0xFFFFFFFF);
        importNow.setEnabled(!SettingsStore.outboxFolder(this).isBlank());
        importNow.setOnClickListener(v->importFolderNow(importNow));
        LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,48));ilp.setMargins(0,UiKit.dp(this,8),0,0);hero.addView(importNow,ilp);

        content.addView(UiKit.hero(this,hero,g[0],g[1]));

        if(jobs.isEmpty())return;

        java.util.Map<String,List<PreparedJob>> groups=new java.util.HashMap<>();
        for(PreparedJob j:jobs){
            if(j.recipientKey==null||j.recipientKey.isBlank())continue;
            String key=j.recipientKey+"|"+j.profileId+"|"+j.color+"|"+j.duplex+"|"+j.registered+"|"+j.shipping+"|"+j.addressCorrection;
            groups.computeIfAbsent(key,k->new ArrayList<>()).add(j);
        }

        for(PreparedJob j:jobs){
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
            Profile p=SecureStore.find(this,j.profileId);
            int icon=DebugProfileManager.isDebug(p)?R.drawable.ic_provider_debug:
                    p!=null&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)?R.drawable.ic_provider_lxp:R.drawable.ic_provider_post;
            ImageView logo=new ImageView(this);logo.setImageResource(icon);
            head.addView(logo,new LinearLayout.LayoutParams(UiKit.dp(this,38),UiKit.dp(this,38)));
            TextView title=UiKit.heading(this,j.name,16);title.setPadding(UiKit.dp(this,10),0,0,0);
            head.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            box.addView(head);

            String opts=(j.color?"Farbe":"SW")+" · "+(j.duplex?"Duplex":"Einseitig")+" · "+j.registered+" · "+("international".equals(j.shipping)?"International":"National")+(j.addressCorrection?" · Korrektur":"");
            TextView meta=UiKit.body(this,opts);meta.setTextSize(12);meta.setPadding(0,UiKit.dp(this,6),0,UiKit.dp(this,8));box.addView(meta);

            LinearLayout actions=new LinearLayout(this);
            MaterialButton edit=UiKit.tonal(this,"Bearbeiten");
            edit.setOnClickListener(v->{Intent i=new Intent(this,OutboxActivity.class);i.putExtra(OutboxActivity.EXTRA_PREPARED_ID,j.id);startActivity(i);});
            actions.addView(edit,new LinearLayout.LayoutParams(0,UiKit.dp(this,46),1f));
            actions.addView(new View(this),new LinearLayout.LayoutParams(UiKit.dp(this,8),1));
            boolean ready=p!=null&&ProfileCompatibility.compatible(p,j.options());
            MaterialButton send=UiKit.primary(this,ready?"Senden":"Profil wählen");
            if(ready)send.setOnClickListener(v->sendPrepared(j,send));
            else send.setOnClickListener(v->{Intent i=new Intent(this,OutboxActivity.class);i.putExtra(OutboxActivity.EXTRA_PREPARED_ID,j.id);startActivity(i);});
            actions.addView(send,new LinearLayout.LayoutParams(0,UiKit.dp(this,46),1f));
            box.addView(actions);

            MaterialButton delete=UiKit.tonal(this,"Löschen");delete.setTextColor(0xFFB3261E);
            delete.setOnClickListener(v->{PreparedJobStore.delete(this,j.id);render();});
            LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,44));dlp.setMargins(0,UiKit.dp(this,7),0,0);box.addView(delete,dlp);

            String key=j.recipientKey+"|"+j.profileId+"|"+j.color+"|"+j.duplex+"|"+j.registered+"|"+j.shipping+"|"+j.addressCorrection;
            List<PreparedJob> same=groups.get(key);
            if(same!=null&&same.size()>1&&same.get(0).id.equals(j.id)){
                MaterialButton merge=UiKit.tonal(this,"Briefe an gleichen Empfänger zusammenführen ("+same.size()+")");
                merge.setOnClickListener(v->mergePreparedGroup(same,merge));
                LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,48));mlp.setMargins(0,UiKit.dp(this,7),0,0);box.addView(merge,mlp);
            }

            content.addView(UiKit.surfaceCard(this,box));
        }
    }

    private void sendPrepared(PreparedJob job,MaterialButton button){
        button.setEnabled(false);button.setText("Wird gesendet…");
        new Thread(()->{
            try{
                PreparedJobSender.send(this,job);
                if(job.deleteSourceAfterSend||!job.sourceUris.isEmpty()){
                    for(String uri:job.sourceUris)try{
                        androidx.documentfile.provider.DocumentFile d=androidx.documentfile.provider.DocumentFile.fromSingleUri(this,Uri.parse(uri));
                        if(d!=null)d.delete();
                    }catch(Exception ignored){}
                }
                PreparedJobStore.delete(this,job.id);
                runOnUiThread(()->{Snackbar.make(content,"Brief erfolgreich übergeben.",Snackbar.LENGTH_LONG).show();render();});
            }catch(Exception e){
                runOnUiThread(()->{button.setEnabled(true);button.setText("Senden");DebugUtil.error(this,button,"Ausgang versenden",e);});
            }
        },"prepared-send").start();
    }

    private void mergePreparedGroup(List<PreparedJob> group,MaterialButton button){
        if(group==null||group.size()<2)return;
        button.setEnabled(false);button.setText("Wird zusammengeführt…");
        new Thread(()->{
            File merged=null;
            try{
                PreparedJob first=group.get(0);
                merged=PdfMergeUtil.mergePrepared(this,group,first.duplex);
                PreparedJob combined=new PreparedJob();
                combined.name=first.name+" + "+(group.size()-1)+" weitere";
                combined.profileId=first.profileId;combined.color=first.color;combined.duplex=first.duplex;combined.registered=first.registered;
                combined.c4=first.c4;combined.shipping=first.shipping;combined.addressCorrection=first.addressCorrection;
                combined.sourceSender=new RectF(first.sourceSender);combined.sourceRecipient=new RectF(first.sourceRecipient);
                combined.targetSender=new RectF(first.targetSender);combined.targetRecipient=new RectF(first.targetRecipient);
                combined.recipientKey=first.recipientKey;
                for(PreparedJob j:group){combined.inputNames.addAll(j.inputNames);combined.sourceUris.addAll(j.sourceUris);}
                File persisted=PreparedJobStore.persistPdf(this,merged,combined.id);combined.filePath=persisted.getAbsolutePath();
                PreparedJobStore.upsert(this,combined);
                for(PreparedJob j:group)PreparedJobStore.delete(this,j.id);
                File finalMerged=merged;
                runOnUiThread(()->{if(finalMerged!=null)finalMerged.delete();Snackbar.make(content,"Briefe zusammengeführt.",Snackbar.LENGTH_LONG).show();render();});
            }catch(Exception e){
                File finalMerged=merged;
                runOnUiThread(()->{if(finalMerged!=null)finalMerged.delete();button.setEnabled(true);button.setText("Zusammenführen");DebugUtil.error(this,button,"Briefe zusammenführen",e);});
            }
        },"prepared-merge").start();
    }

    private void renderProfileSettings(){
        for(Profile p:SecureStore.load(this)){
            if(DebugProfileManager.isDebug(p))continue;
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

    private String folderDisplayName(String uri){
        if(uri==null||uri.isBlank())return "";
        try{
            Uri parsed=Uri.parse(uri);
            if("com.android.externalstorage.documents".equals(parsed.getAuthority())){
                String id=android.provider.DocumentsContract.getTreeDocumentId(parsed);
                if(id!=null){
                    int colon=id.indexOf(':');
                    String volume=colon>=0?id.substring(0,colon):id;
                    String path=colon>=0?id.substring(colon+1):"";
                    String root="primary".equalsIgnoreCase(volume)?"Interner Speicher":volume;
                    return path.isBlank()?root:root+"/"+path;
                }
            }
            androidx.documentfile.provider.DocumentFile f=androidx.documentfile.provider.DocumentFile.fromTreeUri(this,parsed);
            if(f!=null&&f.getName()!=null&&!f.getName().isBlank())return f.getName();
        }catch(Exception ignored){}
        return "Ausgewählter Dokumentordner";
    }

    private void importFolderNow(MaterialButton button){
        if(SettingsStore.outboxFolder(this).isBlank()){
            Snackbar.make(content,"Bitte zuerst einen Importordner wählen.",Snackbar.LENGTH_SHORT).show();
            return;
        }
        button.setEnabled(false);
        button.setText("Import läuft…");
        new Thread(()->{
            try{
                int input=OutboxStore.importFolder(this);
                int prepared=AutoFolderPresets.importPrepared(this);
                runOnUiThread(()->{
                    button.setEnabled(true);
                    button.setText("Ordner jetzt importieren");
                    Snackbar.make(content,(input+prepared)>0?(input+prepared)+" PDF(s) importiert.":"Keine neuen PDFs gefunden.",Snackbar.LENGTH_LONG).show();
                    if(currentTab==2)render();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    button.setEnabled(true);
                    button.setText("Ordner jetzt importieren");
                    DebugUtil.error(this,button,"Ordnerimport",e);
                });
            }
        },"manual-folder-import").start();
    }

    private void handleVibeTap(){
        long now=System.currentTimeMillis();
        if(now-vibeTapWindowStart>4000){
            vibeTapWindowStart=now;
            vibeTapCount=0;
        }
        vibeTapCount++;
        int remaining=5-vibeTapCount;
        if(remaining>0){
            Snackbar.make(content,remaining+" weitere"+(remaining==1?"s Tippen":" Tipps")+" bis zum Debugmodus.",Snackbar.LENGTH_SHORT).show();
            return;
        }

        vibeTapCount=0;
        vibeTapWindowStart=0L;
        DebugProfileManager.setEnabled(this,true);
        if(hiddenDebugBox!=null)hiddenDebugBox.setVisibility(View.VISIBLE);
        Snackbar.make(content,"Debugmodus aktiviert. Lokales Debug-Druckprofil wurde eingerichtet.",Snackbar.LENGTH_LONG).show();
    }

    private void importFoldersNow(MaterialButton button){
        String folder=SettingsStore.outboxFolder(this);
        if(folder==null||folder.isBlank()){
            Snackbar.make(content,"Bitte zuerst einen Importordner auswählen.",Snackbar.LENGTH_SHORT).show();
            return;
        }
        button.setEnabled(false);
        button.setText("Import läuft…");
        new Thread(()->{
            int raw=OutboxStore.importFolder(this);
            int prepared=AutoFolderPresets.importPrepared(this);
            runOnUiThread(()->{
                button.setEnabled(true);
                button.setText("Ordner jetzt importieren");
                Snackbar.make(content,(raw+prepared)>0?(raw+prepared)+" PDF(s) importiert.":"Keine neuen PDFs gefunden.",Snackbar.LENGTH_LONG).show();
                if(currentTab==2)render();
            });
        },"manual-folder-import").start();
    }

    private void renderSettings(){
        content.addView(section("Darstellung"));

        LinearLayout modeBox=new LinearLayout(this);modeBox.setOrientation(LinearLayout.VERTICAL);
        modeBox.addView(UiKit.heading(this,"Erscheinungsbild",17));
        com.google.android.material.button.MaterialButtonToggleGroup modes=new com.google.android.material.button.MaterialButtonToggleGroup(this);
        modes.setSingleSelection(true);modes.setSelectionRequired(true);
        String[][] md={{"System","system"},{"Hell","light"},{"Dunkel","dark"}};
        for(String[] m:md){
            MaterialButton bt=UiKit.tonal(this,m[0]);bt.setId(View.generateViewId());bt.setTag(m[1]);
            modes.addView(bt,new LinearLayout.LayoutParams(0,UiKit.dp(this,48),1f));
            if(m[1].equals(SettingsStore.appearance(this)))modes.check(bt.getId());
        }
        modes.addOnButtonCheckedListener((group,id,checked)->{
            if(!checked)return;
            View v=group.findViewById(id);
            if(v!=null&&!String.valueOf(v.getTag()).equals(SettingsStore.appearance(this))){
                SettingsStore.setAppearance(this,String.valueOf(v.getTag()));
            }
        });
        modeBox.addView(modes);
        content.addView(UiKit.surfaceCard(this,modeBox));

        LinearLayout paletteBox=new LinearLayout(this);paletteBox.setOrientation(LinearLayout.VERTICAL);
        paletteBox.addView(UiKit.heading(this,"Farbthema",17));
        TextView paletteHelp=UiKit.body(this,"Material You übernimmt die Akzentfarben des Systems. Alternativ stehen feste, auf Light und Dark Mode abgestimmte Paletten bereit.");
        paletteHelp.setTextSize(13);paletteHelp.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,8));paletteBox.addView(paletteHelp);
        com.google.android.material.chip.ChipGroup chips=new com.google.android.material.chip.ChipGroup(this);
        chips.setSingleSelection(true);chips.setSelectionRequired(true);
        String[][] palettes={{"Material You","material_you"},{"Ocean","ocean"},{"Forest","forest"},{"Sunset","sunset"},{"Aurora","aurora"},{"Lavender","lavender"},{"Rose","rose"},{"Sand","sand"},{"Graphite","graphite"}};
        for(String[] p:palettes){
            com.google.android.material.chip.Chip chip=new com.google.android.material.chip.Chip(this);
            chip.setText(p[0]);chip.setTag(p[1]);chip.setCheckable(true);chip.setId(View.generateViewId());
            chip.setChecked(p[1].equals(SettingsStore.palette(this)));
            chip.setChipCornerRadius(UiKit.dp(this,18));
            chip.setOnCheckedChangeListener((button,checked)->{
                if(checked&&!String.valueOf(button.getTag()).equals(SettingsStore.palette(this))){
                    SettingsStore.setPalette(this,String.valueOf(button.getTag()));
                    recreate();
                }
            });
            chips.addView(chip);
        }
        paletteBox.addView(chips);content.addView(UiKit.surfaceCard(this,paletteBox));

        content.addView(section("Druckausgang"));
        LinearLayout folderBox=new LinearLayout(this);folderBox.setOrientation(LinearLayout.VERTICAL);
        String folderUri=SettingsStore.outboxFolder(this);
        folderBox.addView(UiKit.heading(this,folderUri.isBlank()?"Kein Importordner":"Aktueller Importordner",16));
        TextView folder=UiKit.mono(this,folderUri.isBlank()?"PDF-Ordner auswählen, um Dateien automatisch in den Druckausgang zu übernehmen.":folderDisplayName(folderUri));
        folder.setPadding(0,UiKit.dp(this,6),0,UiKit.dp(this,10));folderBox.addView(folder);
        MaterialButton choose=UiKit.tonal(this,"Ordner auswählen");choose.setOnClickListener(v->folderPicker.launch(null));folderBox.addView(choose);
        MaterialButton presets=UiKit.tonal(this,"Unterordner für Druckoptionen anlegen");
        presets.setEnabled(!folderUri.isBlank());
        presets.setOnClickListener(v->{
            presets.setEnabled(false);
            presets.setText("Ordner werden angelegt…");
            new Thread(()->{
                try{
                    int created=AutoFolderPresets.createFolders(this);
                    runOnUiThread(()->{
                        presets.setEnabled(true);
                        presets.setText("Unterordner für Druckoptionen anlegen");
                        Snackbar.make(content,created>0?created+" Optionsordner angelegt.":"Optionsordner sind bereits vorhanden.",Snackbar.LENGTH_LONG).show();
                    });
                }catch(Exception e){
                    runOnUiThread(()->{
                        presets.setEnabled(true);
                        presets.setText("Unterordner für Druckoptionen anlegen");
                        DebugUtil.error(this,presets,"Optionsordner",e);
                    });
                }
            },"create-option-folders").start();
        });
        LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,48));plp.setMargins(0,UiKit.dp(this,8),0,0);folderBox.addView(presets,plp);
        TextView presetHelp=UiKit.body(this,"Unterordner wie SW_einseitig_korrektur, Farbe_beidseitig, International oder Einschreiben werden beim Import automatisch als vorbereitete Ausgangsaufträge erkannt. Der debug-Ordner wird nie importiert.");
        presetHelp.setTextSize(12);presetHelp.setPadding(0,UiKit.dp(this,7),0,0);folderBox.addView(presetHelp);
        content.addView(UiKit.surfaceCard(this,folderBox));

        content.addView(section("Versandprofile"));
        renderProfileSettings();

        content.addView(section("Werkzeuge"));
        content.addView(actionCard(R.drawable.ic_nav_profiles,"Versandfeld-Assistent","Adressbereiche am eigenen PDF-Brieflayout festlegen",()->startActivity(new Intent(this,AddressConfigActivity.class))));
        content.addView(actionCard(R.drawable.ic_nav_print,"Android-Druckdienst","Briefversand als Android-Systemdrucker aktivieren",()->startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS))));

        TextView security=UiKit.body(this,"TLS-geschützte Übertragung, Zertifikatsprüfung und verschlüsselte lokale Zugangsdaten.");
        security.setTextSize(12);security.setPadding(0,UiKit.dp(this,14),0,UiKit.dp(this,18));content.addView(security);

        hiddenDebugBox=new LinearLayout(this);
        hiddenDebugBox.setOrientation(LinearLayout.VERTICAL);
        hiddenDebugBox.setVisibility(SettingsStore.debugMode(this)?View.VISIBLE:View.GONE);
        TextView debugTitle=section("Debug");
        hiddenDebugBox.addView(debugTitle);

        LinearLayout debugBody=new LinearLayout(this);debugBody.setOrientation(LinearLayout.VERTICAL);
        com.google.android.material.materialswitch.MaterialSwitch debug=new com.google.android.material.materialswitch.MaterialSwitch(this);
        debug.setText("Debugmodus");
        debug.setChecked(SettingsStore.debugMode(this));
        debug.setOnCheckedChangeListener((button,checked)->{
            DebugProfileManager.setEnabled(this,checked);
            if(!checked){
                hiddenDebugBox.setVisibility(View.GONE);
                Snackbar.make(content,"Debugmodus deaktiviert.",Snackbar.LENGTH_SHORT).show();
            }
        });
        debugBody.addView(debug);
        TextView dh=UiKit.body(this,"Aktiviert technische Fehlerdetails und das lokale Debug-Druckprofil. Testdrucke werden im Unterordner „debug“ des Standardordners als PDF plus Textdatei gespeichert.");
        dh.setTextSize(13);debugBody.addView(dh);
        hiddenDebugBox.addView(UiKit.surfaceCard(this,debugBody));
        content.addView(hiddenDebugBox);

        View footerGap=new View(this);
        content.addView(footerGap,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,36)));

        TextView vibe=UiKit.heading(this,"Vibecoded with ❤️",14);
        vibe.setGravity(Gravity.CENTER);
        vibe.setAlpha(0.82f);
        vibe.setOnClickListener(v->handleVibeTap());
        content.addView(vibe);
        TextView github=UiKit.body(this,"github.com/3115a083/epost-helper");
        github.setGravity(Gravity.CENTER);
        github.setTextColor(SettingsStore.primary(this));
        github.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,28));
        github.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/3115a083/epost-helper"))));
        content.addView(github);
    }

}