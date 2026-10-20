package de.eposthelper.app;

/** Clipboard diagnostics use an allowlist instead of guessing every possible personal field. */
final class DiagnosticRedactor {
    private DiagnosticRedactor(){}
    static String forCopy(String details,String kind){
        String type=kind==null?"Fehler":kind.replaceAll("[^A-Za-z0-9_]","");
        StringBuilder out=new StringBuilder("E-POST Helper ").append(BuildConfig.VERSION_NAME)
            .append("\nFehlertyp: ").append(type);
        java.util.regex.Matcher status=java.util.regex.Pattern
            .compile("(?i)HTTP(?:/[0-9.]+)?(?:[ \\t]+(?:status|error|code))?[ :\\t]+([1-5][0-9]{2})")
            .matcher(details==null?"":details);
        if(status.find())out.append("\nHTTP-Status: ").append(status.group(1));
        out.append("\nAntworttexte, URLs, Dateinamen und personenbezogene Daten wurden ausgelassen.");
        return out.toString();
    }
}
