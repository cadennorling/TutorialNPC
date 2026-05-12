package com.tutorialnpcs.managers;

import com.tutorialnpcs.TutorialNPCsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class PlayerProgressManager {

    private final TutorialNPCsPlugin plugin;
    private File dataFile;
    private YamlConfiguration dataConfig;

    private final Map<UUID, Integer> nextNpcIndex = new HashMap<>();
    private final Map<UUID, Integer> dialogueLine = new HashMap<>();
    private final Map<UUID, Integer> activeTutorialNpcId = new HashMap<>();
    private final Map<UUID, Set<Integer>> completedNpcs = new HashMap<>();

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
        completedNpcs.clear();

        if (dataConfig.contains("players")) {
            for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int idx = dataConfig.getInt("players." + key + ".nextNpcIndex", 0);
                    nextNpcIndex.put(uuid, idx);
                    List<?> raw = dataConfig.getList("players." + key + ".completedNpcs", new ArrayList<>());
                    Set<Integer> set = new HashSet<>();
                    for (Object o : raw) { try { set.add(Integer.parseInt(o.toString())); } catch (Exception ignored) {} }
                    completedNpcs.put(uuid, set);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        plugin.getLogger().info("Loaded progress for " + nextNpcIndex.size() + " player(s).");
    }

    public void saveAll() {
        for (Map.Entry<UUID, Integer> e : nextNpcIndex.entrySet()) {
            String path = "players." + e.getKey();
            dataConfig.set(path + ".nextNpcIndex", e.getValue());
            dataConfig.set(path + ".completedNpcs", new ArrayList<>(completedNpcs.getOrDefault(e.getKey(), new HashSet<>())));
        }
        try { dataConfig.save(dataFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not save playerdata.yml", e); }
    }

    private void savePlayer(UUID uuid) {
        dataConfig.set("players." + uuid + ".nextNpcIndex", nextNpcIndex.getOrDefault(uuid, 0));
        dataConfig.set("players." + uuid + ".completedNpcs", new ArrayList<>(completedNpcs.getOrDefault(uuid, new HashSet<>())));
        try { dataConfig.save(dataFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not save playerdata.yml", e); }
    }

    public boolean isCompleted(Player player, int npcId) {
        return completedNpcs.getOrDefault(player.getUniqueId(), new HashSet<>()).contains(npcId);
    }

    public void markCompleted(Player player, int npcId) {
        completedNpcs.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(npcId);
    }

    public int getNextNpcIndex(Player player) {
        return nextNpcIndex.getOrDefault(player.getUniqueId(), 0);
    }

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
        completedNpcs.remove(uuid);
        dialogueLine.remove(uuid);
        activeTutorialNpcId.remove(uuid);
        savePlayer(uuid);
        plugin.getHologramManager().updateForPlayer(player);
    }

    public void setProgress(Player player, int index) {
        UUID uuid = player.getUniqueId();
        nextNpcIndex.put(uuid, Math.max(0, index));
        savePlayer(uuid);
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
