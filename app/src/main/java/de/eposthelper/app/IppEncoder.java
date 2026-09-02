package de.eposthelper.app;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

public final class IppEncoder {
    private IppEncoder() {}

    private static void attr(DataOutputStream d,int tag,String name,String value) throws Exception{
        byte[] n=name.getBytes(StandardCharsets.UTF_8),v=value.getBytes(StandardCharsets.UTF_8);
        d.writeByte(tag); d.writeShort(n.length); d.write(n); d.writeShort(v.length); d.write(v);
    }

    private static DataOutputStream header(ByteArrayOutputStream out,int operation) throws Exception{
        DataOutputStream d=new DataOutputStream(out);
        d.writeByte(0x02); d.writeByte(0x00); d.writeShort(operation);
        d.writeInt((int)(System.currentTimeMillis()&0x7fffffff));
        d.writeByte(0x01);
        attr(d,0x47,"attributes-charset","utf-8");
        attr(d,0x48,"attributes-natural-language","de");
        return d;
    }

    public static byte[] printJobHeader(String printerUri,String user,String jobName) throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        DataOutputStream d=header(out,0x0002);
        attr(d,0x45,"printer-uri",printerUri);
        attr(d,0x42,"requesting-user-name",user==null||user.isBlank()?"android":user);
        attr(d,0x42,"job-name",jobName);
        attr(d,0x49,"document-format","application/pdf");
        d.writeByte(0x03); d.flush();
        return out.toByteArray();
    }

    public static byte[] getPrinterAttributesHeader(String printerUri,String user) throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        DataOutputStream d=header(out,0x000B);
        attr(d,0x45,"printer-uri",printerUri);
        attr(d,0x42,"requesting-user-name",user==null||user.isBlank()?"android":user);
        d.writeByte(0x03); d.flush();
        return out.toByteArray();
    }
}
