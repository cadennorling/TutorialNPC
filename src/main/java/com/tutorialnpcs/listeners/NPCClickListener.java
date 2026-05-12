package com.tutorialnpcs.listeners;

import com.tutorialnpcs.TutorialNPCsPlugin;
import com.tutorialnpcs.model.TutorialNPC;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class NPCClickListener implements Listener {

    private final TutorialNPCsPlugin plugin;

    public NPCClickListener(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        int citizensId = event.getNPC().getId();
        TutorialNPC tnpc = plugin.getNpcDataManager().getByEntityId(citizensId);
        if (tnpc == null) return;
        plugin.getDialogueManager().handleClick(player, tnpc);
    }
}
