package com.tutorialnpcs.managers;

import com.tutorialnpcs.TutorialNPCsPlugin;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerProgressManager {

    private final TutorialNPCsPlugin plugin;

    // In-memory cache — backed by MySQL
    private final Map<UUID, Integer> nextNpcIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> completedNpcs = new ConcurrentHashMap<>();

    // Active dialogue state (never persisted, only in-memory)
    private final Map<UUID, Integer> activeTutorialNpcId = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> dialogueLine = new ConcurrentHashMap<>();

    public PlayerProgressManager(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Load / save via DB ────────────────────────────────────────────────────

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        int idx = plugin.getDatabaseManager().loadNextNpcIndex(uuid);
        Set<Integer> completed = plugin.getDatabaseManager().loadCompletedNpcs(uuid);
        nextNpcIndex.put(uuid, idx);
        completedNpcs.put(uuid, completed);
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        nextNpcIndex.remove(uuid);
        completedNpcs.remove(uuid);
        activeTutorialNpcId.remove(uuid);
        dialogueLine.remove(uuid);
    }

    // ── Completion tracking ───────────────────────────────────────────────────

    public boolean isCompleted(Player player, int npcId) {
        return completedNpcs.getOrDefault(player.getUniqueId(), new HashSet<>()).contains(npcId);
    }

    public void markCompleted(Player player, int npcId) {
        UUID uuid = player.getUniqueId();
        completedNpcs.computeIfAbsent(uuid, k -> new HashSet<>()).add(npcId);
        // Async DB write
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getDatabaseManager().markNpcCompleted(uuid, npcId));
    }

    // ── Progress ──────────────────────────────────────────────────────────────

    public int getNextNpcIndex(Player player) {
        return nextNpcIndex.getOrDefault(player.getUniqueId(), 0);
    }

    public boolean hasCompletedAll(Player player) {
        return getNextNpcIndex(player) >= plugin.getNpcDataManager().getNPCs().size();
    }

    public void advance(Player player) {
        UUID uuid = player.getUniqueId();
        int newIdx = nextNpcIndex.getOrDefault(uuid, 0) + 1;
        nextNpcIndex.put(uuid, newIdx);
        plugin.getHologramManager().updateForPlayer(player);
        // Async DB write
        final int save = newIdx;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getDatabaseManager().saveNextNpcIndex(uuid, save));
    }

    public void resetProgress(Player player) {
        UUID uuid = player.getUniqueId();
        nextNpcIndex.put(uuid, 0);
        completedNpcs.put(uuid, new HashSet<>());
        activeTutorialNpcId.remove(uuid);
        dialogueLine.remove(uuid);
        plugin.getHologramManager().updateForPlayer(player);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getDatabaseManager().resetPlayer(uuid));
    }

    public void setProgress(Player player, int index) {
        UUID uuid = player.getUniqueId();
        nextNpcIndex.put(uuid, Math.max(0, index));
        plugin.getHologramManager().updateForPlayer(player);
        final int save = Math.max(0, index);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getDatabaseManager().setProgress(uuid, save));
    }

    // ── Active dialogue state ─────────────────────────────────────────────────

    public boolean isInDialogue(Player player) {
        return activeTutorialNpcId.containsKey(player.getUniqueId());
    }

    public int getActiveTutorialNpcId(Player player) {
        return activeTutorialNpcId.getOrDefault(player.getUniqueId(), -1);
    }

    public void startDialogue(Player player, int tutorialNpcId) {
        activeTutorialNpcId.put(player.getUniqueId(), tutorialNpcId);
        dialogueLine.put(player.getUniqueId(), 0);
    }

    public void endDialogue(Player player) {
        activeTutorialNpcId.remove(player.getUniqueId());
        dialogueLine.remove(player.getUniqueId());
    }

    public int getDialogueLine(Player player) {
        return dialogueLine.getOrDefault(player.getUniqueId(), 0);
    }

    public void setDialogueLine(Player player, int line) {
        dialogueLine.put(player.getUniqueId(), line);
    }
}
