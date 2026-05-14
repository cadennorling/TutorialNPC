package com.tutorialnpcs.managers;

import com.tutorialnpcs.TutorialNPCsPlugin;
import com.tutorialnpcs.model.TutorialNPC;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.tutorialnpcs.TutorialNPCsPlugin.color;

public class DialogueManager {

    private final TutorialNPCsPlugin plugin;
    private static final int MAX_DIALOGUE_LINES = 100;
    private final Map<UUID, BukkitTask> autoAdvanceTasks = new ConcurrentHashMap<>();

    public DialogueManager(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    public void handleClick(Player player, TutorialNPC tnpc) {
        if (player == null || tnpc == null) return;
        if (autoAdvanceTasks.containsKey(player.getUniqueId())) return;
        startAutoDialogue(player, tnpc);
    }

    private void startAutoDialogue(Player player, TutorialNPC tnpc) {
        PlayerProgressManager ppm = plugin.getPlayerProgressManager();
        List<String> lines = tnpc.getDialogue();
        String prefix = color(plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        if (lines == null || lines.isEmpty()) {
            player.sendMessage(prefix + color("&7This NPC has nothing to say yet."));
            return;
        }

        // Safety cap — never run more than MAX lines
        int totalLines = Math.min(lines.size(), MAX_DIALOGUE_LINES);

        ppm.startDialogue(player, tnpc.getId());
        ppm.setDialogueLine(player, 0);

        // Clamp delay to sane range: 0.5s minimum, 60s maximum
        double rawDelay = plugin.getConfig().getDouble("dialogue.line-delay-seconds", 3.0);
        double clampedDelay = Math.max(0.5, Math.min(60.0, rawDelay));
        long delayTicks = (long) (clampedDelay * 20);

        String separator = color(plugin.getConfig().getString("dialogue.separator",
                "&8&m------------------------------"));

        // Send first line immediately
        player.sendMessage(separator);
        player.sendMessage(color(lines.get(0)));

        UUID uuid = player.getUniqueId();
        final int lineCap = totalLines;

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int lineIndex = 1;

            @Override
            public void run() {
                // Safety: player offline
                if (!player.isOnline()) {
                    cancelTask(uuid);
                    ppm.endDialogue(player);
                    return;
                }

                if (lineIndex >= lineCap) {
                    // All lines done
                    player.sendMessage(color(plugin.getConfig().getString("dialogue.finished-message",
                            "&aConversation complete! Head to the next NPC.")));
                    player.sendMessage(separator);
                    ppm.endDialogue(player);
                    cancelTask(uuid);

                    ppm.markCompleted(player, tnpc.getId());
                    ppm.advance(player);

                    if (ppm.hasCompletedAll(player)) {
                        player.sendMessage(prefix + color("&6&lYou have completed the entire tutorial! Well done!"));
                        if (plugin.getConfig().getBoolean("progression.broadcast-completion", false)) {
                            String broadcast = color(plugin.getConfig().getString(
                                    "progression.completion-broadcast",
                                    "&6&l{player} &ehas completed the tutorial!"))
                                    .replace("{player}", player.getName());
                            plugin.getServer().broadcastMessage(broadcast);
                        }
                    }
                    return;
                }

                // Send next line safely
                if (lineIndex < lines.size()) {
                    player.sendMessage(color(lines.get(lineIndex)));
                    ppm.setDialogueLine(player, lineIndex);
                }
                lineIndex++;
            }
        }, delayTicks, delayTicks);

        autoAdvanceTasks.put(uuid, task);
    }

    private void cancelTask(UUID uuid) {
        BukkitTask task = autoAdvanceTasks.remove(uuid);
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
    }

    public void cancelDialogue(Player player) {
        if (player == null) return;
        cancelTask(player.getUniqueId());
        plugin.getPlayerProgressManager().endDialogue(player);
    }

    public boolean isInAutoDialogue(Player player) {
        if (player == null) return false;
        return autoAdvanceTasks.containsKey(player.getUniqueId());
    }
}
