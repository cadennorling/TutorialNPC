package com.tutorialnpcs.managers;

import com.tutorialnpcs.TutorialNPCsPlugin;
import com.tutorialnpcs.model.TutorialNPC;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class NPCDataManager {

    private final TutorialNPCsPlugin plugin;
    private File npcFile;
    private YamlConfiguration npcConfig;

    // Ordered list of tutorial NPCs
    private final List<TutorialNPC> npcs = new ArrayList<>();
    // Citizens ID → TutorialNPC for fast lookup on click
    private final Map<Integer, TutorialNPC> citizensIdMap = new HashMap<>();

    public NPCDataManager(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── File helpers ─────────────────────────────────────────────────────────

    private void loadFile() {
        String fileName = plugin.getConfig().getString("storage.npc-data-file", "npcs.yml");
        npcFile = new File(plugin.getDataFolder(), fileName);
        if (!npcFile.exists()) {
            plugin.saveResource("npcs.yml", false);
        }
        npcConfig = YamlConfiguration.loadConfiguration(npcFile);
    }

    private void saveFile() {
        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save npcs.yml", e);
        }
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    public void loadNPCs() {
        loadFile();
        npcs.clear();
        citizensIdMap.clear();

        List<Map<?, ?>> list = npcConfig.getMapList("npcs");
        for (Map<?, ?> map : list) {
            TutorialNPC npc = new TutorialNPC();
            npc.setId(toInt(map.get("id"), 0));
            npc.setName(str(map.get("name"), "NPC"));
            npc.setSkin(str(map.get("skin"), "Steve"));
            npc.setCitizensId(toInt(map.get("citizens-id"), -1));
            npc.setWorld(str(map.get("world"), "world"));
            npc.setX(toDouble(map.get("x"), 0));
            npc.setY(toDouble(map.get("y"), 64));
            npc.setZ(toDouble(map.get("z"), 0));

            Object rawDialogue = map.get("dialogue");
            if (rawDialogue instanceof List<?> dl) {
                List<String> lines = new ArrayList<>();
                for (Object o : dl) lines.add(o.toString());
                npc.setDialogue(lines);
            }

            npcs.add(npc);
            if (npc.getCitizensId() >= 0) {
                citizensIdMap.put(npc.getCitizensId(), npc);
            }
        }

        // Sort by id just in case
        npcs.sort(Comparator.comparingInt(TutorialNPC::getId));

        plugin.getLogger().info("Loaded " + npcs.size() + " tutorial NPC(s).");
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    public void saveNPCs() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TutorialNPC npc : npcs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", npc.getId());
            map.put("name", npc.getName());
            map.put("skin", npc.getSkin());
            map.put("citizens-id", npc.getCitizensId());
            map.put("world", npc.getWorld());
            map.put("x", npc.getX());
            map.put("y", npc.getY());
            map.put("z", npc.getZ());
            map.put("dialogue", npc.getDialogue());
            list.add(map);
        }
        npcConfig.set("npcs", list);
        saveFile();
    }

    // ── Spawn / despawn Citizens NPCs ────────────────────────────────────────

    public NPC spawnCitizensNPC(TutorialNPC tnpc, Location loc) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();

        // Remove old if exists
        if (tnpc.getCitizensId() >= 0) {
            NPC old = registry.getById(tnpc.getCitizensId());
            if (old != null) old.destroy();
        }

        NPC npc = registry.createNPC(EntityType.PLAYER, TutorialNPCsPlugin.color(tnpc.getName()));
        npc.spawn(loc);

        // Apply skin
        SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
        skin.setSkinName(tnpc.getSkin(), true);

        // Make NPC protected (don't take damage, don't move)
        npc.setProtected(true);

        tnpc.setCitizensId(npc.getId());
        tnpc.setWorld(loc.getWorld().getName());
        tnpc.setX(loc.getX());
        tnpc.setY(loc.getY());
        tnpc.setZ(loc.getZ());

        citizensIdMap.put(npc.getId(), tnpc);
        saveNPCs();
        return npc;
    }

    public void removeCitizensNPC(TutorialNPC tnpc) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        if (tnpc.getCitizensId() >= 0) {
            NPC old = registry.getById(tnpc.getCitizensId());
            if (old != null) old.destroy();
        }
        citizensIdMap.remove(tnpc.getCitizensId());
        tnpc.setCitizensId(-1);
        saveNPCs();
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public TutorialNPC createNPC(String name, String skin, Location loc) {
        int nextId = npcs.isEmpty() ? 1 : npcs.get(npcs.size() - 1).getId() + 1;
        TutorialNPC tnpc = new TutorialNPC(nextId, name, skin,
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        npcs.add(tnpc);
        spawnCitizensNPC(tnpc, loc);
        saveNPCs();
        return tnpc;
    }

    public boolean deleteNPC(int id) {
        TutorialNPC target = getNPCById(id);
        if (target == null) return false;
        removeCitizensNPC(target);
        npcs.removeIf(n -> n.getId() == id);
        // Re-number remaining
        for (int i = 0; i < npcs.size(); i++) {
            npcs.get(i).setId(i + 1);
        }
        saveNPCs();
        return true;
    }

    // ── Dialogue editing ─────────────────────────────────────────────────────

    public void addDialogueLine(TutorialNPC npc, String line) {
        npc.getDialogue().add(line);
        saveNPCs();
    }

    public boolean removeDialogueLine(TutorialNPC npc, int index) {
        if (index < 0 || index >= npc.getDialogue().size()) return false;
        npc.getDialogue().remove(index);
        saveNPCs();
        return true;
    }

    public void clearDialogue(TutorialNPC npc) {
        npc.getDialogue().clear();
        saveNPCs();
    }

    // ── Lookups ──────────────────────────────────────────────────────────────

    public TutorialNPC getByEntityId(int citizensId) {
        return citizensIdMap.get(citizensId);
    }

    public TutorialNPC getByLocation(Location loc, double radius) {
        for (TutorialNPC npc : npcs) {
            Location npcLoc = npc.getLocation();
            if (npcLoc == null) continue;
            if (!npcLoc.getWorld().equals(loc.getWorld())) continue;
            if (npcLoc.distance(loc) <= radius) return npc;
        }
        return null;
    }

    public void remapCitizensId(TutorialNPC tnpc, int newCitizensId) {
        citizensIdMap.remove(tnpc.getCitizensId());
        tnpc.setCitizensId(newCitizensId);
        citizensIdMap.put(newCitizensId, tnpc);
        saveNPCs();
    }

    public TutorialNPC getNPCById(int id) {
        return npcs.stream().filter(n -> n.getId() == id).findFirst().orElse(null);
    }

    public List<TutorialNPC> getNPCs() { return Collections.unmodifiableList(npcs); }

    public String debugMappings() {
        StringBuilder sb = new StringBuilder("{");
        citizensIdMap.forEach((k, v) -> sb.append(k).append("->").append(v.getId()).append("(").append(v.getName()).append("), "));
        sb.append("}");
        return sb.toString();
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    private int toInt(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(o.toString()); } catch (NumberFormatException e) { return def; }
    }

    private double toDouble(Object o, double def) {
        if (o == null) return def;
        try { return Double.parseDouble(o.toString()); } catch (NumberFormatException e) { return def; }
    }

    private String str(Object o, String def) {
        return o == null ? def : o.toString();
    }
}
