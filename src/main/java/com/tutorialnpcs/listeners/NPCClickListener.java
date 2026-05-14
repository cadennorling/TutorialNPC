package com.tutorialnpcs.listeners;

import com.tutorialnpcs.TutorialNPCsPlugin;
import com.tutorialnpcs.model.TutorialNPC;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.Location;
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
        if (last != null && now - last < CLICK_COOLDOWN_MS) {
            plugin.getLogger().info("[DEBUG] Debounced click from " + player.getName());
            return;
        }
        lastClick.put(uuid, now);

        int citizensId = event.getNPC().getId();
        plugin.getLogger().info("[DEBUG] " + player.getName() + " clicked Citizens ID: " + citizensId);

        // Try lookup by Citizens ID first
        TutorialNPC tnpc = plugin.getNpcDataManager().getByEntityId(citizensId);

        if (tnpc == null) {
            // Fall back to location
            Location npcLoc = event.getNPC().getEntity() != null
                    ? event.getNPC().getEntity().getLocation() : null;
            plugin.getLogger().info("[DEBUG] No ID match, trying location: " + npcLoc);
            if (npcLoc != null) {
                tnpc = plugin.getNpcDataManager().getByLocation(npcLoc, 2.0);
                if (tnpc != null) {
                    plugin.getLogger().info("[DEBUG] Found by location! TutorialNPC #" + tnpc.getId() + " remapping citizens ID " + citizensId);
                    plugin.getNpcDataManager().remapCitizensId(tnpc, citizensId);
                } else {
                    plugin.getLogger().info("[DEBUG] Not found by location either. Not a tutorial NPC.");
                }
            }
        } else {
            plugin.getLogger().info("[DEBUG] Found by ID: TutorialNPC #" + tnpc.getId() + " name: " + tnpc.getName());
        }

        if (tnpc == null) return;

        String prefix = TutorialNPCsPlugin.color(
                plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        // Block if in dialogue
        if (plugin.getDialogueManager().isInAutoDialogue(player)) {
            plugin.getLogger().info("[DEBUG] Blocked - already in dialogue");
            player.sendMessage(prefix + TutorialNPCsPlugin.color("&7Please wait until the current conversation finishes."));
            return;
        }

        List<TutorialNPC> allNpcs = plugin.getNpcDataManager().getNPCs();
        int nextIdx = plugin.getPlayerProgressManager().getNextNpcIndex(player);

        int thisIdx = -1;
        for (int i = 0; i < allNpcs.size(); i++) {
            if (allNpcs.get(i).getId() == tnpc.getId()) {
                thisIdx = i;
                break;
            }
        }

        plugin.getLogger().info("[DEBUG] thisIdx=" + thisIdx + " nextIdx=" + nextIdx + " hasBypass=" + player.hasPermission("tutorialnpcs.bypass"));

        if (thisIdx < 0) {
            plugin.getLogger().info("[DEBUG] thisIdx is -1, returning");
            return;
        }

        // ORDER CHECK
        if (thisIdx > nextIdx) {
            if (player.hasPermission("tutorialnpcs.bypass")) {
                plugin.getLogger().info("[DEBUG] Out of order but player has bypass permission - allowing");
            } else {
                TutorialNPC required = allNpcs.get(nextIdx);
                plugin.getLogger().info("[DEBUG] BLOCKED out of order. Need NPC #" + (nextIdx+1) + " (" + required.getName() + ")");
                String msg = plugin.getConfig().getString("progression.wrong-order-message",
                        "&cYou need to speak with &e{npc_name} &cfirst!");
                player.sendMessage(prefix + TutorialNPCsPlugin.color(msg.replace("{npc_name}", required.getName())));
                return;
            }
        }

        // REVISIT CHECK
        if (thisIdx < nextIdx) {
            boolean allowRevisit = plugin.getConfig().getBoolean("progression.allow-revisit", false);
            plugin.getLogger().info("[DEBUG] Already completed NPC, allowRevisit=" + allowRevisit);
            if (!allowRevisit) {
                player.sendMessage(prefix + TutorialNPCsPlugin.color(
                        plugin.getConfig().getString("progression.revisit-message", "&7You have already spoken with this NPC.")));
            } else {
                plugin.getDialogueManager().handleClick(player, tnpc);
            }
            return;
        }

        plugin.getLogger().info("[DEBUG] Starting dialogue with TutorialNPC #" + tnpc.getId());
        plugin.getDialogueManager().handleClick(player, tnpc);
    }
}
