package de.eposthelper.app;

public final class DiagnosticException extends Exception {
    private final String userMessage;
    private final String debugDetails;

    public DiagnosticException(String userMessage,String debugDetails){
        super(userMessage);
        this.userMessage=userMessage;
        this.debugDetails=debugDetails;
    }

    public String userMessage(){ return userMessage; }
    public String debugDetails(){ return debugDetails; }
}
