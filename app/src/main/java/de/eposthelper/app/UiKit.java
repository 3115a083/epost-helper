package de.eposthelper.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public final class UiKit {
    private UiKit() {}

    public static int dp(Context c,int v){ return Math.round(v*c.getResources().getDisplayMetrics().density); }

    public static TextView heading(Context c,String text,int sp){
        TextView t=new TextView(c);
        t.setText(text); t.setTextSize(sp); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setTextColor(resolveText(c)); return t;
    }

    public static TextView body(Context c,String text){
        TextView t=new TextView(c); t.setText(text); t.setTextSize(15); t.setLineSpacing(0,1.15f);
        t.setTextColor(resolveSecondaryText(c)); return t;
    }

    public static TextView mono(Context c,String text){
        TextView t=body(c,text); t.setTypeface(Typeface.MONOSPACE); t.setTextIsSelectable(true); return t;
    }

    public static MaterialCardView surfaceCard(Context c,View content){
        MaterialCardView card=new MaterialCardView(c);
        card.setRadius(dp(c,26)); card.setCardElevation(0); card.setStrokeWidth(0);
        card.setCardBackgroundColor(resolveSurface(c));
        card.setContentPadding(dp(c,18),dp(c,18),dp(c,18),dp(c,18));
        card.addView(content);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,dp(c,7),0,dp(c,7)); card.setLayoutParams(lp);
        return card;
    }

    public static MaterialCardView hero(Context c,View content,int startColor,int endColor){
        MaterialCardView card=new MaterialCardView(c);
        card.setRadius(dp(c,28)); card.setCardElevation(0); card.setStrokeWidth(0);
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{startColor,endColor});
        g.setCornerRadius(dp(c,28)); card.setBackground(g);
        card.setContentPadding(dp(c,22),dp(c,22),dp(c,22),dp(c,22));
        card.addView(content);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,dp(c,8),0,dp(c,12)); card.setLayoutParams(lp);
        return card;
    }

    public static TextView heroTitle(Context c,String text,int sp){
        TextView t=new TextView(c); t.setText(text); t.setTextSize(sp); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setTextColor(Color.WHITE); return t;
    }

    public static TextView heroBody(Context c,String text){
        TextView t=new TextView(c); t.setText(text); t.setTextSize(15); t.setTextColor(0xEFFFFFFF); t.setLineSpacing(0,1.15f); return t;
    }

    public static TextView pill(Context c,String text,boolean positive){
        TextView t=new TextView(c); t.setText(text); t.setTextSize(12); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER); t.setPadding(dp(c,12),dp(c,6),dp(c,12),dp(c,6));
        GradientDrawable g=new GradientDrawable();
        g.setColor(positive?0xFFE5F6E9:0xFFF2F2F2); g.setCornerRadius(dp(c,99)); t.setBackground(g);
        t.setTextColor(positive?0xFF19723C:0xFF5F6368); return t;
    }

    public static MaterialButton primary(Context c,String text){
        MaterialButton b=new MaterialButton(c); b.setText(text); b.setTextSize(14);
        b.setCornerRadius(dp(c,24)); b.setMinHeight(dp(c,52)); return b;
    }

    public static MaterialButton tonal(Context c,String text){
        MaterialButton b=primary(c,text);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE9EEFF));
        b.setTextColor(0xFF2457E6); return b;
    }

    public static int resolveSurface(Context c){
        android.util.TypedValue tv=new android.util.TypedValue();
        c.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerLow,tv,true);
        return tv.data!=0?tv.data:0xFFF7F7FA;
    }
    public static int resolveText(Context c){
        android.util.TypedValue tv=new android.util.TypedValue();
        c.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface,tv,true);
        return tv.data!=0?tv.data:Color.BLACK;
    }
    public static int resolveSecondaryText(Context c){
        android.util.TypedValue tv=new android.util.TypedValue();
        c.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant,tv,true);
        return tv.data!=0?tv.data:0xFF5F6368;
    }
}
