package com.tutorialnpcs.listeners;

import com.tutorialnpcs.TutorialNPCsPlugin;
import com.tutorialnpcs.model.TutorialNPC;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NPCClickListener implements Listener {

    private final TutorialNPCsPlugin plugin;

    // Debounce to prevent Citizens double-firing
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 500;

    public NPCClickListener(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNPCRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        UUID uuid = player.getUniqueId();

        // Debounce
        long now = System.currentTimeMillis();
        Long last = lastClick.get(uuid);
        if (last != null && now - last < CLICK_COOLDOWN_MS) return;
        lastClick.put(uuid, now);

        int citizensId = event.getNPC().getId();
        TutorialNPC tnpc = plugin.getNpcDataManager().getByEntityId(citizensId);
        if (tnpc == null) return; // Not one of our tutorial NPCs

        String prefix = TutorialNPCsPlugin.color(
                plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        // Block if already in dialogue with any NPC
        if (plugin.getDialogueManager().isInAutoDialogue(player)) {
            player.sendMessage(prefix + TutorialNPCsPlugin.color(
                    "&7Please wait until the current conversation finishes."));
            return;
        }

        List<TutorialNPC> allNpcs = plugin.getNpcDataManager().getNPCs();
        int nextIdx = plugin.getPlayerProgressManager().getNextNpcIndex(player);

        // Find this NPC's position in the order
        int thisIdx = -1;
        for (int i = 0; i < allNpcs.size(); i++) {
            if (allNpcs.get(i).getId() == tnpc.getId()) {
                thisIdx = i;
                break;
            }
        }
        if (thisIdx < 0) return;

        // ── ORDER CHECK FIRST — always runs before anything else ─────────────
        // If this NPC is ahead of where the player is, block it
        if (thisIdx > nextIdx && !player.hasPermission("tutorialnpcs.bypass")) {
            TutorialNPC required = allNpcs.get(nextIdx);
            String msg = plugin.getConfig().getString("progression.wrong-order-message",
                    "&cYou need to speak with &e{npc_name} &cfirst!");
            player.sendMessage(prefix + TutorialNPCsPlugin.color(
                    msg.replace("{npc_name}", required.getName())));
            return;
        }

        // ── REVISIT CHECK — only for NPCs the player has already completed ───
        if (thisIdx < nextIdx) {
            // This is a previously completed NPC
            boolean allowRevisit = plugin.getConfig().getBoolean("progression.allow-revisit", false);
            if (!allowRevisit) {
                player.sendMessage(prefix + TutorialNPCsPlugin.color(
                        plugin.getConfig().getString("progression.revisit-message",
                                "&7You have already spoken with this NPC.")));
            } else {
                plugin.getDialogueManager().handleClick(player, tnpc);
            }
            return;
        }

        // ── thisIdx == nextIdx — this is the correct next NPC ────────────────
        plugin.getDialogueManager().handleClick(player, tnpc);
    }
}
