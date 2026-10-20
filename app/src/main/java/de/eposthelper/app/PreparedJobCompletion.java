package de.eposthelper.app;

/** A local test must never consume a real document or remove its queued job. */
final class PreparedJobCompletion {
    interface Step {void run() throws Exception;}
    static boolean finish(Profile profile,Step deleteSources,Step deleteJob) throws Exception {
        if(DebugProfileManager.isDebug(profile))return false;
        deleteSources.run();
        deleteJob.run();
        return true;
    }
}
