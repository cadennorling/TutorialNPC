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

    // UUID -> last click timestamp in ms, to prevent double-fire
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 500;

    public NPCClickListener(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNPCRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        UUID uuid = player.getUniqueId();

        // Debounce — ignore clicks within 500ms of the last one
        long now = System.currentTimeMillis();
        Long last = lastClick.get(uuid);
        if (last != null && now - last < CLICK_COOLDOWN_MS) return;
        lastClick.put(uuid, now);

        int citizensId = event.getNPC().getId();
        TutorialNPC tnpc = plugin.getNpcDataManager().getByEntityId(citizensId);
        if (tnpc == null) return;

        String prefix = TutorialNPCsPlugin.color(
                plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));

        // Block if already in dialogue
        if (plugin.getDialogueManager().isInAutoDialogue(player)) {
            player.sendMessage(prefix + TutorialNPCsPlugin.color(
                    "&7Please wait until the current conversation finishes."));
            return;
        }

        // Block if already completed and revisit off
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

        // Block if not the next NPC in order
        List<TutorialNPC> allNpcs = plugin.getNpcDataManager().getNPCs();
        int nextIdx = plugin.getPlayerProgressManager().getNextNpcIndex(player);

        int thisIdx = -1;
        for (int i = 0; i < allNpcs.size(); i++) {
            if (allNpcs.get(i).getId() == tnpc.getId()) {
                thisIdx = i;
                break;
            }
        }
        if (thisIdx < 0) return;

        if (thisIdx != nextIdx && !player.hasPermission("tutorialnpcs.bypass")) {
            if (nextIdx < allNpcs.size()) {
                TutorialNPC required = allNpcs.get(nextIdx);
                String msg = plugin.getConfig().getString("progression.wrong-order-message",
                        "&cYou need to speak with &e{npc_name} &cfirst!");
                player.sendMessage(prefix + TutorialNPCsPlugin.color(
                        msg.replace("{npc_name}", required.getName())));
            }
            return;
        }

        plugin.getDialogueManager().handleClick(player, tnpc);
    }
}
