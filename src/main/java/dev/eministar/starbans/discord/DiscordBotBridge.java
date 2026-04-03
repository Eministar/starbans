package dev.eministar.starbans.discord;

public interface DiscordBotBridge extends AutoCloseable {

    void start() throws Exception;

    void syncWorkflowCase(long caseId) throws Exception;

    @Override
    void close() throws Exception;
}
