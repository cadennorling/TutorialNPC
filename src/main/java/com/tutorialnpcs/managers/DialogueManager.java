package com.tutorialnpcs.managers;

import com.tutorialnpcs.TutorialNPCsPlugin;
import com.tutorialnpcs.model.TutorialNPC;
import org.bukkit.entity.Player;

import java.util.List;

import static com.tutorialnpcs.TutorialNPCsPlugin.color;

public class DialogueManager {

    private final TutorialNPCsPlugin plugin;

    public DialogueManager(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a player right-clicks a tutorial NPC.
     * Handles first click (open dialogue) and subsequent clicks (advance / finish).
     */
    public void handleClick(Player player, TutorialNPC tnpc) {
        PlayerProgressManager ppm = plugin.getPlayerProgressManager();
        boolean enforceOrder = plugin.getConfig().getBoolean("progression.enforce-order", true);
        boolean allowRevisit = plugin.getConfig().getBoolean("progression.allow-revisit", true);
        String prefix = color(plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        List<TutorialNPC> allNpcs = plugin.getNpcDataManager().getNPCs();
        int nextIdx = ppm.getNextNpcIndex(player);

        // Find the index of this NPC in the ordered list
        int thisIdx = -1;
        for (int i = 0; i < allNpcs.size(); i++) {
            if (allNpcs.get(i).getId() == tnpc.getId()) {
                thisIdx = i;
                break;
            }
        }
        if (thisIdx < 0) return; // NPC not in list

        // ── Already completed this NPC ───────────────────────────────────────
        if (ppm.isCompleted(player, tnpc.getId())) {
            if (!allowRevisit) {
                String revisit = plugin.getConfig().getString("progression.revisit-message",
                        "&7You have already spoken with this NPC.");
                player.sendMessage(prefix + color(revisit));
                return;
            }
            // Allow revisit but never re-advance
            handleDialogueClick(player, tnpc, false);
            return;
        }

        // ── Order enforcement ────────────────────────────────────────────────
        if (enforceOrder && !player.hasPermission("tutorialnpcs.bypass")) {
            if (thisIdx > nextIdx) {
                // Trying to skip ahead
                TutorialNPC required = allNpcs.get(nextIdx);
                String msg = plugin.getConfig().getString("progression.wrong-order-message",
                        "&cYou need to speak with &e{npc_name} &cfirst!");
                msg = msg.replace("{npc_name}", required.getName());
                player.sendMessage(prefix + color(msg));
                return;
            }
        }

        // ── This is the correct (or bypass) NPC ─────────────────────────────
        handleDialogueClick(player, tnpc, true);
    }

    /**
     * Sends the next dialogue line, or finishes the conversation.
     * @param advanceOnFinish whether completing the dialogue should advance player progress
     */
    private void handleDialogueClick(Player player, TutorialNPC tnpc, boolean advanceOnFinish) {
        PlayerProgressManager ppm = plugin.getPlayerProgressManager();
        String prefix = color(plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        // If not already in dialogue with this NPC, start fresh
        if (!ppm.isInDialogue(player) || ppm.getActiveTutorialNpcId(player) != tnpc.getId()) {
            ppm.startDialogue(player, tnpc.getId());
        }

        List<String> lines = tnpc.getDialogue();
        int lineIndex = ppm.getDialogueLine(player);

        if (lines.isEmpty()) {
            player.sendMessage(prefix + color("&7This NPC has nothing to say yet."));
            ppm.endDialogue(player);
            return;
        }

        // Print separator + current line
        String separator = color(plugin.getConfig().getString("dialogue.separator",
                "&8&m------------------------------"));
        player.sendMessage(separator);
        player.sendMessage(color(lines.get(lineIndex)));

        boolean isLastLine = (lineIndex >= lines.size() - 1);

        if (isLastLine) {
            // End of dialogue
            String finishedMsg = color(plugin.getConfig().getString("dialogue.finished-message",
                    "&aConversation complete! Head to the next NPC."));
            player.sendMessage(finishedMsg);
            player.sendMessage(separator);

            ppm.endDialogue(player);

            if (advanceOnFinish) {
                ppm.markCompleted(player, tnpc.getId());
                ppm.advance(player);

                // Check if all done
                if (ppm.hasCompletedAll(player)) {
                    player.sendMessage(prefix + color("&6&lYou have completed the entire tutorial! Well done!"));
                    if (plugin.getConfig().getBoolean("progression.broadcast-completion", false)) {
                        String broadcast = color(plugin.getConfig().getString(
                                "progression.completion-broadcast",
                                "&6&l{player} &ehas completed the tutorial!"));
                        broadcast = broadcast.replace("{player}", player.getName());
                        player.getServer().broadcastMessage(broadcast);
                    }
                }
            }
        } else {
            // More lines remain
            String continueMsg = color(plugin.getConfig().getString("dialogue.click-to-continue",
                    "&7&o(Click to continue)"));
            player.sendMessage(continueMsg);
            player.sendMessage(separator);
            ppm.advanceDialogueLine(player);
        }
    }
}
