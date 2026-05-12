package com.tutorialnpcs.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.ArrayList;
import java.util.List;

public class TutorialNPC {

    private int id;
    private String name;
    private String skin;
    private int citizensId;
    private String world;
    private double x, y, z;
    private List<String> dialogue;

    public TutorialNPC() {
        dialogue = new ArrayList<>();
        citizensId = -1;
    }

    public TutorialNPC(int id, String name, String skin, String world, double x, double y, double z) {
        this.id = id;
        this.name = name;
        this.skin = skin;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dialogue = new ArrayList<>();
        this.citizensId = -1;
    }

    public Location getLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSkin() { return skin; }
    public void setSkin(String skin) { this.skin = skin; }
    public int getCitizensId() { return citizensId; }
    public void setCitizensId(int citizensId) { this.citizensId = citizensId; }
    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }
    public List<String> getDialogue() { return dialogue; }
    public void setDialogue(List<String> dialogue) { this.dialogue = dialogue; }
}
