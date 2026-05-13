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

    // UUID → running auto-advance task
    private final Map<UUID, BukkitTask> autoAdvanceTasks = new ConcurrentHashMap<>();

    public DialogueManager(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    public void handleClick(Player player, TutorialNPC tnpc) {
        // Don't start if already in dialogue (safety check)
        if (autoAdvanceTasks.containsKey(player.getUniqueId())) return;
        startAutoDialogue(player, tnpc);
    }

    private void startAutoDialogue(Player player, TutorialNPC tnpc) {
        PlayerProgressManager ppm = plugin.getPlayerProgressManager();
        List<String> lines = tnpc.getDialogue();
        String prefix = color(plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        if (lines.isEmpty()) {
            player.sendMessage(prefix + color("&7This NPC has nothing to say yet."));
            return;
        }

        ppm.startDialogue(player, tnpc.getId());
        ppm.setDialogueLine(player, 0);

        double delaySeconds = plugin.getConfig().getDouble("dialogue.line-delay-seconds", 3.0);
        long delayTicks = Math.max(1L, (long) (delaySeconds * 20));

        String separator = color(plugin.getConfig().getString("dialogue.separator",
                "&8&m------------------------------"));

        // Send first line immediately
        player.sendMessage(separator);
        player.sendMessage(color(lines.get(0)));

        // If only one line, finish immediately after delay
        UUID uuid = player.getUniqueId();

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int lineIndex = 1;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelTask(uuid);
                    ppm.endDialogue(player);
                    return;
                }

                if (lineIndex >= lines.size()) {
                    // All lines done
                    player.sendMessage(color(plugin.getConfig().getString("dialogue.finished-message",
                            "&aConversation complete! Head to the next NPC.")));
                    player.sendMessage(separator);
                    ppm.endDialogue(player);
                    cancelTask(uuid);

                    // Mark completed and advance
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

                // Send next line
                player.sendMessage(color(lines.get(lineIndex)));
                ppm.setDialogueLine(player, lineIndex);
                lineIndex++;
            }
        }, delayTicks, delayTicks);

        autoAdvanceTasks.put(uuid, task);
    }

    private void cancelTask(UUID uuid) {
        BukkitTask task = autoAdvanceTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    public void cancelDialogue(Player player) {
        cancelTask(player.getUniqueId());
        plugin.getPlayerProgressManager().endDialogue(player);
    }

    public boolean isInAutoDialogue(Player player) {
        return autoAdvanceTasks.containsKey(player.getUniqueId());
    }
}
