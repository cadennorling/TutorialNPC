package com.tutorialnpcs;

import com.tutorialnpcs.commands.TnpcCommand;
import com.tutorialnpcs.listeners.NPCClickListener;
import com.tutorialnpcs.listeners.PlayerJoinListener;
import com.tutorialnpcs.listeners.PlayerQuitListener;
import com.tutorialnpcs.managers.*;
import org.bukkit.plugin.java.JavaPlugin;

public class TutorialNPCsPlugin extends JavaPlugin {

    private static TutorialNPCsPlugin instance;

    private DatabaseManager databaseManager;
    private NPCDataManager npcDataManager;
    private PlayerProgressManager playerProgressManager;
    private DialogueManager dialogueManager;
    private HologramManager hologramManager;
    private NPCClickListener npcClickListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        npcDataManager = new NPCDataManager(this);
        playerProgressManager = new PlayerProgressManager(this);
        dialogueManager = new DialogueManager(this);
        hologramManager = new HologramManager(this);
        npcClickListener = new NPCClickListener(this);

        if (!databaseManager.connect()) {
            getLogger().severe("Could not connect to MySQL! Disabling TutorialNPCs.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        npcDataManager.loadNPCs();

        getServer().getPluginManager().registerEvents(npcClickListener, this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);

        TnpcCommand cmd = new TnpcCommand(this);
        getCommand("tnpc").setExecutor(cmd);
        getCommand("tnpc").setTabCompleter(cmd);

        hologramManager.startRefreshTask();

        for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
            playerProgressManager.loadPlayer(p);
            hologramManager.updateForPlayer(p);
        }

        getLogger().info("TutorialNPCs enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) hologramManager.removeAllHolograms();
        if (dialogueManager != null) {
            for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
                dialogueManager.cancelDialogue(p);
            }
        }
        if (databaseManager != null) databaseManager.disconnect();
        getLogger().info("TutorialNPCs disabled.");
    }

    public static TutorialNPCsPlugin getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public NPCDataManager getNpcDataManager() { return npcDataManager; }
    public PlayerProgressManager getPlayerProgressManager() { return playerProgressManager; }
    public DialogueManager getDialogueManager() { return dialogueManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public NPCClickListener getNPCClickListener() { return npcClickListener; }

    public static String color(String s) {
        return s == null ? "" : org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }
}
