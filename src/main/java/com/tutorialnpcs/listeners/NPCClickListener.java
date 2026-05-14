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

    // Debounce map — prevents Citizens double-fire and spam clicking
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 750;

    // Rate limit — max clicks per window
    private final Map<UUID, Integer> clickCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickWindowStart = new ConcurrentHashMap<>();
    private static final int MAX_CLICKS_PER_WINDOW = 10;
    private static final long CLICK_WINDOW_MS = 5000; // 5 seconds

    public NPCClickListener(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNPCRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        // ── Debounce ─────────────────────────────────────────────────────────
        long now = System.currentTimeMillis();
        Long last = lastClick.get(uuid);
        if (last != null && now - last < CLICK_COOLDOWN_MS) return;
        lastClick.put(uuid, now);

        // ── Rate limit ────────────────────────────────────────────────────────
        if (isRateLimited(uuid, now)) {
            player.sendMessage(TutorialNPCsPlugin.color(
                    plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r")
                    + "&cYou are clicking too fast! Please slow down."));
            return;
        }

        // ── Safety checks ─────────────────────────────────────────────────────
        if (event.getNPC() == null) return;

        int citizensId = event.getNPC().getId();
        if (citizensId < 0) return;

        // ── NPC Lookup ────────────────────────────────────────────────────────
        TutorialNPC tnpc = plugin.getNpcDataManager().getByEntityId(citizensId);

        if (tnpc == null) {
            // Fall back to location lookup
            if (event.getNPC().getEntity() == null) return;
            Location npcLoc = event.getNPC().getEntity().getLocation();
            if (npcLoc == null || npcLoc.getWorld() == null) return;

            tnpc = plugin.getNpcDataManager().getByLocation(npcLoc, 2.0);
            if (tnpc != null) {
                plugin.getNpcDataManager().remapCitizensId(tnpc, citizensId);
            }
        }

        if (tnpc == null) return; // Not one of our tutorial NPCs

        String prefix = TutorialNPCsPlugin.color(
                plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        // ── Block if in dialogue ──────────────────────────────────────────────
        if (plugin.getDialogueManager().isInAutoDialogue(player)) {
            player.sendMessage(prefix + TutorialNPCsPlugin.color(
                    "&7Please wait until the current conversation finishes."));
            return;
        }

        // ── Order check ───────────────────────────────────────────────────────
        List<TutorialNPC> allNpcs = plugin.getNpcDataManager().getNPCs();
        if (allNpcs.isEmpty()) return;

        int nextIdx = plugin.getPlayerProgressManager().getNextNpcIndex(player);

        int thisIdx = -1;
        for (int i = 0; i < allNpcs.size(); i++) {
            if (allNpcs.get(i).getId() == tnpc.getId()) {
                thisIdx = i;
                break;
            }
        }
        if (thisIdx < 0) return;

        if (thisIdx > nextIdx && !player.hasPermission("tutorialnpcs.bypass")) {
            if (nextIdx < allNpcs.size()) {
                TutorialNPC required = allNpcs.get(nextIdx);
                String msg = plugin.getConfig().getString("progression.wrong-order-message",
                        "&cYou need to speak with &e{npc_name} &cfirst!");
                player.sendMessage(prefix + TutorialNPCsPlugin.color(
                        msg.replace("{npc_name}", required.getName())));
            }
            return;
        }

        // ── Revisit check ─────────────────────────────────────────────────────
        if (thisIdx < nextIdx) {
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

        // ── Start dialogue ────────────────────────────────────────────────────
        plugin.getDialogueManager().handleClick(player, tnpc);
    }

    private boolean isRateLimited(UUID uuid, long now) {
        long windowStart = clickWindowStart.getOrDefault(uuid, 0L);
        if (now - windowStart > CLICK_WINDOW_MS) {
            // Reset window
            clickWindowStart.put(uuid, now);
            clickCount.put(uuid, 1);
            return false;
        }
        int count = clickCount.getOrDefault(uuid, 0) + 1;
        clickCount.put(uuid, count);
        return count > MAX_CLICKS_PER_WINDOW;
    }

    public void cleanup(UUID uuid) {
        lastClick.remove(uuid);
        clickCount.remove(uuid);
        clickWindowStart.remove(uuid);
    }
}
