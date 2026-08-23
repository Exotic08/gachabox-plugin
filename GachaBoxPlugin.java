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
    private BoxManager boxManager;
    private NamespacedKey boxIdKey;
    private static final double BOX_PRICE = 1000.0;

    @Override
    public void onEnable() {
        boxIdKey = new NamespacedKey(this, "gacha_box_id");

        if (!setupEconomy()) {
            getLogger().severe("[GachaBox] Khong tim thay Vault/Economy! Tat plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getDataFolder().mkdirs();
        boxManager = new BoxManager(this);

        getServer().getPluginManager().registerEvents(
                new GachaBoxListener(this, boxManager, economy), this);

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

        if (args.length < 1) {
            player.sendMessage("§7Dung: /buyexoticbox <ten_hop> [so_luong]");
            return true;
        }

        String rawName = args[0];
        String id = BoxManager.toId(rawName);

        if (!boxManager.exists(id)) {
            player.sendMessage("§cKhong tim thay hop ten \"" + rawName + "\". Kiem tra lai ten hoac nho admin tao truoc.");
            return true;
        }

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
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

        BoxData box = boxManager.get(id);
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);
            ItemStack boxItem = createGachaBox(box);
            boxItem.setAmount(stackSize);
            player.getInventory().addItem(boxItem);
            remaining -= stackSize;
        }

        player.sendMessage(String.format(
                "§d✿ §fĐã mua §e%d §fhộp §d%s §fvới giá §6%.0f Ecoin§f!",
                amount, box.displayName, totalPrice));
        return true;
    }

    private ItemStack createGachaBox(BoxData box) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✿ " + box.displayName + " ✿")
                .color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Click chuột phải để mở hộp")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Nhận ngẫu nhiên phần thưởng")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(boxIdKey, PersistentDataType.STRING, box.id);
        item.setItemMeta(meta);
        return item;
    }

    private boolean handleExoticBoxAdmin(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage("§7Dung: /exoticbox create <ten_hop>");
                    return true;
                }
                String rawName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                String id = BoxManager.toId(rawName);

                if (boxManager.exists(id)) {
                    sender.sendMessage("§cHop ten nay da ton tai roi.");
                    return true;
                }

                boxManager.createBox(rawName);
                sender.sendMessage("§d✿ §fDa tao hop moi: §e" + rawName + " §7(id: " + id + ")");
            }
            case "add" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cLenh nay can dung trong game (de lay item dang cam).");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§7Dung: /exoticbox add <ten_hop> item <so_luong> <ti_le>");
                    sender.sendMessage("§7Dung: /exoticbox add <ten_hop> ecoin <so_ecoin> <ti_le>");
                    return true;
                }

                String id = BoxManager.toId(args[1]);
                if (!boxManager.exists(id)) {
                    sender.sendMessage("§cKhong tim thay hop nay. Dung /exoticbox create truoc.");
                    return true;
                }

                String rewardType = args[2].toLowerCase();

                try {
                    double amountArg = Double.parseDouble(args[3]);
                    double chance = args.length >= 5 ? Double.parseDouble(args[4]) : 1.0;

                    if (rewardType.equals("ecoin")) {
                        boxManager.addEcoinReward(id, amountArg, chance);
                        sender.sendMessage(String.format(
                                "§d✿ §fDa them phan thuong vao [%s]: §6%.0f Ecoin §f(ti le %.2f)",
                                args[1], amountArg, chance));
                    } else if (rewardType.equals("item")) {
                        ItemStack held = player.getInventory().getItemInMainHand();
                        if (held.getType() == Material.AIR) {
                            sender.sendMessage("§cBan can cam vat pham tren tay truoc khi them.");
                            return true;
                        }
                        boxManager.addItemReward(id, held, (int) amountArg, chance);
                        sender.sendMessage(String.format(
                                "§d✿ §fDa them phan thuong vao [%s]: §e%.0fx %s §f(ti le %.2f)",
                                args[1], amountArg, held.getType().name(), chance));
                    } else {
                        sender.sendMessage("§cLoai phan thuong phai la 'item' hoac 'ecoin'.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cSo luong hoac ti le khong hop le.");
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("§7Dung: /exoticbox remove <ten_hop> <so_thu_tu>");
                    return true;
                }
                String id = BoxManager.toId(args[1]);
                if (!boxManager.exists(id)) {
                    sender.sendMessage("§cKhong tim thay hop nay.");
                    return true;
                }
                try {
                    int index = Integer.parseInt(args[2]) - 1;
                    if (boxManager.removeReward(id, index)) {
                        sender.sendMessage("§d✿ §fDa xoa phan thuong so §e" + args[2] + " §fkhoi [" + args[1] + "]");
                    } else {
                        sender.sendMessage("§cSo thu tu khong hop le.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cSo thu tu khong hop le.");
                }
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage("§7Dung: /exoticbox info <ten_hop>");
                    sender.sendMessage("§7Danh sach hop hien co: " +
                            String.join(", ", boxManager.getAll().keySet()));
                    return true;
                }
                String id = BoxManager.toId(args[1]);
                BoxData box = boxManager.get(id);
                if (box == null) {
                    sender.sendMessage("§cKhong tim thay hop nay.");
                    return true;
                }

                List<RewardEntry> rewards = box.rewards;
                if (rewards.isEmpty()) {
                    sender.sendMessage("§7Hop [" + box.displayName + "] chua co phan thuong nao.");
                    return true;
                }

                double total = rewards.stream().mapToDouble(r -> r.chance).sum();
                sender.sendMessage("§d✿ §f§lPhan thuong hop [" + box.displayName + "]:");
                for (int i = 0; i < rewards.size(); i++) {
                    RewardEntry r = rewards.get(i);
                    double percent = total > 0 ? (r.chance / total) * 100 : 0;
                    sender.sendMessage(String.format(
                            "§7%d. §f%s §7- %.2f%% §8(trong so: %.2f)",
                            i + 1, r.describe(), percent, r.chance));
                }
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§7Dung: /exoticbox <create|add|remove|info> <ten_hop> ...");
    }
}
