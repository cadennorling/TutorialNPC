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
        // Cancel any running dialogue task
        plugin.getDialogueManager().cancelDialogue(event.getPlayer());
        // Remove their hologram
        plugin.getHologramManager().removeForPlayer(event.getPlayer());
        // Unload from memory (data is already saved async on each change)
        plugin.getPlayerProgressManager().unloadPlayer(event.getPlayer());
    }
}
