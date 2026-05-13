package com.tutorialnpcs.listeners;

import com.tutorialnpcs.TutorialNPCsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final TutorialNPCsPlugin plugin;

    public PlayerJoinListener(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Load player data from MySQL async, then update hologram on main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getPlayerProgressManager().loadPlayer(event.getPlayer());
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getHologramManager().updateForPlayer(event.getPlayer()));
        });
    }
}
