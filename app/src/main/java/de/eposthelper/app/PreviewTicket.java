package de.eposthelper.app;

/** A list position alone cannot identify an asynchronous PDF preview. */
final class PreviewTicket {
    private final long generation;
    private final String uri;
    PreviewTicket(long generation,String uri){this.generation=generation;this.uri=uri;}
    boolean matches(long currentGeneration,String currentUri){
        return generation==currentGeneration&&uri.equals(currentUri);
    }
}
