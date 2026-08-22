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
    private final RewardManager rewardManager;
    private final Economy economy;
    private final NamespacedKey boxKey;

    public GachaBoxListener(GachaBoxPlugin plugin, RewardManager rewardManager, Economy economy) {
        this.plugin = plugin;
        this.rewardManager = rewardManager;
        this.economy = economy;
        this.boxKey = new NamespacedKey(plugin, "gacha_box");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!meta.getPersistentDataContainer().has(boxKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        RewardEntry reward = rewardManager.rollReward();
        if (reward == null) {
            player.sendMessage(Component.text("✿ Hiện chưa có phần thưởng nào được thiết lập, liên hệ admin!")
                    .color(NamedTextColor.RED));
            return;
        }

        // Trừ 1 hộp khỏi tay
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
