package com.sheepwolf.game;

import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public class SheepWolfRestrictions implements Listener {
    private final SheepWolfPlugin plugin;

    public SheepWolfRestrictions(SheepWolfPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler public void onBreak(BlockBreakEvent e) { if (plugin.getPhase() == GamePhase.RUNNING) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (plugin.getPhase() == GamePhase.RUNNING) e.setCancelled(true); }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (plugin.getPhase() != GamePhase.RUNNING) return;
        event.setCancelled(true);
        if (event.getEntity() instanceof Player player) {
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    @EventHandler
    public void onNaturalRegen(EntityRegainHealthEvent event) {
        if (plugin.getPhase() != GamePhase.RUNNING) return;
        Entity entity = event.getEntity();
        if (entity instanceof Player player && plugin.isSheep(player)
                && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.getPhase() != GamePhase.RUNNING) return;
        if (event.getItem().getType() == Material.ROTTEN_FLESH) event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (plugin.getPhase() != GamePhase.RUNNING) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (plugin.isSheep(victim)) event.setDamage(event.getDamage() * 0.5);

        if (victim.getHealth() - event.getFinalDamage() <= 0 && plugin.isSheep(victim)) {
            event.setCancelled(true);
            victim.setHealth(victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            plugin.eliminateSheep(victim);
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (plugin.getPhase() != GamePhase.RUNNING) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!plugin.isWolf(player)) return;
        if (event.getInventory().getHolder() instanceof Chest) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.getPhase() != GamePhase.RUNNING) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.CHEST) return;
        Player player = event.getPlayer();
        String keyDrop = plugin.getConfig().getString("points.key_drop_chest", "");
        String hit = event.getClickedBlock().getX() + "," + event.getClickedBlock().getY() + "," + event.getClickedBlock().getZ();
        if (plugin.isSheep(player) && hit.equals(keyDrop) && player.getInventory().contains(Material.TRIPWIRE_HOOK)) {
            player.getInventory().removeItem(new org.bukkit.inventory.ItemStack(Material.TRIPWIRE_HOOK, 1));
            event.setCancelled(true);
            player.performCommand("sw addkey");
        }
    }
}
