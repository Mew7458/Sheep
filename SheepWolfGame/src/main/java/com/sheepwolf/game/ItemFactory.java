package com.sheepwolf.game;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemFactory {
    private ItemFactory() {}

    public static ItemStack wolfSword(boolean finalPhase) {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(finalPhase ? "§cWolf Blade §7(Final)" : "§cWolf Blade");
            sword.setItemMeta(meta);
        }
        if (finalPhase) {
            sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 255);
        }
        return sword;
    }
}
