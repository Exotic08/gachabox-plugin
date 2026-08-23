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

import java.util.List;

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
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        String boxId = meta.getPersistentDataContainer().get(boxIdKey, PersistentDataType.STRING);
        if (boxId == null) return;

        // Bất kể bấm chuột trái hay phải đều huỷ Event để ngăn đặt block xuống đất
        event.setCancelled(true);

        // ===== 1. CLICK CHUỘT TRÁI ĐỂ XEM THÔNG TIN =====
        if (event.getAction().isLeftClick()) {
            BoxData box = boxManager.get(boxId);
            if (box == null) {
                player.sendMessage(Component.text("✿ Hộp này không còn tồn tại trên hệ thống!")
                        .color(NamedTextColor.RED));
                return;
            }

            List<RewardEntry> rewards = box.rewards;
            if (rewards.isEmpty()) {
                player.sendMessage(Component.text("✿ Hộp [" + box.displayName + "] chưa có phần thưởng nào.")
                        .color(NamedTextColor.GRAY));
                return;
            }

            double total = rewards.stream().mapToDouble(r -> r.chance).sum();
            player.sendMessage(Component.text("✿ Phần thưởng hộp [" + box.displayName + "]:")
                    .color(NamedTextColor.LIGHT_PURPLE));
            
            for (int i = 0; i < rewards.size(); i++) {
                RewardEntry r = rewards.get(i);
                double percent = total > 0 ? (r.chance / total) * 100 : 0;
                player.sendMessage(String.format("§7%d. §f%s §7- %.2f%%", i + 1, r.describe(), percent));
            }
            return;
        }

        // ===== 2. CLICK CHUỘT PHẢI ĐỂ MỞ HỘP =====
        if (event.getAction().isRightClick()) {
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
                // Nếu túi đồ đầy, vật phẩm sẽ văng xuống đất để không bị mất
                if (!player.getInventory().addItem(won).isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), won);
                }
            }

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            player.sendMessage(Component.text("✿ Chúc mừng! Bạn nhận được: ")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(reward.describe()).color(NamedTextColor.GOLD)));
        }
    }
}
