package com.belovexotic.gachabox;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class GachaBoxListener implements Listener {

    private final GachaBoxPlugin plugin;
    private final BoxManager boxManager;
    private final Economy economy;
    private final NamespacedKey boxIdKey;

    public GachaBoxListener(GachaBoxPlugin plugin, BoxManager boxManager, Economy economy) {
        this.plugin = plugin;
        this.boxManager = boxManager;
        this.economy = economy;
        this.boxIdKey = new NamespacedKey(plugin, "gacha_box_id");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String boxId = meta.getPersistentDataContainer().get(boxIdKey, PersistentDataType.STRING);
        if (boxId == null) return;

        event.setCancelled(true);

        RewardEntry reward = boxManager.rollReward(boxId);
        if (reward == null) {
            player.sendMessage(Component.text("✿ Hộp này hiện chưa có phần thưởng nào, liên hệ admin!")
                    .color(NamedTextColor.RED));
            return;
        }

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        if (reward.type == RewardEntry.Type.ECOIN) {
            economy.depositPlayer(player, reward.ecoinAmount);
        } else {
            ItemStack won = reward.item.clone();
            won.setAmount(reward.amount);
            player.getInventory().addItem(won);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.sendMessage(Component.text("✿ Chúc mừng! Bạn nhận được: ")
                .color(NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(reward.describe()).color(NamedTextColor.GOLD)));
    }
}
