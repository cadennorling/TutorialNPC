package com.tutorialnpcs.managers;

import com.tutorialnpcs.TutorialNPCsPlugin;
import com.tutorialnpcs.model.TutorialNPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.tutorialnpcs.TutorialNPCsPlugin.color;

public class HologramManager {

    private final TutorialNPCsPlugin plugin;
    private final Map<UUID, TextDisplay> playerHolograms = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;

    public HologramManager(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    public void startRefreshTask() {
        int interval = plugin.getConfig().getInt("waypoint.refresh-interval", 10);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, interval);
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) updateForPlayer(player);
    }

    public void updateForPlayer(Player player) {
        if (!plugin.getConfig().getBoolean("waypoint.enabled", true)) { removeForPlayer(player); return; }

        PlayerProgressManager ppm = plugin.getPlayerProgressManager();
        List<TutorialNPC> npcs = plugin.getNpcDataManager().getNPCs();

        if (ppm.hasCompletedAll(player) || npcs.isEmpty()) { removeForPlayer(player); return; }

        int nextIdx = ppm.getNextNpcIndex(player);
        if (nextIdx >= npcs.size()) { removeForPlayer(player); return; }

        TutorialNPC nextNpc = npcs.get(nextIdx);
        Location npcLoc = nextNpc.getLocation();
        if (npcLoc == null || !npcLoc.getWorld().equals(player.getWorld())) { removeForPlayer(player); return; }

        double height = plugin.getConfig().getDouble("waypoint.hologram-height", 2.5);
        Location holoLoc = npcLoc.clone().add(0, height, 0);

        String nameColor = plugin.getConfig().getString("waypoint.name-color", "&e&l");
        String distColor = plugin.getConfig().getString("waypoint.distance-color", "&f");
        String distLabel = plugin.getConfig().getString("waypoint.distance-label", " blocks away");
        int dist = (int) Math.round(player.getLocation().distance(npcLoc));
        String text = color(nameColor + nextNpc.getName() + "\n" + distColor + dist + distLabel);

        UUID uuid = player.getUniqueId();
        TextDisplay existing = playerHolograms.get(uuid);

        if (existing != null && !existing.isDead()) {
            existing.setText(text);
            existing.teleport(holoLoc);
        } else {
            TextDisplay td = (TextDisplay) holoLoc.getWorld().spawnEntity(holoLoc, org.bukkit.entity.EntityType.TEXT_DISPLAY);
            td.setText(text);
            td.setBillboard(Display.Billboard.CENTER);
            td.setSeeThrough(true);
            td.setDefaultBackground(false);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setShadowed(true);
            Transformation t = td.getTransformation();
            td.setTransformation(new Transformation(
                    t.getTranslation(),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1.4f, 1.4f, 1.4f),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
            playerHolograms.put(uuid, td);
        }
    }

    public void removeForPlayer(Player player) {
        TextDisplay td = playerHolograms.remove(player.getUniqueId());
        if (td != null && !td.isDead()) td.remove();
    }

    public void removeAllHolograms() {
        if (refreshTask != null) refreshTask.cancel();
        for (TextDisplay td : playerHolograms.values()) if (td != null && !td.isDead()) td.remove();
        playerHolograms.clear();
    }
}
