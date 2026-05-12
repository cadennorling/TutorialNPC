package com.tutorialnpcs.managers;

import com.tutorialnpcs.TutorialNPCsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerProgressManager {

    private final TutorialNPCsPlugin plugin;
    private File dataFile;
    private YamlConfiguration dataConfig;

    private final Map<UUID, Integer> nextNpcIndex = new HashMap<>();
    private final Map<UUID, Integer> dialogueLine = new HashMap<>();
    private final Map<UUID, Integer> activeTutorialNpcId = new HashMap<>();

    public PlayerProgressManager(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        String fileName = plugin.getConfig().getString("storage.player-data-file", "playerdata.yml");
        dataFile = new File(plugin.getDataFolder(), fileName);
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not create playerdata.yml", e); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        nextNpcIndex.clear();
        if (dataConfig.contains("players")) {
            for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int idx = dataConfig.getInt("players." + key + ".nextNpcIndex", 0);
                    nextNpcIndex.put(uuid, idx);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        plugin.getLogger().info("Loaded progress for " + nextNpcIndex.size() + " player(s).");
    }

    public void saveAll() {
        for (Map.Entry<UUID, Integer> e : nextNpcIndex.entrySet())
            dataConfig.set("players." + e.getKey() + ".nextNpcIndex", e.getValue());
        try { dataConfig.save(dataFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not save playerdata.yml", e); }
    }

    private void savePlayer(UUID uuid) {
        dataConfig.set("players." + uuid + ".nextNpcIndex", nextNpcIndex.getOrDefault(uuid, 0));
        try { dataConfig.save(dataFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not save playerdata.yml", e); }
    }

    public int getNextNpcIndex(Player player) { return nextNpcIndex.getOrDefault(player.getUniqueId(), 0); }

    public boolean hasCompletedAll(Player player) {
        return getNextNpcIndex(player) >= plugin.getNpcDataManager().getNPCs().size();
    }

    public void advance(Player player) {
        UUID uuid = player.getUniqueId();
        nextNpcIndex.put(uuid, nextNpcIndex.getOrDefault(uuid, 0) + 1);
        savePlayer(uuid);
        plugin.getHologramManager().updateForPlayer(player);
    }

    public void resetProgress(Player player) {
        UUID uuid = player.getUniqueId();
        nextNpcIndex.put(uuid, 0);
        dialogueLine.remove(uuid);
        activeTutorialNpcId.remove(uuid);
        savePlayer(uuid);
        plugin.getHologramManager().updateForPlayer(player);
    }

    public void setProgress(Player player, int index) {
        nextNpcIndex.put(player.getUniqueId(), Math.max(0, index));
        savePlayer(player.getUniqueId());
        plugin.getHologramManager().updateForPlayer(player);
    }

    public boolean isInDialogue(Player player) { return activeTutorialNpcId.containsKey(player.getUniqueId()); }
    public int getActiveTutorialNpcId(Player player) { return activeTutorialNpcId.getOrDefault(player.getUniqueId(), -1); }

    public void startDialogue(Player player, int tutorialNpcId) {
        activeTutorialNpcId.put(player.getUniqueId(), tutorialNpcId);
        dialogueLine.put(player.getUniqueId(), 0);
    }

    public void endDialogue(Player player) {
        activeTutorialNpcId.remove(player.getUniqueId());
        dialogueLine.remove(player.getUniqueId());
    }

    public int getDialogueLine(Player player) { return dialogueLine.getOrDefault(player.getUniqueId(), 0); }

    public void advanceDialogueLine(Player player) {
        UUID uuid = player.getUniqueId();
        dialogueLine.put(uuid, dialogueLine.getOrDefault(uuid, 0) + 1);
    }
}
