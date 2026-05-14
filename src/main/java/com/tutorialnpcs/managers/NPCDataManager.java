package com.tutorialnpcs.managers;

import com.tutorialnpcs.TutorialNPCsPlugin;
import com.tutorialnpcs.model.TutorialNPC;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
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
    private final List<TutorialNPC> npcs = new ArrayList<>();
    private final Map<Integer, TutorialNPC> citizensIdMap = new HashMap<>();

    // Security limits
    private static final int MAX_NPCS = 100;
    private static final int MAX_DIALOGUE_LINES = 100;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_LINE_LENGTH = 256;

    public NPCDataManager(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Strip characters that could break YAML or inject commands */
    public static String sanitize(String input) {
        if (input == null) return "";
        // Allow colour codes (&x), letters, numbers, spaces, punctuation
        // Remove null bytes, backticks, and other dangerous chars
        return input.replace("\0", "")
                    .replace("`", "")
                    .replace("\r", "")
                    .substring(0, Math.min(input.length(), MAX_LINE_LENGTH));
    }

    public static String sanitizeName(String input) {
        if (input == null) return "NPC";
        return input.replace("\0", "")
                    .replace("`", "")
                    .replace("\r", "")
                    .substring(0, Math.min(input.length(), MAX_NAME_LENGTH));
    }

    private void loadFile() {
        String fileName = plugin.getConfig().getString("storage.npc-data-file", "npcs.yml");
        npcFile = new File(plugin.getDataFolder(), fileName);
        if (!npcFile.exists()) plugin.saveResource("npcs.yml", false);
        npcConfig = YamlConfiguration.loadConfiguration(npcFile);
    }

    private void saveFile() {
        try { npcConfig.save(npcFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not save npcs.yml", e); }
    }

    public void loadNPCs() {
        loadFile();
        npcs.clear();
        citizensIdMap.clear();

        List<Map<?, ?>> list = npcConfig.getMapList("npcs");
        for (Map<?, ?> map : list) {
            if (npcs.size() >= MAX_NPCS) {
                plugin.getLogger().warning("NPC limit (" + MAX_NPCS + ") reached, skipping remaining NPCs.");
                break;
            }
            TutorialNPC npc = new TutorialNPC();
            npc.setId(toInt(map.get("id"), 0));
            npc.setName(sanitizeName(str(map.get("name"), "NPC")));
            npc.setSkin(sanitizeName(str(map.get("skin"), "Steve")));
            npc.setCitizensId(toInt(map.get("citizens-id"), -1));
            npc.setWorld(str(map.get("world"), "world"));
            npc.setX(toDouble(map.get("x"), 0));
            npc.setY(toDouble(map.get("y"), 64));
            npc.setZ(toDouble(map.get("z"), 0));
            Object rawDialogue = map.get("dialogue");
            if (rawDialogue instanceof List<?> dl) {
                List<String> lines = new ArrayList<>();
                for (Object o : dl) {
                    if (lines.size() >= MAX_DIALOGUE_LINES) break;
                    lines.add(sanitize(o.toString()));
                }
                npc.setDialogue(lines);
            }
            npcs.add(npc);
            if (npc.getCitizensId() >= 0) citizensIdMap.put(npc.getCitizensId(), npc);
        }
        npcs.sort(Comparator.comparingInt(TutorialNPC::getId));
        plugin.getLogger().info("Loaded " + npcs.size() + " tutorial NPC(s).");
    }

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

    public NPC spawnCitizensNPC(TutorialNPC tnpc, Location loc) {
        if (tnpc == null || loc == null || loc.getWorld() == null) return null;
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        if (tnpc.getCitizensId() >= 0) {
            NPC old = registry.getById(tnpc.getCitizensId());
            if (old != null) old.destroy();
        }
        NPC npc = registry.createNPC(EntityType.PLAYER, TutorialNPCsPlugin.color(tnpc.getName()));
        npc.spawn(loc);
        SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
        skin.setSkinName(tnpc.getSkin(), true);
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
        if (tnpc == null) return;
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        if (tnpc.getCitizensId() >= 0) {
            NPC old = registry.getById(tnpc.getCitizensId());
            if (old != null) old.destroy();
        }
        citizensIdMap.remove(tnpc.getCitizensId());
        tnpc.setCitizensId(-1);
        saveNPCs();
    }

    public TutorialNPC createNPC(String name, String skin, Location loc) {
        if (npcs.size() >= MAX_NPCS) return null;
        if (loc == null || loc.getWorld() == null) return null;
        String safeName = sanitizeName(name);
        String safeSkin = sanitizeName(skin);
        int nextId = npcs.isEmpty() ? 1 : npcs.get(npcs.size() - 1).getId() + 1;
        TutorialNPC tnpc = new TutorialNPC(nextId, safeName, safeSkin,
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
        for (int i = 0; i < npcs.size(); i++) npcs.get(i).setId(i + 1);
        saveNPCs();
        return true;
    }

    public void addDialogueLine(TutorialNPC npc, String line) {
        if (npc == null || line == null) return;
        if (npc.getDialogue().size() >= MAX_DIALOGUE_LINES) return;
        npc.getDialogue().add(sanitize(line));
        saveNPCs();
    }

    public boolean removeDialogueLine(TutorialNPC npc, int index) {
        if (npc == null || index < 0 || index >= npc.getDialogue().size()) return false;
        npc.getDialogue().remove(index);
        saveNPCs();
        return true;
    }

    public void clearDialogue(TutorialNPC npc) {
        if (npc == null) return;
        npc.getDialogue().clear();
        saveNPCs();
    }

    // ── Lookups ───────────────────────────────────────────────────────────────

    public TutorialNPC getByEntityId(int citizensId) {
        return citizensIdMap.get(citizensId);
    }

    public TutorialNPC getByLocation(Location loc, double radius) {
        if (loc == null || loc.getWorld() == null) return null;
        for (TutorialNPC npc : npcs) {
            Location npcLoc = npc.getLocation();
            if (npcLoc == null || npcLoc.getWorld() == null) continue;
            if (!npcLoc.getWorld().equals(loc.getWorld())) continue;
            try {
                if (npcLoc.distance(loc) <= radius) return npc;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public void remapCitizensId(TutorialNPC tnpc, int newCitizensId) {
        if (tnpc == null) return;
        citizensIdMap.remove(tnpc.getCitizensId());
        tnpc.setCitizensId(newCitizensId);
        citizensIdMap.put(newCitizensId, tnpc);
        saveNPCs();
    }

    public TutorialNPC getNPCById(int id) {
        return npcs.stream().filter(n -> n.getId() == id).findFirst().orElse(null);
    }

    public List<TutorialNPC> getNPCs() { return Collections.unmodifiableList(npcs); }

    private int toInt(Object o, int def) { try { return Integer.parseInt(o.toString()); } catch (Exception e) { return def; } }
    private double toDouble(Object o, double def) { try { return Double.parseDouble(o.toString()); } catch (Exception e) { return def; } }
    private String str(Object o, String def) { return o == null ? def : o.toString(); }
}
