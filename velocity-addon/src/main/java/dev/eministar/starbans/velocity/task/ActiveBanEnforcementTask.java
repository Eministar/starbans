package dev.eministar.starbans.velocity.task;

import dev.eministar.starbans.velocity.StarBansVelocityAddon;

public final class ActiveBanEnforcementTask implements Runnable {

    private final StarBansVelocityAddon plugin;

    public ActiveBanEnforcementTask(StarBansVelocityAddon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getActiveBanEnforcer().enforceAll();
    }
}
