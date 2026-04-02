package dev.eministar.starbans.discord;

public interface DiscordBotBridge extends AutoCloseable {

    void start() throws Exception;

    @Override
    void close() throws Exception;
}
