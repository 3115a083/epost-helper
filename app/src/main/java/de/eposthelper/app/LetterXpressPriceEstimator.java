package de.eposthelper.app;

public final class LetterXpressPriceEstimator {
    private LetterXpressPriceEstimator(){}

    public static double gross(JobOptions o,int pages){
        if(pages<1)return -1;
        int sheets=o.duplex?(pages+1)/2:pages;
        int col=o.color?1:0;
        int dup=o.duplex?1:0;

        double base;
        if(sheets<=3)base=pick(col,dup,0.96,1.00,1.09,1.19);
        else if(sheets<=8)base=pick(col,dup,1.33,1.37,1.46,1.56);
        else if(sheets<=90)base=pick(col,dup,2.02,2.06,2.15,2.25);
        else if(sheets<=190)base=pick(col,dup,4.05,4.06,4.07,4.14);
        else base=pick(col,dup,8.33,8.45,8.57,8.69);

        double extra=pick(col,dup,0.07,0.08,0.11,0.18);
        double total=base+Math.max(0,sheets-1)*extra;
        String r=o.lxpRegistered();
        if("r1".equals(r))total+=4.06;
        if("r2".equals(r))total+=4.45;
        if(o.c4)total+=1.01;
        return Math.round(total*100.0)/100.0;
    }

    private static double pick(int color,int duplex,double swSimplex,double swDuplex,double colorSimplex,double colorDuplex){
        if(color==1)return duplex==1?colorDuplex:colorSimplex;
        return duplex==1?swDuplex:swSimplex;
    }
}
