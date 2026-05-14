package com.tutorialnpcs.listeners;

import com.tutorialnpcs.TutorialNPCsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final TutorialNPCsPlugin plugin;

    public PlayerQuitListener(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer() == null) return;
        plugin.getDialogueManager().cancelDialogue(event.getPlayer());
        plugin.getHologramManager().removeForPlayer(event.getPlayer());
        plugin.getPlayerProgressManager().unloadPlayer(event.getPlayer());
        // Clean up rate limit data
        plugin.getNPCClickListener().cleanup(event.getPlayer().getUniqueId());
    }
}
