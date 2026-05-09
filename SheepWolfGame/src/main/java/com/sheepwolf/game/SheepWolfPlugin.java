package com.sheepwolf.game;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class SheepWolfPlugin extends JavaPlugin implements Listener {
    private final Set<UUID> sheepPlayers = new HashSet<>();
    private final Set<UUID> eliminatedSheep = new HashSet<>();
    private UUID wolf;
    private GamePhase phase = GamePhase.WAITING;
    private int secondsElapsed = 0;
    private int depositedKeys = 0;
    private boolean hallLocked = false;
    private BukkitTask tickTask;
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(new SheepWolfRestrictions(this), this);
        getLogger().info("SheepWolf enabled. Use /sw start to start a round.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("sw")) return false;
        if (args.length == 0) {
            sender.sendMessage("/sw <start|stop|reset|status|addkey>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> startRound();
            case "stop", "reset" -> endRound();
            case "status" -> sender.sendMessage(statusLine());
            case "addkey" -> {
                depositedKeys++;
                Bukkit.broadcastMessage("[SheepWolf] Key deposited: " + depositedKeys + "/" + getRequiredKeys());
                checkWinConditions();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public boolean isSheep(Player player) { return sheepPlayers.contains(player.getUniqueId()) && !eliminatedSheep.contains(player.getUniqueId()); }
    public boolean isWolf(Player player) { return wolf != null && wolf.equals(player.getUniqueId()); }
    public boolean isHallLocked() { return hallLocked; }
    public GamePhase getPhase() { return phase; }

    public void eliminateSheep(Player sheep) {
        eliminatedSheep.add(sheep.getUniqueId());
        sheep.setGameMode(GameMode.SPECTATOR);
        Location prison = getLocation("points.prison");
        if (prison != null) sheep.teleport(prison);
        checkWinConditions();
    }

    private void startRound() {
        if (phase == GamePhase.RUNNING) return;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.size() < 2) {
            Bukkit.broadcastMessage("Need at least 2 players to start.");
            return;
        }

        secondsElapsed = 0;
        depositedKeys = 0;
        hallLocked = false;
        sheepPlayers.clear();
        eliminatedSheep.clear();

        Player wolfPlayer = online.get(random.nextInt(online.size()));
        wolf = wolfPlayer.getUniqueId();

        for (Player player : online) {
            player.setGameMode(GameMode.ADVENTURE);
            player.getInventory().clear();
            if (player.getUniqueId().equals(wolf)) {
                applyWolfLoadout(player);
                teleportByKey(player, "points.wolf_spawn");
            } else {
                sheepPlayers.add(player.getUniqueId());
                applySheepLoadout(player);
                teleportToRandomSheepSpawn(player);
            }
        }

        phase = GamePhase.RUNNING;
        startTickTask();
        Bukkit.broadcastMessage("Game started! Wolf: " + wolfPlayer.getName());
    }

    private void applyWolfLoadout(Player player) {
        ItemStack sword = ItemFactory.wolfSword(false);
        player.getInventory().addItem(sword);
        var speed = Objects.requireNonNull(player.getAttribute(Attribute.MOVEMENT_SPEED));
        if (speed.getModifier(NamespacedKeys.WOLF_SPEED) == null) {
            speed.addModifier(new AttributeModifier(NamespacedKeys.WOLF_SPEED, 0.10, AttributeModifier.Operation.ADD_SCALAR));
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, true, false));
    }

    private void applySheepLoadout(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false));
    }

    private void startTickTask() {
        if (tickTask != null) tickTask.cancel();
        tickTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (phase != GamePhase.RUNNING) return;
            secondsElapsed++;
            refreshSheepEffects();

            if (secondsElapsed % 120 == 0) playSheepAmbient();
            if (secondsElapsed % 180 == 0) toggleHallLockAndEject();
            if (secondsElapsed == 600) triggerPhaseTwo();
        }, 20L, 20L);
    }

    private void toggleHallLockAndEject() {
        hallLocked = !hallLocked;
        Bukkit.broadcastMessage(hallLocked ? "大厅已封锁！" : "大厅已重新开放！");
        if (hallLocked) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isInHall(player.getLocation())) {
                    teleportToNearestHallExit(player);
                }
            }
        }
    }

    private boolean isInHall(Location loc) {
        double minX = getConfig().getDouble("hall_region.min_x", -99999);
        double minY = getConfig().getDouble("hall_region.min_y", -99999);
        double minZ = getConfig().getDouble("hall_region.min_z", -99999);
        double maxX = getConfig().getDouble("hall_region.max_x", 99999);
        double maxY = getConfig().getDouble("hall_region.max_y", 99999);
        double maxZ = getConfig().getDouble("hall_region.max_z", 99999);
        return loc.getX() >= minX && loc.getX() <= maxX && loc.getY() >= minY && loc.getY() <= maxY && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    private void teleportToNearestHallExit(Player player) {
        List<Location> exits = loadLocations("points.hall_eject");
        if (exits.isEmpty()) return;
        Location from = player.getLocation();
        Location best = exits.getFirst();
        double dist = best.distanceSquared(from);
        for (Location candidate : exits) {
            double d = candidate.distanceSquared(from);
            if (d < dist) {
                dist = d;
                best = candidate;
            }
        }
        player.teleport(best);
    }

    private void refreshSheepEffects() {
        for (UUID id : sheepPlayers) {
            if (eliminatedSheep.contains(id)) continue;
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) continue;
            if (secondsElapsed < 600 && !player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 220, 0, true, false));
            }
        }
    }

    private void playSheepAmbient() {
        for (UUID id : sheepPlayers) {
            if (eliminatedSheep.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), "entity.sheep.ambient", 1.0f, 1.0f);
        }
    }

    private void triggerPhaseTwo() {
        for (UUID id : sheepPlayers) {
            if (eliminatedSheep.contains(id)) continue;
            Player sheep = Bukkit.getPlayer(id);
            if (sheep != null) {
                sheep.removePotionEffect(PotionEffectType.INVISIBILITY);
                sheep.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false));
            }
        }
        Player wolfPlayer = wolf == null ? null : Bukkit.getPlayer(wolf);
        if (wolfPlayer != null) {
            wolfPlayer.getInventory().addItem(ItemFactory.wolfSword(true));
            Bukkit.broadcastMessage("10分钟已到：羊失去隐身并加速，狼获得终局剑！");
        }
    }

    private int getRequiredKeys() { return getConfig().getInt("required_keys", 15); }

    private void checkWinConditions() {
        long aliveSheep = sheepPlayers.stream().filter(id -> !eliminatedSheep.contains(id)).count();
        if (aliveSheep == 0) {
            Bukkit.broadcastMessage("Wolf wins! All sheep eliminated.");
            endRound();
            return;
        }
        if (depositedKeys >= getRequiredKeys()) {
            Bukkit.broadcastMessage("Sheep win! Enough keys deposited.");
            endRound();
        }
    }

    private void endRound() {
        phase = GamePhase.WAITING;
        if (tickTask != null) tickTask.cancel();
        tickTask = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getInventory().clear();
            player.removePotionEffect(PotionEffectType.REGENERATION);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.removePotionEffect(PotionEffectType.SPEED);
            player.setGameMode(GameMode.ADVENTURE);
            teleportByKey(player, "points.lobby");
        }
        sheepPlayers.clear();
        eliminatedSheep.clear();
        wolf = null;
        secondsElapsed = 0;
        depositedKeys = 0;
        hallLocked = false;
    }

    private String statusLine() {
        return "phase=" + phase + ", t=" + secondsElapsed + "s, keys=" + depositedKeys + "/" + getRequiredKeys() + ", hallLocked=" + hallLocked;
    }

    private void teleportToRandomSheepSpawn(Player player) {
        List<Location> points = loadLocations("points.sheep_spawns");
        if (points.isEmpty()) return;
        player.teleport(points.get(random.nextInt(points.size())));
    }

    private void teleportByKey(Player player, String path) {
        Location location = getLocation(path);
        if (location != null) player.teleport(location);
    }

    private List<Location> loadLocations(String path) {
        List<?> raw = getConfig().getList(path);
        if (raw == null || raw.isEmpty()) return List.of();
        List<Location> out = new ArrayList<>();
        for (Object obj : raw) {
            Location loc = parseLocation(String.valueOf(obj));
            if (loc != null) out.add(loc);
        }
        return out;
    }

    private Location getLocation(String path) {
        return parseLocation(getConfig().getString(path));
    }

    private Location parseLocation(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(",");
        if (parts.length < 3) return null;
        World world = Bukkit.getWorld(getConfig().getString("world", "world"));
        if (world == null) return null;
        return new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
    }
}
