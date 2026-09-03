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
    private boolean recentLoaded=false;
    private String queueCostCache="";
    private boolean queueCostLoading=false;
    private int statsPeriodIndex=0;
    private boolean statsRemoteLoading=false;
    private boolean statsRemoteLoaded=false;
    private boolean statsRemoteAvailable=false;
    private List<RecentLetter> statsRemoteCache=new ArrayList<>();
    private String statsRemoteProfileId="";

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
        DeviceTransferStore.restoreIfNeeded(this);
        DebugProfileManager.ensure(this);
        OutboxStore.reconcilePrepared(this);
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
        queueCostCache="";
        if(content!=null)render();
    }

    @Override protected void onPause(){
        DeviceTransferStore.refresh(this);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out){
        out.putInt("currentTab",currentTab);
        super.onSaveInstanceState(out);
    }

    private void navigateTo(int tab){
        if(tab==currentTab)return;
        tabHistory.push(currentTab);
        currentTab=tab;
        if(tab==0)queueCostCache="";
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
        int[] icons={R.drawable.ic_nav_home,R.drawable.ic_nav_print,R.drawable.ic_nav_send,R.drawable.ic_nav_settings};
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
        int[] g=SettingsStore.gradient(this);
        Profile api=firstLetterXpressApi(profiles);
        Profile statsApi=firstStatsApi(profiles);
        Profile balanceProfile=firstBalanceApi(profiles);
        if(statsApi==null){
            statsRemoteCache.clear();statsRemoteLoaded=false;statsRemoteAvailable=false;statsRemoteProfileId="";
        }else if(!statsApi.id.equals(statsRemoteProfileId)){
            statsRemoteCache.clear();statsRemoteLoaded=false;statsRemoteAvailable=false;statsRemoteProfileId=statsApi.id;
        }

        SendStatsStore.Period[] periods={
                SendStatsStore.Period.THIS_MONTH,
                SendStatsStore.Period.LAST_MONTH,
                SendStatsStore.Period.THIS_YEAR
        };
        String[] periodNames={
                "Laufender Monat",
                "Vormonat",
                "Jahr "+java.time.LocalDate.now().getYear()
        };
        statsPeriodIndex=Math.max(0,Math.min(statsPeriodIndex,2));
        boolean useRemote=statsApi!=null&&statsApi.includeServerHistoryInStats;
        boolean useRemoteData=useRemote&&statsRemoteLoaded&&statsRemoteAvailable;
        SendStatsStore.Summary summary=SendStatsStore.summarize(
                this,periods[statsPeriodIndex],
                useRemoteData?statsRemoteCache:java.util.Collections.emptyList(),
                statsApi==null?"":statsApi.id,useRemoteData);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        LinearLayout statHead=new LinearLayout(this);statHead.setGravity(Gravity.CENTER_VERTICAL);

        ImageView prev=new ImageView(this);prev.setImageResource(R.drawable.ic_chevron_left);prev.setColorFilter(0xFFFFFFFF);
        prev.setContentDescription("Vorherige Statistik");prev.setPadding(UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,8));
        prev.setOnClickListener(v->{statsPeriodIndex=(statsPeriodIndex+2)%3;render();});
        statHead.addView(prev,new LinearLayout.LayoutParams(UiKit.dp(this,42),UiKit.dp(this,42)));

        TextView period=UiKit.heroTitle(this,periodNames[statsPeriodIndex],21);period.setGravity(Gravity.CENTER);
        statHead.addView(period,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));

        ImageView next=new ImageView(this);next.setImageResource(R.drawable.ic_chevron_right);next.setColorFilter(0xFFFFFFFF);
        next.setContentDescription("Nächste Statistik");next.setPadding(UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,8));
        next.setOnClickListener(v->{statsPeriodIndex=(statsPeriodIndex+1)%3;render();});
        statHead.addView(next,new LinearLayout.LayoutParams(UiKit.dp(this,42),UiKit.dp(this,42)));
        hero.addView(statHead);

        LinearLayout statRow=new LinearLayout(this);statRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView count=UiKit.heroTitle(this,String.valueOf(summary.count),28);
        statRow.addView(count);
        TextView countLabel=UiKit.heroBody(this,summary.count==1?" Brief":" Briefe");
        statRow.addView(countLabel);
        View gap=new View(this);statRow.addView(gap,new LinearLayout.LayoutParams(0,1,1f));
        String costText;
        if(summary.knownCostCount==0)costText=summary.count==0?"Kosten: 0,00 €":"Kosten: ?";
        else costText=String.format(java.util.Locale.GERMANY,"Kosten: %.2f €",summary.cost)+(summary.unknownCostCount>0?" + ?":"");
        statRow.addView(UiKit.heroBody(this,costText));
        hero.addView(statRow);

        TextView types=UiKit.heroBody(this,summary.topTypes());types.setTextSize(12);hero.addView(types);
        if(balanceProfile!=null){
            TextView bal=UiKit.heroBody(this,balanceCache.isBlank()?"Guthaben wird geladen…":"Guthaben: "+balanceCache.replace(" EUR"," €"));
            bal.setTextSize(12);bal.setPadding(0,UiKit.dp(this,4),0,0);hero.addView(bal);
        }
        if(useRemote&&!statsRemoteLoaded){
            TextView src=UiKit.heroBody(this,statsRemoteLoading?"LetterXpress-Statistik wird synchronisiert…":"Serverstatistik noch nicht geladen");
            src.setTextSize(11);hero.addView(src);
        }else if(useRemote&&statsRemoteLoaded&&!statsRemoteAvailable){
            TextView src=UiKit.heroBody(this,"Serverstatistik nicht erreichbar · lokale Daten werden verwendet");
            src.setTextSize(11);hero.addView(src);
        }
        content.addView(UiKit.hero(this,hero,g[0],g[1]));

        if(useRemote&&!statsRemoteLoaded&&!statsRemoteLoading)loadStatsRemote(statsApi);
        if(balanceProfile!=null&&balanceCache.isBlank()&&!historyLoading)loadBalanceOnly(balanceProfile);

        if(api!=null){
            LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView title=section("Letzte LetterXpress-Sendungen");
            titleRow.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            MaterialButton refresh=UiKit.tonal(this,"Aktualisieren");
            refresh.setMinWidth(0);refresh.setPadding(UiKit.dp(this,12),0,UiKit.dp(this,12),0);
            refresh.setOnClickListener(v->{
                recentCache.clear();recentLoaded=false;balanceCache="";
                statsRemoteCache.clear();statsRemoteLoaded=false;statsRemoteAvailable=false;
                loadRecent(api,true);
                if(statsApi!=null&&statsApi.includeServerHistoryInStats)loadStatsRemote(statsApi);
            });
            titleRow.addView(refresh,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,UiKit.dp(this,44)));
            content.addView(titleRow);

            recentContainer=new LinearLayout(this);recentContainer.setOrientation(LinearLayout.VERTICAL);content.addView(recentContainer);
            if(!recentLoaded)loadRecent(api,false);else showRecent(recentCache);
        }
    }

    private Profile firstStatsApi(List<Profile> profiles){
        for(Profile p:profiles)
            if(p.active&&p.includeServerHistoryInStats&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&Profile.TYPE_LXP_API.equals(p.type))
                return p;
        return null;
    }

    private void loadStatsRemote(Profile p){
        if(p==null||statsRemoteLoading)return;
        statsRemoteProfileId=p.id;
        statsRemoteLoading=true;
        new Thread(()->{
            try{
                List<RecentLetter> jobs=LetterXpressApiClient.recentJobs(p,1000);
                runOnUiThread(()->{statsRemoteLoading=false;statsRemoteLoaded=true;statsRemoteAvailable=true;statsRemoteCache=jobs;if(currentTab==0)render();});
            }catch(Exception e){
                runOnUiThread(()->{statsRemoteLoading=false;statsRemoteLoaded=true;statsRemoteAvailable=false;statsRemoteCache.clear();if(currentTab==0)render();});
            }
        },"stats-remote").start();
    }

    private void loadBalanceOnly(Profile p){
        new Thread(()->{
            try{
                String balance=LetterXpressApiClient.balance(p);
                runOnUiThread(()->{balanceCache=balance;if(currentTab==0)render();});
            }catch(Exception ignored){}
        },"balance-only").start();
    }

    private Profile firstLetterXpressApi(List<Profile> profiles){
        for(Profile p:profiles)if(p.active&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&Profile.TYPE_LXP_API.equals(p.type))return p;
        return null;
    }

    private Profile firstBalanceApi(List<Profile> profiles){
        for(Profile p:profiles)
            if(p.active&&p.showBalanceOnHome&&Profile.PROVIDER_LETTERXPRESS.equals(p.provider)&&Profile.TYPE_LXP_API.equals(p.type))
                return p;
        return null;
    }

    private void loadQueueCost(){
        if(queueCostLoading)return;
        queueCostLoading=true;
        new Thread(()->{
            double known=0d;
            int unknown=0;
            try{
                for(PreparedJob j:PreparedJobStore.load(this)){
                    Profile p=SecureStore.find(this,j.profileId);
                    if(p==null||DebugProfileManager.isDebug(p))continue;
                    if(Profile.PROVIDER_LETTERXPRESS.equals(p.provider)){
                        int pages=PdfMergeUtil.countPages(new File(j.filePath));
                        double estimate=LetterXpressPriceEstimator.gross(j.options(),pages);
                        if(estimate>=0)known+=estimate; else unknown++;
                    }else{
                        unknown++;
                    }
                }
                String value;
                if(known<=0&&unknown==0)value="0,00 €";
                else if(known>0)value=String.format(java.util.Locale.GERMANY,"≈ %.2f €",known)+(unknown>0?" + ?":"");
                else value="?";
                final String result=value;
                runOnUiThread(()->{
                    queueCostLoading=false;
                    queueCostCache=result;
                    if(currentTab==0)render();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    queueCostLoading=false;
                    queueCostCache="?";
                    if(currentTab==0)render();
                });
            }
        },"queue-cost").start();
    }

    private void loadRecent(Profile p,boolean force){
        if(historyLoading)return;
        historyLoading=true;
        if(recentContainer!=null){recentContainer.removeAllViews();recentContainer.addView(UiKit.body(this,"Sendungen werden geladen…"));}
        new Thread(()->{
            try{
                List<RecentLetter> jobs=LetterXpressApiClient.recentJobs(p,5);
                String balance="";
                Profile balanceApi=firstBalanceApi(SecureStore.load(this));
                if(balanceApi!=null)balance=LetterXpressApiClient.balance(balanceApi);
                final String loadedBalance=balance;
                runOnUiThread(()->{
                    historyLoading=false;recentLoaded=true;recentCache=jobs;balanceCache=loadedBalance;
                    if(currentTab==0){render();}
                });
            }catch(Exception e){
                runOnUiThread(()->{historyLoading=false;recentLoaded=true;if(currentTab==0&&recentContainer!=null){recentContainer.removeAllViews();recentContainer.addView(UiKit.body(this,"Sendungshistorie derzeit nicht verfügbar."));}});
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

    private MaterialCardView metricCompact(String label,String value){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);
        TextView l=UiKit.body(this,label);l.setTextSize(10);l.setGravity(Gravity.CENTER);box.addView(l);
        TextView v=UiKit.heading(this,value,16);v.setGravity(Gravity.CENTER);v.setSingleLine(true);v.setPadding(0,UiKit.dp(this,3),0,0);box.addView(v);
        MaterialCardView card=UiKit.surfaceCard(this,box);
        card.setContentPadding(UiKit.dp(this,7),UiKit.dp(this,9),UiKit.dp(this,7),UiKit.dp(this,9));
        return card;
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
        hero.addView(UiKit.heroTitle(this,"PDFs vorbereiten",23));
        hero.addView(UiKit.heroBody(this,"Auswählen, zusammenführen, Vorschau prüfen, Versandoptionen festlegen und Profilkosten vergleichen."));
        MaterialButton open=UiKit.primary(this,"PDF(s) importieren");
        open.setBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        open.setOnClickListener(v->startActivity(new Intent(this,OutboxActivity.class)));
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52));op.setMargins(0,UiKit.dp(this,12),0,0);hero.addView(open,op);
        content.addView(UiKit.hero(this,hero,g[0],g[1]));

        String folder=SettingsStore.outboxFolder(this);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        info.addView(UiKit.heading(this,"Importordner",16));
        TextView folderInfo=UiKit.body(this,folder.isBlank()?"Kein Importordner eingerichtet.":"Aktiver Ordner: "+folderDisplayName(folder));
        folderInfo.setPadding(0,UiKit.dp(this,5),0,0);info.addView(folderInfo);
        content.addView(UiKit.surfaceCard(this,info));
    }

    private void renderPreparedQueue(){
        List<PreparedJob> jobs=PreparedJobStore.load(this);
        int[] g=SettingsStore.gradient(this);

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.addView(UiKit.heroTitle(this,jobs.isEmpty()?"Ausgang ist leer":jobs.size()+" vorbereitete"+(jobs.size()==1?"r Brief":" Briefe"),23));
        hero.addView(UiKit.heroBody(this,"Vorbereitete Briefe prüfen, zusammenführen und gesammelt oder einzeln versenden."));

        MaterialButton sendAll=UiKit.primary(this,"Alle versenden");
        sendAll.setBackgroundTintList(ColorStateList.valueOf(0x44FFFFFF));
        sendAll.setTextColor(0xFFFFFFFF);
        sendAll.setEnabled(!jobs.isEmpty());
        sendAll.setOnClickListener(v->confirmSendAll(jobs,sendAll));
        LinearLayout.LayoutParams salp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52));
        salp.setMargins(0,UiKit.dp(this,10),0,0);hero.addView(sendAll,salp);

        MaterialButton importNow=UiKit.tonal(this,"Ordner jetzt importieren");
        importNow.setBackgroundTintList(ColorStateList.valueOf(0x22FFFFFF));
        importNow.setTextColor(0xFFFFFFFF);
        importNow.setEnabled(!SettingsStore.outboxFolder(this).isBlank());
        importNow.setOnClickListener(v->importFolderNow(importNow));
        LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,48));
        ilp.setMargins(0,UiKit.dp(this,8),0,0);hero.addView(importNow,ilp);
        content.addView(UiKit.hero(this,hero,g[0],g[1]));

        if(jobs.isEmpty())return;

        java.util.Map<String,List<PreparedJob>> groups=new java.util.HashMap<>();
        for(PreparedJob j:jobs){
            if(j.isMergedGroup()||j.recipientKey==null||j.recipientKey.isBlank())continue;
            String key=preparedGroupKey(j);
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

            if(j.isMergedGroup())renderMergedParts(box,j);

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
            LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,44));
            dlp.setMargins(0,UiKit.dp(this,7),0,0);box.addView(delete,dlp);

            if(!j.isMergedGroup()){
                List<PreparedJob> same=groups.get(preparedGroupKey(j));
                if(same!=null&&same.size()>1&&same.get(0).id.equals(j.id)){
                    MaterialButton merge=UiKit.tonal(this,"Mit "+(same.size()-1)+" weiteren Brief"+(same.size()==2?"":"en")+" zusammenführen");
                    merge.setOnClickListener(v->choosePreparedMergeMode(same,merge));
                    LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,48));
                    mlp.setMargins(0,UiKit.dp(this,7),0,0);box.addView(merge,mlp);
                }
            }

            content.addView(UiKit.surfaceCard(this,box));
        }
    }

    private String preparedGroupKey(PreparedJob j){
        return j.recipientKey+"|"+j.profileId+"|"+j.color+"|"+j.duplex+"|"+j.registered+"|"+j.shipping+"|"+j.addressCorrection;
    }

    private void renderMergedParts(LinearLayout box,PreparedJob merged){
        List<PreparedJob> parts=merged.mergedParts();
        if(parts.isEmpty())return;

        LinearLayout nested=new LinearLayout(this);nested.setOrientation(LinearLayout.VERTICAL);
        nested.setPadding(UiKit.dp(this,8),UiKit.dp(this,4),0,UiKit.dp(this,8));
        for(int i=0;i<parts.size();i++){
            PreparedJob part=parts.get(i);
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);
            int indent=i==0?0:UiKit.dp(this,28);
            row.setPadding(indent,UiKit.dp(this,4),0,UiKit.dp(this,4));
            TextView partTitle=UiKit.heading(this,(i==0?"Hauptbrief · ":"↳ Briefteil · ")+part.name,14);
            row.addView(partTitle);
            if(i>0){
                TextView sub=UiKit.body(this,"Wird hinter dem Hauptbrief versendet");
                sub.setTextSize(11);row.addView(sub);
            }
            nested.addView(row);
        }

        if(merged.duplex){
            com.google.android.material.materialswitch.MaterialSwitch separate=new com.google.android.material.materialswitch.MaterialSwitch(this);
            separate.setText("Briefteile auf getrennten Blättern beginnen");
            separate.setChecked(merged.keepPartsOnSeparateSheets);
            separate.setOnCheckedChangeListener((button,checked)->updateMergedSheetMode(merged,checked,separate));
            nested.addView(separate);
        }

        MaterialButton split=UiKit.tonal(this,"Briefe wieder trennen");
        split.setOnClickListener(v->unmergePrepared(merged,split));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,44));
        slp.setMargins(0,UiKit.dp(this,6),0,0);nested.addView(split,slp);
        box.addView(nested);
    }

    private void confirmSendAll(List<PreparedJob> jobs,MaterialButton button){
        if(jobs==null||jobs.isEmpty())return;
        button.setEnabled(false);button.setText("Kosten werden ermittelt…");
        new Thread(()->{
            double known=0d;int unknown=0;String issue=null;
            try{
                for(PreparedJob job:jobs){
                    Profile p=SecureStore.find(this,job.profileId);
                    if(p==null||!ProfileCompatibility.compatible(p,job.options())){
                        issue="Mindestens ein Brief hat kein kompatibles Versandprofil.";
                        break;
                    }
                    File pdf=PreparedJobStore.ensureFile(this,job);
                    int pages=PdfMergeUtil.countPages(pdf);
                    if(DebugProfileManager.isDebug(p))continue;
                    if(Profile.PROVIDER_LETTERXPRESS.equals(p.provider)){
                        double price=LetterXpressPriceEstimator.gross(job.options(),pages);
                        if(price>=0)known+=price;else unknown++;
                    }else unknown++;
                }
            }catch(Exception e){issue=e.getMessage()==null?"Mindestens eine vorbereitete PDF ist nicht verfügbar.":e.getMessage();}

            double finalKnown=known;int finalUnknown=unknown;String finalIssue=issue;
            runOnUiThread(()->{
                button.setEnabled(true);button.setText("Alle versenden");
                if(finalIssue!=null){
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Alle versenden nicht möglich")
                            .setMessage(finalIssue+" Bitte die betroffenen Briefe zuerst prüfen.")
                            .setPositiveButton("OK",null).show();
                    return;
                }
                String price;
                if(finalKnown<=0&&finalUnknown==0)price="0,00 €";
                else if(finalKnown>0)price=String.format(java.util.Locale.GERMANY,"ca. %.2f €",finalKnown)+(finalUnknown>0?" + unbekannte Kosten":"");
                else price="nicht vollständig ermittelbar";
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Alle Briefe versenden?")
                        .setMessage(jobs.size()+" vorbereitete Briefe werden jetzt versendet.\n\nVoraussichtliche Kosten: "+price)
                        .setNegativeButton("Abbrechen",null)
                        .setPositiveButton("Alle versenden",(d,w)->sendAllPrepared(new ArrayList<>(jobs),button))
                        .show();
            });
        },"queue-estimate").start();
    }

    private void sendAllPrepared(List<PreparedJob> jobs,MaterialButton button){
        button.setEnabled(false);button.setText("Alle werden versendet…");
        new Thread(()->{
            int sent=0,failed=0;
            for(PreparedJob job:jobs){
                try{
                    PreparedJobSender.send(this,job);
                    deletePreparedSources(job);
                    PreparedJobStore.delete(this,job.id);
                    sent++;
                }catch(Exception e){failed++;}
            }
            int finalSent=sent,finalFailed=failed;
            runOnUiThread(()->{
                render();
                Snackbar.make(content,finalFailed==0
                        ?finalSent+" Briefe erfolgreich übergeben."
                        :finalSent+" Briefe versendet, "+finalFailed+" fehlgeschlagen. Fehlgeschlagene Briefe bleiben im Ausgang.",
                        Snackbar.LENGTH_LONG).show();
            });
        },"send-all-prepared").start();
    }

    private void sendPrepared(PreparedJob job,MaterialButton button){
        button.setEnabled(false);button.setText("Wird gesendet…");
        new Thread(()->{
            try{
                PreparedJobSender.send(this,job);
                deletePreparedSources(job);
                PreparedJobStore.delete(this,job.id);
                runOnUiThread(()->{Snackbar.make(content,"Brief erfolgreich übergeben.",Snackbar.LENGTH_LONG).show();render();});
            }catch(Exception e){
                runOnUiThread(()->{button.setEnabled(true);button.setText("Senden");DebugUtil.error(this,button,"Ausgang versenden",e);});
            }
        },"prepared-send").start();
    }

    private void deletePreparedSources(PreparedJob job){
        if(!(job.deleteSourceAfterSend||!job.sourceUris.isEmpty()))return;
        for(String uri:job.sourceUris)try{
            androidx.documentfile.provider.DocumentFile d=androidx.documentfile.provider.DocumentFile.fromSingleUri(this,Uri.parse(uri));
            if(d!=null)d.delete();
        }catch(Exception ignored){}
    }

    private void choosePreparedMergeMode(List<PreparedJob> group,MaterialButton button){
        if(group==null||group.size()<2)return;
        PreparedJob first=group.get(0);
        if(!first.duplex){mergePreparedGroup(group,button,false);return;}
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Briefe zusammenführen")
                .setMessage("Der erste Brief wird zum Hauptbrief. Die weiteren Briefe erscheinen eingerückt darunter. Sollen die Briefteile bei Duplex jeweils auf einem neuen Blatt beginnen?")
                .setNegativeButton("Direkt anhängen",(d,w)->mergePreparedGroup(group,button,false))
                .setPositiveButton("Blätter trennen",(d,w)->mergePreparedGroup(group,button,true))
                .show();
    }

    private List<String> flattenMergedPartJson(List<PreparedJob> group){
        List<String> parts=new ArrayList<>();
        for(PreparedJob j:group){
            if(j.isMergedGroup())parts.addAll(j.mergedPartJson);
            else try{parts.add(j.toJson().toString());}catch(Exception ignored){}
        }
        return parts;
    }

    private void mergePreparedGroup(List<PreparedJob> group,MaterialButton button,boolean keepSheetBoundaries){
        if(group==null||group.size()<2)return;
        button.setEnabled(false);button.setText("Wird zusammengeführt…");
        new Thread(()->{
            File merged=null;
            try{
                List<PreparedJob> actualParts=new ArrayList<>();
                for(String raw:flattenMergedPartJson(group)){
                    try{actualParts.add(PreparedJob.fromJson(new org.json.JSONObject(raw)));}catch(Exception ignored){}
                }
                if(actualParts.size()<2)throw new IllegalStateException("Briefteile konnten nicht geladen werden.");
                for(PreparedJob part:actualParts)PreparedJobStore.ensureFile(this,part);

                PreparedJob first=actualParts.get(0);
                merged=PdfMergeUtil.mergePrepared(this,actualParts,first.duplex&&keepSheetBoundaries);
                PreparedJob combined=new PreparedJob();
                combined.name=first.name+" + "+(actualParts.size()-1)+" weitere";
                combined.profileId=first.profileId;combined.color=first.color;combined.duplex=first.duplex;combined.registered=first.registered;
                combined.c4=first.c4;combined.shipping=first.shipping;combined.addressCorrection=first.addressCorrection;
                combined.sourceSender=new RectF(first.sourceSender);combined.sourceRecipient=new RectF(first.sourceRecipient);
                combined.targetSender=new RectF(first.targetSender);combined.targetRecipient=new RectF(first.targetRecipient);
                combined.recipientKey=first.recipientKey;
                combined.keepPartsOnSeparateSheets=first.duplex&&keepSheetBoundaries;
                for(PreparedJob part:actualParts){
                    try{combined.mergedPartJson.add(part.toJson().toString());}catch(Exception ignored){}
                    combined.inputNames.addAll(part.inputNames);
                    for(String uri:part.sourceUris)if(!combined.sourceUris.contains(uri))combined.sourceUris.add(uri);
                }
                File persisted=PreparedJobStore.persistPdf(this,merged,combined.id);combined.filePath=persisted.getAbsolutePath();

                for(PreparedJob j:group){
                    PreparedJobStore.removeMetadataOnly(this,j.id);
                    if(j.isMergedGroup())try{new File(j.filePath).delete();}catch(Exception ignored){}
                }
                PreparedJobStore.upsert(this,combined);

                File finalMerged=merged;
                runOnUiThread(()->{
                    if(finalMerged!=null)finalMerged.delete();
                    Snackbar.make(content,"Briefe zusammengeführt. Der erste Brief ist der Hauptbrief.",Snackbar.LENGTH_LONG).show();
                    render();
                });
            }catch(Exception e){
                File finalMerged=merged;
                runOnUiThread(()->{
                    if(finalMerged!=null)finalMerged.delete();
                    button.setEnabled(true);button.setText("Zusammenführen");
                    DebugUtil.error(this,button,"Briefe zusammenführen",e);
                });
            }
        },"prepared-merge").start();
    }

    private void updateMergedSheetMode(PreparedJob mergedJob,boolean separate,com.google.android.material.materialswitch.MaterialSwitch toggle){
        toggle.setEnabled(false);
        new Thread(()->{
            File temp=null;
            try{
                List<PreparedJob> parts=mergedJob.mergedParts();
                for(PreparedJob part:parts)PreparedJobStore.ensureFile(this,part);
                temp=PdfMergeUtil.mergePrepared(this,parts,mergedJob.duplex&&separate);
                File persisted=PreparedJobStore.persistPdf(this,temp,mergedJob.id);
                mergedJob.filePath=persisted.getAbsolutePath();
                mergedJob.keepPartsOnSeparateSheets=separate;
                PreparedJobStore.upsert(this,mergedJob);
                runOnUiThread(()->{Snackbar.make(content,"Blatttrennung aktualisiert.",Snackbar.LENGTH_SHORT).show();render();});
            }catch(Exception e){
                File finalTemp=temp;
                runOnUiThread(()->{
                    if(finalTemp!=null)finalTemp.delete();
                    toggle.setEnabled(true);toggle.setChecked(!separate);
                    DebugUtil.error(this,toggle,"Briefteile aktualisieren",e);
                });
            }finally{if(temp!=null)temp.delete();}
        },"update-merged-sheets").start();
    }

    private void unmergePrepared(PreparedJob mergedJob,MaterialButton button){
        button.setEnabled(false);button.setText("Wird getrennt…");
        new Thread(()->{
            try{
                List<PreparedJob> parts=mergedJob.mergedParts();
                if(parts.size()<2)throw new IllegalStateException("Gespeicherte Briefteile fehlen.");
                PreparedJobStore.removeMetadataOnly(this,mergedJob.id);
                try{new File(mergedJob.filePath).delete();}catch(Exception ignored){}
                for(PreparedJob part:parts)PreparedJobStore.upsert(this,part);
                runOnUiThread(()->{Snackbar.make(content,"Briefe wieder getrennt.",Snackbar.LENGTH_LONG).show();render();});
            }catch(Exception e){
                runOnUiThread(()->{button.setEnabled(true);button.setText("Briefe wieder trennen");DebugUtil.error(this,button,"Briefe trennen",e);});
            }
        },"prepared-unmerge").start();
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
                    String root="primary".equalsIgnoreCase(volume)?"Interner Speicher":"Speicher "+volume;
                    return path.isBlank()?root:root+"/"+path;
                }
            }
            String authority=parsed.getAuthority();
            String treeId=null;
            try{treeId=android.provider.DocumentsContract.getTreeDocumentId(parsed);}catch(Exception ignored){}
            if(treeId!=null&&!treeId.isBlank()){
                String name=treeId;
                int slash=Math.max(name.lastIndexOf('/'),name.lastIndexOf(':'));
                if(slash>=0&&slash+1<name.length())name=name.substring(slash+1);
                if(!name.isBlank())return (authority==null||authority.isBlank()?"Dokumentordner":authority)+" · "+name;
            }
            if(authority!=null&&!authority.isBlank())return "Dokumentordner · "+authority;
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

    private void checkUpdates(TextView anchor){
        anchor.setText("Updateprüfung läuft…");
        new Thread(()->{
            try{
                UpdateChecker.Result result=UpdateChecker.check();
                runOnUiThread(()->{
                    anchor.setText("Build "+BuildConfig.VERSION_NAME+" · "+BuildConfig.VERSION_CODE+" · Auf Updates prüfen");
                    if(result.updateAvailable){
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("Update verfügbar")
                                .setMessage(result.message)
                                .setNegativeButton("Später",null)
                                .setPositiveButton("GitHub öffnen",(d,w)->{
                                    if(result.url!=null&&!result.url.isBlank())
                                        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(result.url)));
                                }).show();
                    }else{
                        Snackbar.make(content,result.message,Snackbar.LENGTH_LONG).show();
                    }
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    anchor.setText("Build "+BuildConfig.VERSION_NAME+" · "+BuildConfig.VERSION_CODE+" · Auf Updates prüfen");
                    DebugUtil.error(this,anchor,"Updateprüfung",e);
                });
            }
        },"update-check").start();
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
        TextView folder=UiKit.mono(this,folderUri.isBlank()?"PDF-Ordner auswählen. Die App durchsucht ihn nur, wenn du im Ausgang auf Importieren tippst.":folderDisplayName(folderUri));
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
        content.addView(actionCard(R.drawable.ic_nav_settings,
                "Export / Import & Gerätewechsel",
                "Passwortgeschützte Sicherung von Einstellungen und Statistiken sowie portable Geräteübertragung ohne API-Keys",
                ()->startActivity(new Intent(this,BackupActivity.class))));

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

        TextView build=UiKit.body(this,"Build "+BuildConfig.VERSION_NAME+" · "+BuildConfig.VERSION_CODE+" · Auf Updates prüfen");
        build.setGravity(Gravity.CENTER);
        build.setTextSize(11);
        build.setTextColor(SettingsStore.primary(this));
        build.setPadding(0,0,0,UiKit.dp(this,18));
        build.setOnClickListener(v->checkUpdates(build));
        content.addView(build);
    }

}