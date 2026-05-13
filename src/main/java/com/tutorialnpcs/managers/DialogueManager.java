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
        PlayerProgressManager ppm = plugin.getPlayerProgressManager();
        boolean enforceOrder = plugin.getConfig().getBoolean("progression.enforce-order", true);
        boolean allowRevisit = plugin.getConfig().getBoolean("progression.allow-revisit", false);
        String prefix = color(plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        // If already in auto-advance dialogue, ignore clicks
        if (autoAdvanceTasks.containsKey(player.getUniqueId())) return;

        List<TutorialNPC> allNpcs = plugin.getNpcDataManager().getNPCs();
        int nextIdx = ppm.getNextNpcIndex(player);

        int thisIdx = -1;
        for (int i = 0; i < allNpcs.size(); i++) {
            if (allNpcs.get(i).getId() == tnpc.getId()) { thisIdx = i; break; }
        }
        if (thisIdx < 0) return;

        // ── Already completed ────────────────────────────────────────────────
        if (ppm.isCompleted(player, tnpc.getId())) {
            if (allowRevisit) {
                startAutoDialogue(player, tnpc, false);
            } else {
                player.sendMessage(prefix + color(plugin.getConfig().getString(
                        "progression.revisit-message", "&7You have already spoken with this NPC.")));
            }
            return;
        }

        // ── Order enforcement ────────────────────────────────────────────────
        if (enforceOrder && !player.hasPermission("tutorialnpcs.bypass")) {
            if (thisIdx > nextIdx) {
                TutorialNPC required = allNpcs.get(nextIdx);
                String msg = plugin.getConfig().getString("progression.wrong-order-message",
                        "&cYou need to speak with &e{npc_name} &cfirst!");
                player.sendMessage(prefix + color(msg.replace("{npc_name}", required.getName())));
                return;
            }
        }

        // ── Start auto-advancing dialogue ────────────────────────────────────
        startAutoDialogue(player, tnpc, true);
    }

    private void startAutoDialogue(Player player, TutorialNPC tnpc, boolean advanceOnFinish) {
        PlayerProgressManager ppm = plugin.getPlayerProgressManager();
        List<String> lines = tnpc.getDialogue();

        if (lines.isEmpty()) {
            String prefix = color(plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));
            player.sendMessage(prefix + color("&7This NPC has nothing to say yet."));
            return;
        }

        ppm.startDialogue(player, tnpc.getId());
        ppm.setDialogueLine(player, 0);

        double delaySeconds = plugin.getConfig().getDouble("dialogue.line-delay-seconds", 3.0);
        long delayTicks = Math.max(1L, (long)(delaySeconds * 20));

        String separator = color(plugin.getConfig().getString("dialogue.separator",
                "&8&m------------------------------"));

        // Send first line immediately
        player.sendMessage(separator);
        player.sendMessage(color(lines.get(0)));

        UUID uuid = player.getUniqueId();

        // Schedule remaining lines
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int lineIndex = 1;

            @Override
            public void run() {
                // Player went offline
                if (!player.isOnline()) {
                    cancelTask(uuid);
                    ppm.endDialogue(player);
                    return;
                }

                if (lineIndex >= lines.size()) {
                    // All lines sent — finish up
                    player.sendMessage(color(plugin.getConfig().getString("dialogue.finished-message",
                            "&aConversation complete! Head to the next NPC.")));
                    player.sendMessage(separator);
                    ppm.endDialogue(player);
                    cancelTask(uuid);

                    if (advanceOnFinish) {
                        ppm.markCompleted(player, tnpc.getId());
                        ppm.advance(player);

                        if (ppm.hasCompletedAll(player)) {
                            String prefix = color(plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));
                            player.sendMessage(prefix + color("&6&lYou have completed the entire tutorial! Well done!"));
                            if (plugin.getConfig().getBoolean("progression.broadcast-completion", false)) {
                                String broadcast = color(plugin.getConfig().getString(
                                        "progression.completion-broadcast",
                                        "&6&l{player} &ehas completed the tutorial!"))
                                        .replace("{player}", player.getName());
                                plugin.getServer().broadcastMessage(broadcast);
                            }
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
