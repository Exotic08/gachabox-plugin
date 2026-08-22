package com.belovexotic.gachabox;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class GachaBoxPlugin extends JavaPlugin {

    private Economy economy;
    private RewardManager rewardManager;
    private NamespacedKey boxKey;
    private static final double BOX_PRICE = 1000.0;

    @Override
    public void onEnable() {
        boxKey = new NamespacedKey(this, "gacha_box");

        if (!setupEconomy()) {
            getLogger().severe("[GachaBox] Khong tim thay Vault/Economy! Tat plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getDataFolder().mkdirs();
        rewardManager = new RewardManager(this);

        getServer().getPluginManager().registerEvents(
                new GachaBoxListener(this, rewardManager, economy), this);

        getLogger().info("[GachaBox] Da bat thanh cong.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("buyexoticbox")) {
            return handleBuyBox(sender, args);
        }
        if (command.getName().equalsIgnoreCase("exoticbox")) {
            return handleExoticBoxAdmin(sender, args);
        }
        return false;
    }

    private boolean handleBuyBox(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLenh nay chi dung duoc trong game.");
            return true;
        }

        int amount = 1;
        if (args.length >= 1) {
            try {
                amount = Integer.parseInt(args[0]);
                if (amount <= 0) {
                    player.sendMessage("§cSo luong phai lon hon 0.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§cSo luong khong hop le.");
                return true;
            }
        }

        double totalPrice = BOX_PRICE * amount;
        double balance = economy.getBalance(player);

        if (balance < totalPrice) {
            player.sendMessage(String.format(
                    "§c✿ Bạn không đủ Ecoin! Cần §e%.0f§c, hiện có §e%.0f§c.",
                    totalPrice, balance));
            return true;
        }

        economy.withdrawPlayer(player, totalPrice);

        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);
            ItemStack box = createGachaBox();
            box.setAmount(stackSize);
            player.getInventory().addItem(box);
            remaining -= stackSize;
        }

        player.sendMessage(String.format(
                "§d✿ §fĐã mua §e%d §fhộp Exotic Box với giá §6%.0f Ecoin§f!", amount, totalPrice));
        return true;
    }

    private ItemStack createGachaBox() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✿ Exotic Box ✿")
                .color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Click chuột phải để mở hộp")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Nhận ngẫu nhiên phần thưởng")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(boxKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean handleExoticBoxAdmin(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§7Dung: /exoticbox <add item|add ecoin|remove <so_thu_tu>|info>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cLenh nay can dung trong game (de lay item dang cam).");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§7Dung: /exoticbox add item <so_luong> <ti_le>");
                    sender.sendMessage("§7Dung: /exoticbox add ecoin <so_ecoin> <ti_le>");
                    return true;
                }

                String rewardType = args[1].toLowerCase();

                try {
                    double amountArg = Double.parseDouble(args[2]);
                    double chance = args.length >= 4 ? Double.parseDouble(args[3]) : 1.0;

                    if (rewardType.equals("ecoin")) {
                        rewardManager.addEcoinReward(amountArg, chance);
                        sender.sendMessage(String.format(
                                "§d✿ §fDa them phan thuong: §6%.0f Ecoin §f(ti le %.2f)", amountArg, chance));
                    } else if (rewardType.equals("item")) {
                        ItemStack held = player.getInventory().getItemInMainHand();
                        if (held.getType() == Material.AIR) {
                            sender.sendMessage("§cBan can cam vat pham tren tay truoc khi them.");
                            return true;
                        }
                        rewardManager.addItemReward(held, (int) amountArg, chance);
                        sender.sendMessage(String.format(
                                "§d✿ §fDa them phan thuong: §e%.0fx %s §f(ti le %.2f)",
                                amountArg, held.getType().name(), chance));
                    } else {
                        sender.sendMessage("§cLoai phan thuong phai la 'item' hoac 'ecoin'.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cSo luong hoac ti le khong hop le.");
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage("§7Dung: /exoticbox remove <so_thu_tu> (xem so thu tu qua /exoticbox info)");
                    return true;
                }
                try {
                    int index = Integer.parseInt(args[1]) - 1; // người dùng nhập 1-based, code 0-based
                    if (rewardManager.removeReward(index)) {
                        sender.sendMessage("§d✿ §fDa xoa phan thuong so §e" + args[1] + "§f.");
                    } else {
                        sender.sendMessage("§cSo thu tu khong hop le.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cSo thu tu khong hop le.");
                }
            }
            case "info" -> {
                List<RewardEntry> rewards = rewardManager.getRewards();
                if (rewards.isEmpty()) {
                    sender.sendMessage("§7Hien chua co phan thuong nao.");
                    return true;
                }

                double total = rewards.stream().mapToDouble(r -> r.chance).sum();
                sender.sendMessage("§d✿ §f§lDanh sach phan thuong Gacha Box:");
                for (int i = 0; i < rewards.size(); i++) {
                    RewardEntry r = rewards.get(i);
                    double percent = total > 0 ? (r.chance / total) * 100 : 0;
                    sender.sendMessage(String.format(
                            "§7%d. §f%s §7- %.2f%% §8(trong so: %.2f)",
                            i + 1, r.describe(), percent, r.chance));
                }
            }
            default -> sender.sendMessage("§7Dung: /exoticbox <add item|add ecoin|remove <so_thu_tu>|info>");
        }

        return true;
    }
}
