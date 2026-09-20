package com.benim.megastack;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public class MegaStackPlugin extends JavaPlugin implements Listener {

    // 9999 yerine 1 kalmasi gereken ozel itemler (isim sonu eslesmeyenler)
    private static final Set<String> SPECIAL_ONE_STACK = Set.of(
            "BOW", "CROSSBOW", "TRIDENT", "SHIELD", "ELYTRA", "SHEARS",
            "FISHING_ROD", "FLINT_AND_STEEL", "MACE", "TURTLE_HELMET",
            "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK", "BRUSH",
            "LEATHER_HORSE_ARMOR", "IRON_HORSE_ARMOR",
            "GOLDEN_HORSE_ARMOR", "DIAMOND_HORSE_ARMOR"
    );

    private static final int BIG_STACK = 9999;
    private static final int SMALL_STACK = 1;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        // Periyodik tam tarama: online oyuncularin envanterleri + yerdeki itemler.
        // Kacan bir event olsa bile bu tarama her seyi 20 saniyede bir duzeltir.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                fixInventory(p.getInventory());
                fixItem(p.getItemOnCursor());
            }
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Item itemEntity) {
                        ItemStack stack = itemEntity.getItemStack();
                        if (fixItem(stack)) {
                            itemEntity.setItemStack(stack);
                        }
                    }
                }
            }
        }, 100L, 400L); // 5 sn sonra basla, her 20 sn'de bir tekrarla

        getLogger().info("MegaStack aktif! Zirh/alet/silah haric her item 9999 stacklenebilir.");
    }

    // ----------------- Ana mantik -----------------

    private boolean isArmorToolOrWeapon(Material type) {
        String n = type.name();
        return n.endsWith("_SWORD")
                || n.endsWith("_AXE")
                || n.endsWith("_PICKAXE")
                || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE")
                || n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS")
                || SPECIAL_ONE_STACK.contains(n);
    }

    /**
     * Verilen ItemStack'in max stack size'ini kurala gore ayarlar.
     * Degisiklik yapildiysa true doner.
     */
    private boolean fixItem(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        if (type == Material.AIR) return false;

        int target = isArmorToolOrWeapon(type) ? SMALL_STACK : BIG_STACK;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Integer current = meta.getMaxStackSize();
        if (current != null && current == target) return false;

        meta.setMaxStackSize(target);
        item.setItemMeta(meta);
        return true;
    }

    private void fixInventory(Inventory inv) {
        if (inv == null) return;
        ItemStack[] contents = inv.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (fixItem(contents[i])) {
                changed = true;
            }
        }
        if (changed) {
            inv.setContents(contents);
        }
    }

    // ----------------- Event dinleyicileri -----------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> fixInventory(event.getPlayer().getInventory()), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        fixItem(event.getCurrentItem());
        fixItem(event.getCursor());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        for (ItemStack item : event.getNewItems().values()) {
            fixItem(item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        fixInventory(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack stack = entity.getItemStack();
        if (fixItem(stack)) {
            entity.setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (fixItem(stack)) {
            entity.setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDrop(BlockDropItemEvent event) {
        event.getItems().forEach(item -> {
            ItemStack stack = item.getItemStack();
            if (fixItem(stack)) {
                item.setItemStack(stack);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        fixItem(event.getInventory().getResult());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraft(CraftItemEvent event) {
        fixItem(event.getCurrentItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        // Firindan cikan itemin miktari zaten envanterde; bir sonraki
        // periyodik tarama veya InventoryClickEvent yakalar, ekstra islem gerekmez.
    }
}
