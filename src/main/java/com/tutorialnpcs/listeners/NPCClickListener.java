package com.tutorialnpcs.listeners;

import com.tutorialnpcs.TutorialNPCsPlugin;
import com.tutorialnpcs.model.TutorialNPC;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

public class NPCClickListener implements Listener {

    private final TutorialNPCsPlugin plugin;

    public NPCClickListener(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNPCRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        int citizensId = event.getNPC().getId();

        TutorialNPC tnpc = plugin.getNpcDataManager().getByEntityId(citizensId);
        if (tnpc == null) return; // Not one of our tutorial NPCs

        String prefix = TutorialNPCsPlugin.color(
                plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        // ── Block if already in dialogue ─────────────────────────────────────
        if (plugin.getDialogueManager().isInAutoDialogue(player)) {
            player.sendMessage(prefix + TutorialNPCsPlugin.color(
                    "&7Please wait until the current conversation finishes."));
            return;
        }

        // ── Block if already completed this NPC and revisit is off ───────────
        boolean allowRevisit = plugin.getConfig().getBoolean("progression.allow-revisit", false);
        if (plugin.getPlayerProgressManager().isCompleted(player, tnpc.getId())) {
            if (!allowRevisit) {
                player.sendMessage(prefix + TutorialNPCsPlugin.color(
                        plugin.getConfig().getString("progression.revisit-message",
                                "&7You have already spoken with this NPC.")));
            } else {
                plugin.getDialogueManager().handleClick(player, tnpc);
            }
            return;
        }

        // ── Block if this is not the next NPC in order ───────────────────────
        List<TutorialNPC> allNpcs = plugin.getNpcDataManager().getNPCs();
        int nextIdx = plugin.getPlayerProgressManager().getNextNpcIndex(player);

        // Find which index this NPC is
        int thisIdx = -1;
        for (int i = 0; i < allNpcs.size(); i++) {
            if (allNpcs.get(i).getId() == tnpc.getId()) {
                thisIdx = i;
                break;
            }
        }
        if (thisIdx < 0) return;

        if (thisIdx != nextIdx && !player.hasPermission("tutorialnpcs.bypass")) {
            // Wrong NPC — tell them who they should be talking to
            if (nextIdx < allNpcs.size()) {
                TutorialNPC required = allNpcs.get(nextIdx);
                String msg = plugin.getConfig().getString("progression.wrong-order-message",
                        "&cYou need to speak with &e{npc_name} &cfirst!");
                player.sendMessage(prefix + TutorialNPCsPlugin.color(
                        msg.replace("{npc_name}", required.getName())));
            }
            return;
        }

        // ── All checks passed — start dialogue ───────────────────────────────
        plugin.getDialogueManager().handleClick(player, tnpc);
    }
}
