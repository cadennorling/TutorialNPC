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

    public void handleClick(Player player, TutorialNPC tnpc) {
        PlayerProgressManager ppm = plugin.getPlayerProgressManager();
        boolean enforceOrder = plugin.getConfig().getBoolean("progression.enforce-order", true);
        boolean allowRevisit = plugin.getConfig().getBoolean("progression.allow-revisit", true);
        String prefix = color(plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        List<TutorialNPC> allNpcs = plugin.getNpcDataManager().getNPCs();
        int nextIdx = ppm.getNextNpcIndex(player);

        int thisIdx = -1;
        for (int i = 0; i < allNpcs.size(); i++) {
            if (allNpcs.get(i).getId() == tnpc.getId()) { thisIdx = i; break; }
        }
        if (thisIdx < 0) return;

        if (enforceOrder && !player.hasPermission("tutorialnpcs.bypass")) {
            if (thisIdx > nextIdx) {
                TutorialNPC required = allNpcs.get(nextIdx);
                String msg = plugin.getConfig().getString("progression.wrong-order-message",
                        "&cYou need to speak with &e{npc_name} &cfirst!");
                player.sendMessage(prefix + color(msg.replace("{npc_name}", required.getName())));
                return;
            }
            if (thisIdx < nextIdx) {
                if (!allowRevisit) {
                    player.sendMessage(prefix + color(plugin.getConfig().getString(
                            "progression.revisit-message", "&7You have already spoken with this NPC.")));
                    return;
                }
                handleDialogueClick(player, tnpc, false);
                return;
            }
        }
        handleDialogueClick(player, tnpc, thisIdx == nextIdx);
    }

    private void handleDialogueClick(Player player, TutorialNPC tnpc, boolean advanceOnFinish) {
        PlayerProgressManager ppm = plugin.getPlayerProgressManager();
        String prefix = color(plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        if (!ppm.isInDialogue(player) || ppm.getActiveTutorialNpcId(player) != tnpc.getId())
            ppm.startDialogue(player, tnpc.getId());

        List<String> lines = tnpc.getDialogue();
        int lineIndex = ppm.getDialogueLine(player);

        if (lines.isEmpty()) {
            player.sendMessage(prefix + color("&7This NPC has nothing to say yet."));
            ppm.endDialogue(player);
            return;
        }

        String separator = color(plugin.getConfig().getString("dialogue.separator", "&8&m------------------------------"));
        player.sendMessage(separator);
        player.sendMessage(color(lines.get(lineIndex)));

        if (lineIndex >= lines.size() - 1) {
            player.sendMessage(color(plugin.getConfig().getString("dialogue.finished-message",
                    "&aConversation complete! Head to the next NPC.")));
            player.sendMessage(separator);
            ppm.endDialogue(player);
            if (advanceOnFinish) {
                ppm.advance(player);
                if (ppm.hasCompletedAll(player)) {
                    player.sendMessage(prefix + color("&6&lYou have completed the entire tutorial! Well done!"));
                    if (plugin.getConfig().getBoolean("progression.broadcast-completion", false)) {
                        String broadcast = color(plugin.getConfig().getString(
                                "progression.completion-broadcast", "&6&l{player} &ehas completed the tutorial!"))
                                .replace("{player}", player.getName());
                        player.getServer().broadcastMessage(broadcast);
                    }
                }
            }
        } else {
            player.sendMessage(color(plugin.getConfig().getString("dialogue.click-to-continue", "&7&o(Click to continue)")));
            player.sendMessage(separator);
            ppm.advanceDialogueLine(player);
        }
    }
}
