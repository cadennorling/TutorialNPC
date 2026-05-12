package com.tutorialnpcs;

import com.tutorialnpcs.commands.TnpcCommand;
import com.tutorialnpcs.listeners.NPCClickListener;
import com.tutorialnpcs.listeners.PlayerQuitListener;
import com.tutorialnpcs.managers.DialogueManager;
import com.tutorialnpcs.managers.HologramManager;
import com.tutorialnpcs.managers.NPCDataManager;
import com.tutorialnpcs.managers.PlayerProgressManager;
import org.bukkit.plugin.java.JavaPlugin;

public class TutorialNPCsPlugin extends JavaPlugin {

    private static TutorialNPCsPlugin instance;

    private NPCDataManager npcDataManager;
    private PlayerProgressManager playerProgressManager;
    private DialogueManager dialogueManager;
    private HologramManager hologramManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        npcDataManager = new NPCDataManager(this);
        playerProgressManager = new PlayerProgressManager(this);
        dialogueManager = new DialogueManager(this);
        hologramManager = new HologramManager(this);
        npcDataManager.loadNPCs();
        playerProgressManager.loadAll();
        getServer().getPluginManager().registerEvents(new NPCClickListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        TnpcCommand cmd = new TnpcCommand(this);
        getCommand("tnpc").setExecutor(cmd);
        getCommand("tnpc").setTabCompleter(cmd);
        hologramManager.startRefreshTask();
        getLogger().info("TutorialNPCs enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) hologramManager.removeAllHolograms();
        if (playerProgressManager != null) playerProgressManager.saveAll();
        getLogger().info("TutorialNPCs disabled.");
    }

    public static TutorialNPCsPlugin getInstance() { return instance; }
    public NPCDataManager getNpcDataManager() { return npcDataManager; }
    public PlayerProgressManager getPlayerProgressManager() { return playerProgressManager; }
    public DialogueManager getDialogueManager() { return dialogueManager; }
    public HologramManager getHologramManager() { return hologramManager; }

    public static String color(String s) {
        return s == null ? "" : org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }
}
