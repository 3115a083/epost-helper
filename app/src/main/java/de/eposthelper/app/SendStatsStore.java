package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class SendStatsStore {
    private static final String PREF="send_stats";
    private static final String KEY="events";
    private SendStatsStore(){}

    public static synchronized List<SendStat> load(Context c){
        ArrayList<SendStat> out=new ArrayList<>();
        String raw=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"[]");
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++)out.add(SendStat.fromJson(a.getJSONObject(i)));
        }catch(Exception ignored){}
        return out;
    }

    public static synchronized void save(Context c,List<SendStat> stats){
        try{
            JSONArray a=new JSONArray();
            for(SendStat s:stats)a.put(s.toJson());
            c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply();
        }catch(Exception ignored){}
    }

    public static synchronized void record(Context c,Profile p,Uri pdf,JobOptions o){
        if(p==null||o==null||DebugProfileManager.isDebug(p))return;
        SendStat s=new SendStat();
        s.profileId=p.id;s.provider=p.provider;s.registered=o.registered;s.color=o.color;s.duplex=o.duplex;s.shipping=o.shipping;
        s.pages=PdfMergeUtil.countPages(c,pdf);
        if(Profile.PROVIDER_LETTERXPRESS.equals(p.provider)){
            s.cost=LetterXpressPriceEstimator.gross(o,s.pages);
        }
        List<SendStat> all=load(c);
        all.add(s);
        // Keep five years max to prevent unbounded prefs growth.
        long cutoff=System.currentTimeMillis()-5L*366L*24L*60L*60L*1000L;
        all.removeIf(x->x.timestamp<cutoff);
        save(c,all);
        DeviceTransferStore.refresh(c);
    }

    public static Summary summarize(Context c,Period period,List<RecentLetter> remote,String remoteProfileId,boolean useRemote){
        long[] bounds=bounds(period);
        Summary out=new Summary();

        for(SendStat s:load(c)){
            if(s.timestamp<bounds[0]||s.timestamp>=bounds[1])continue;
            if(useRemote&&Profile.PROVIDER_LETTERXPRESS.equals(s.provider)&&remoteProfileId.equals(s.profileId))continue;
            out.add(s.registered,s.color,s.duplex,s.cost);
        }

        if(useRemote&&remote!=null){
            for(RecentLetter r:remote){
                long ts=parseRemoteTime(r.createdAt);
                if(ts<bounds[0]||ts>=bounds[1])continue;
                out.count++;
                double value=r.amount+r.vat;
                if(value>0){out.cost+=value;out.knownCostCount++;}
                String type=(r.status==null||r.status.isBlank())?"LetterXpress":r.status;
                out.typeCounts.put(type,out.typeCounts.getOrDefault(type,0)+1);
            }
        }
        return out;
    }

    private static long[] bounds(Period p){
        ZoneId zone=ZoneId.systemDefault();
        LocalDate today=LocalDate.now(zone);
        LocalDate start,end;
        if(p==Period.THIS_MONTH){
            start=today.withDayOfMonth(1);end=start.plusMonths(1);
        }else if(p==Period.LAST_MONTH){
            end=today.withDayOfMonth(1);start=end.minusMonths(1);
        }else{
            start=today.withDayOfYear(1);end=start.plusYears(1);
        }
        return new long[]{start.atStartOfDay(zone).toInstant().toEpochMilli(),end.atStartOfDay(zone).toInstant().toEpochMilli()};
    }

    private static long parseRemoteTime(String v){
        if(v==null||v.isBlank())return 0L;
        try{return Instant.parse(v).toEpochMilli();}catch(Exception ignored){}
        try{return ZonedDateTime.parse(v,DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli();}catch(DateTimeParseException ignored){}
        try{return java.sql.Timestamp.valueOf(v.replace("T"," ").replace("Z","")).getTime();}catch(Exception ignored){}
        return 0L;
    }

    public enum Period{THIS_MONTH,LAST_MONTH,THIS_YEAR}

    public static final class Summary{
        public int count=0;
        public double cost=0d;
        public int knownCostCount=0;
        public final java.util.LinkedHashMap<String,Integer> typeCounts=new java.util.LinkedHashMap<>();

        void add(String registered,boolean color,boolean duplex,double value){
            count++;
            String type=(registered==null||"Nein".equals(registered)?"Standard":registered)+" · "+(color?"Farbe":"SW")+" · "+(duplex?"Duplex":"Einseitig");
            typeCounts.put(type,typeCounts.getOrDefault(type,0)+1);
            if(value>=0){cost+=value;knownCostCount++;}
        }

        public String topTypes(){
            if(typeCounts.isEmpty())return "Noch keine Sendungen";
            java.util.List<java.util.Map.Entry<String,Integer>> e=new java.util.ArrayList<>(typeCounts.entrySet());
            e.sort((a,b)->Integer.compare(b.getValue(),a.getValue()));
            StringBuilder s=new StringBuilder();
            for(int i=0;i<Math.min(2,e.size());i++){
                if(i>0)s.append(" · ");
                s.append(e.get(i).getValue()).append("× ").append(e.get(i).getKey());
            }
            return s.toString();
        }
    }
}
