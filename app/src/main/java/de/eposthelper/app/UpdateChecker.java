package de.eposthelper.app;

import android.content.Context;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class UpdateChecker {
    private static final String API="https://api.github.com/repos/3115a083/epost-helper/releases/latest";
    private UpdateChecker(){}

    public static Result check() throws Exception{
        Request req=new Request.Builder().url(API).header("Accept","application/vnd.github+json").header("User-Agent","E-POST-Helper").build();
        try(Response r=new OkHttpClient().newCall(req).execute()){
            if(r.code()==404)return new Result(false,"","", "Noch keine GitHub-Release veröffentlicht.");
            if(!r.isSuccessful())throw new IllegalStateException("GitHub meldet HTTP "+r.code());
            String raw=r.body()==null?"":r.body().string();
            JSONObject o=new JSONObject(raw);
            String tag=o.optString("tag_name","");
            String url=o.optString("html_url","");
            String current=BuildConfig.VERSION_NAME;
            boolean newer=compare(clean(tag),clean(current))>0;
            return new Result(newer,tag,url,newer?"Neue Version "+tag+" verfügbar.":"Du verwendest die aktuelle veröffentlichte Version.");
        }
    }

    private static String clean(String v){
        if(v==null)return "0";
        v=v.trim();
        if(v.startsWith("v")||v.startsWith("V"))v=v.substring(1);
        int dash=v.indexOf('-');if(dash>=0)v=v.substring(0,dash);
        return v;
    }

    private static int compare(String a,String b){
        String[] x=a.split("\\."),y=b.split("\\.");
        int n=Math.max(x.length,y.length);
        for(int i=0;i<n;i++){
            int xi=i<x.length?num(x[i]):0,yi=i<y.length?num(y[i]):0;
            if(xi!=yi)return Integer.compare(xi,yi);
        }
        return 0;
    }

    private static int num(String s){
        try{return Integer.parseInt(s.replaceAll("[^0-9]",""));}catch(Exception e){return 0;}
    }

    public static final class Result{
        public final boolean updateAvailable;
        public final String version;
        public final String url;
        public final String message;
        Result(boolean a,String v,String u,String m){updateAvailable=a;version=v;url=u;message=m;}
    }
}
