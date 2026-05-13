package com.tutorialnpcs.commands;

import com.tutorialnpcs.TutorialNPCsPlugin;
import com.tutorialnpcs.model.TutorialNPC;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

import static com.tutorialnpcs.TutorialNPCsPlugin.color;

public class TnpcCommand implements CommandExecutor, TabCompleter {

    private final TutorialNPCsPlugin plugin;

    public TnpcCommand(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    private String prefix() {
        return color(plugin.getConfig().getString("prefix", "&8[&bTutorial&8] &r"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tutorialnpcs.admin")) {
            sender.sendMessage(prefix() + color("&cYou don't have permission."));
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(prefix() + color("&cPlayers only.")); return true; }
                if (args.length < 3) { sender.sendMessage(prefix() + color("&cUsage: /tnpc create <name> <skin>")); return true; }
                TutorialNPC npc = plugin.getNpcDataManager().createNPC(args[1], args[2], player.getLocation());
                sender.sendMessage(prefix() + color("&aCreated NPC &e" + args[1] + " &a(ID: " + npc.getId() + ")"));
            }
            case "delete" -> {
                if (args.length < 2) { sender.sendMessage(prefix() + color("&cUsage: /tnpc delete <id>")); return true; }
                int id = parseInt(args[1]);
                if (plugin.getNpcDataManager().deleteNPC(id))
                    sender.sendMessage(prefix() + color("&aDeleted NPC #" + id));
                else sender.sendMessage(prefix() + color("&cNo NPC with ID " + id));
            }
            case "list" -> {
                List<TutorialNPC> npcs = plugin.getNpcDataManager().getNPCs();
                if (npcs.isEmpty()) { sender.sendMessage(prefix() + color("&7No NPCs created yet.")); return true; }
                sender.sendMessage(color("&b&lTutorial NPCs:"));
                for (TutorialNPC n : npcs)
                    sender.sendMessage(color("  &e#" + n.getId() + " &f" + n.getName() + " &7(skin: " + n.getSkin() + ", lines: " + n.getDialogue().size() + ")"));
            }
            case "info" -> {
                if (args.length < 2) { sender.sendMessage(prefix() + color("&cUsage: /tnpc info <id>")); return true; }
                TutorialNPC n = plugin.getNpcDataManager().getNPCById(parseInt(args[1]));
                if (n == null) { sender.sendMessage(prefix() + color("&cNPC not found.")); return true; }
                sender.sendMessage(color("&b&lNPC #" + n.getId() + " - " + n.getName()));
                sender.sendMessage(color("  &7Skin: &f" + n.getSkin() + "  Citizens ID: &f" + n.getCitizensId()));
                sender.sendMessage(color("  &7Location: &f" + n.getWorld() + " " + String.format("%.1f %.1f %.1f", n.getX(), n.getY(), n.getZ())));
                sender.sendMessage(color("  &7Dialogue (" + n.getDialogue().size() + " lines):"));
                for (int i = 0; i < n.getDialogue().size(); i++)
                    sender.sendMessage(color("    &8[" + i + "] &r" + n.getDialogue().get(i)));
            }
            case "setname" -> {
                if (args.length < 3) { sender.sendMessage(prefix() + color("&cUsage: /tnpc setname <id> <name>")); return true; }
                TutorialNPC n = plugin.getNpcDataManager().getNPCById(parseInt(args[1]));
                if (n == null) { sender.sendMessage(prefix() + color("&cNPC not found.")); return true; }
                n.setName(args[2]); plugin.getNpcDataManager().saveNPCs();
                sender.sendMessage(prefix() + color("&aName set to &e" + args[2]));
            }
            case "setskin" -> {
                if (args.length < 3) { sender.sendMessage(prefix() + color("&cUsage: /tnpc setskin <id> <skin>")); return true; }
                TutorialNPC n = plugin.getNpcDataManager().getNPCById(parseInt(args[1]));
                if (n == null) { sender.sendMessage(prefix() + color("&cNPC not found.")); return true; }
                n.setSkin(args[2]);
                if (n.getCitizensId() >= 0) {
                    var npcObj = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(n.getCitizensId());
                    if (npcObj != null) npcObj.getOrAddTrait(net.citizensnpcs.trait.SkinTrait.class).setSkinName(args[2], true);
                }
                plugin.getNpcDataManager().saveNPCs();
                sender.sendMessage(prefix() + color("&aSkin set to &e" + args[2]));
            }
            case "addline" -> {
                if (args.length < 3) { sender.sendMessage(prefix() + color("&cUsage: /tnpc addline <id> <text...>")); return true; }
                TutorialNPC n = plugin.getNpcDataManager().getNPCById(parseInt(args[1]));
                if (n == null) { sender.sendMessage(prefix() + color("&cNPC not found.")); return true; }
                String line = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getNpcDataManager().addDialogueLine(n, line);
                sender.sendMessage(prefix() + color("&aAdded line [" + (n.getDialogue().size() - 1) + "]"));
            }
            case "removeline" -> {
                if (args.length < 3) { sender.sendMessage(prefix() + color("&cUsage: /tnpc removeline <id> <lineIndex>")); return true; }
                TutorialNPC n = plugin.getNpcDataManager().getNPCById(parseInt(args[1]));
                if (n == null) { sender.sendMessage(prefix() + color("&cNPC not found.")); return true; }
                if (plugin.getNpcDataManager().removeDialogueLine(n, parseInt(args[2])))
                    sender.sendMessage(prefix() + color("&aRemoved line " + args[2]));
                else sender.sendMessage(prefix() + color("&cInvalid line index."));
            }
            case "clearlines" -> {
                if (args.length < 2) { sender.sendMessage(prefix() + color("&cUsage: /tnpc clearlines <id>")); return true; }
                TutorialNPC n = plugin.getNpcDataManager().getNPCById(parseInt(args[1]));
                if (n == null) { sender.sendMessage(prefix() + color("&cNPC not found.")); return true; }
                plugin.getNpcDataManager().clearDialogue(n);
                sender.sendMessage(prefix() + color("&aCleared dialogue for NPC #" + args[1]));
            }
            case "move" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(prefix() + color("&cPlayers only.")); return true; }
                if (args.length < 2) { sender.sendMessage(prefix() + color("&cUsage: /tnpc move <id>")); return true; }
                TutorialNPC n = plugin.getNpcDataManager().getNPCById(parseInt(args[1]));
                if (n == null) { sender.sendMessage(prefix() + color("&cNPC not found.")); return true; }
                plugin.getNpcDataManager().spawnCitizensNPC(n, player.getLocation());
                sender.sendMessage(prefix() + color("&aMoved NPC #" + args[1] + " to your location."));
            }
            case "resetprogress" -> {
                if (args.length < 2) { sender.sendMessage(prefix() + color("&cUsage: /tnpc resetprogress <player>")); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(prefix() + color("&cPlayer not online.")); return true; }
                plugin.getPlayerProgressManager().resetProgress(target);
                sender.sendMessage(prefix() + color("&aReset progress for &e" + target.getName()));
            }
            case "setprogress" -> {
                if (args.length < 3) { sender.sendMessage(prefix() + color("&cUsage: /tnpc setprogress <player> <index>")); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(prefix() + color("&cPlayer not online.")); return true; }
                plugin.getPlayerProgressManager().setProgress(target, parseInt(args[2]));
                sender.sendMessage(prefix() + color("&aSet &e" + target.getName() + "&a's progress to index &e" + args[2]));
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getNpcDataManager().loadNPCs();
                sender.sendMessage(prefix() + color("&aReloaded!"));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&b&lTutorialNPCs Commands:"));
        sender.sendMessage(color("&e/tnpc create <name> <skin> &7- Create NPC at your location"));
        sender.sendMessage(color("&e/tnpc delete <id> &7- Delete an NPC"));
        sender.sendMessage(color("&e/tnpc list &7- List all NPCs"));
        sender.sendMessage(color("&e/tnpc info <id> &7- Show NPC details and dialogue"));
        sender.sendMessage(color("&e/tnpc setname <id> <name> &7- Change NPC name"));
        sender.sendMessage(color("&e/tnpc setskin <id> <skin> &7- Change NPC skin"));
        sender.sendMessage(color("&e/tnpc addline <id> <text...> &7- Add dialogue line"));
        sender.sendMessage(color("&e/tnpc removeline <id> <index> &7- Remove dialogue line"));
        sender.sendMessage(color("&e/tnpc clearlines <id> &7- Clear all dialogue"));
        sender.sendMessage(color("&e/tnpc move <id> &7- Move NPC to your location"));
        sender.sendMessage(color("&e/tnpc resetprogress <player> &7- Reset player progress"));
        sender.sendMessage(color("&e/tnpc setprogress <player> <index> &7- Set player progress"));
        sender.sendMessage(color("&e/tnpc reload &7- Reload config and NPC data"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tutorialnpcs.admin")) return List.of();
        if (args.length == 1)
            return Arrays.asList("create","delete","list","info","setname","setskin","addline","removeline","clearlines","move","resetprogress","setprogress","reload")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "delete","info","setname","setskin","addline","removeline","clearlines","move" ->
                    { return plugin.getNpcDataManager().getNPCs().stream().map(n -> String.valueOf(n.getId())).collect(Collectors.toList()); }
                case "resetprogress","setprogress" ->
                    { return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()); }
            }
        }
        return List.of();
    }

    private int parseInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return -1; } }
}
