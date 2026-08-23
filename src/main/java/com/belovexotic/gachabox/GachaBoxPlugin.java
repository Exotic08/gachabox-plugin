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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class GachaBoxPlugin extends JavaPlugin {

    private Economy economy;
    private BoxManager boxManager;
    private NamespacedKey boxIdKey;

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

    // TÍNH NĂNG GỢI Ý LỆNH (TAB COMPLETER)
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("exoticbox")) {
            if (args.length == 1) {
                commands.add("create");
                commands.add("price");
                commands.add("skin");
                commands.add("add");
                commands.add("remove");
                commands.add("info");
                StringUtil.copyPartialMatches(args[0], commands, completions);
            } else if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if (sub.equals("price") || sub.equals("skin") || sub.equals("add") || sub.equals("remove") || sub.equals("info")) {
                    commands.addAll(boxManager.getAll().keySet());
                    StringUtil.copyPartialMatches(args[1], commands, completions);
                } else if (sub.equals("create")) {
                    completions.add("<tên_hộp>");
                }
            } else if (args.length == 3) {
                String sub = args[0].toLowerCase();
                if (sub.equals("add")) {
                    commands.add("item");
                    commands.add("ecoin");
                    StringUtil.copyPartialMatches(args[2], commands, completions);
                } else if (sub.equals("price")) {
                    completions.add("<giá_tiền>");
                } else if (sub.equals("skin")) {
                    completions.add("<mã_base64>");
                } else if (sub.equals("remove")) {
                    completions.add("<số_thứ_tự>");
                }
            } else if (args.length == 4 && args[0].equalsIgnoreCase("add")) {
                completions.add("<số_lượng>");
            } else if (args.length == 5 && args[0].equalsIgnoreCase("add")) {
                completions.add("<tỉ_lệ>");
            }
        } else if (command.getName().equalsIgnoreCase("buyexoticbox")) {
            if (args.length == 1) {
                commands.addAll(boxManager.getAll().keySet());
                StringUtil.copyPartialMatches(args[0], commands, completions);
            } else if (args.length == 2) {
                commands.add("1");
                commands.add("16");
                commands.add("64");
                StringUtil.copyPartialMatches(args[1], commands, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }

    private boolean handleBuyBox(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cLệnh này chỉ dùng được trong game.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§7Dùng: /buyexoticbox <tên_hộp> [số_lượng]");
            return true;
        }

        String rawName = args[0];
        String id = BoxManager.toId(rawName);

        if (!boxManager.exists(id)) {
            player.sendMessage("§cKhông tìm thấy hộp tên \"" + rawName + "\". Kiểm tra lại tên hoặc nhờ admin tạo trước.");
            return true;
        }

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount <= 0) {
                    player.sendMessage("§cSố lượng phải lớn hơn 0.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§cSố lượng không hợp lệ.");
                return true;
            }
        }

        BoxData box = boxManager.get(id);
        double totalPrice = box.price * amount;
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
        lore.add(Component.text("Chuột Trái: Xem thông tin phần thưởng")
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Chuột Phải: Mở hộp nhận đồ ngẫu nhiên")
                .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(boxIdKey, PersistentDataType.STRING, box.id);

        if (meta instanceof SkullMeta skullMeta && box.texture != null && !box.texture.isEmpty()) {
            try {
                Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
                Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

                Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "GachaBox");
                Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", box.texture);

                Object properties = profileClass.getMethod("getProperties").invoke(profile);
                properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, "textures", property);

                Field profileField = skullMeta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(skullMeta, profile);
            } catch (Exception e) {
                getLogger().warning("Khong the load skin cho hop: " + box.displayName);
            }
        }

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
                    sender.sendMessage("§7Dùng: /exoticbox create <tên_hộp>");
                    return true;
                }
                String rawName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                String id = BoxManager.toId(rawName);

                if (boxManager.exists(id)) {
                    sender.sendMessage("§cHộp tên này đã tồn tại rồi.");
                    return true;
                }

                boxManager.createBox(rawName);
                sender.sendMessage("§d✿ §fĐã tạo hộp mới: §e" + rawName + " §7(id: " + id + ")");
            }
            case "price" -> {
                if (args.length < 3) {
                    sender.sendMessage("§7Dùng: /exoticbox price <tên_hộp> <giá_tiền>");
                    return true;
                }
                String id = BoxManager.toId(args[1]);
                BoxData box = boxManager.get(id);
                if (box == null) {
                    sender.sendMessage("§cKhông tìm thấy hộp này.");
                    return true;
                }
                try {
                    double newPrice = Double.parseDouble(args[2]);
                    box.price = newPrice;
                    boxManager.save();
                    sender.sendMessage("§d✿ §fĐã đổi giá của hộp §e" + box.displayName + " §fthành §6" + newPrice + " Ecoin");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cGiá tiền không hợp lệ.");
                }
            }
            case "skin" -> {
                if (args.length < 3) {
                    sender.sendMessage("§7Dùng: /exoticbox skin <tên_hộp> <mã_base64_skin>");
                    return true;
                }
                String id = BoxManager.toId(args[1]);
                BoxData box = boxManager.get(id);
                if (box == null) {
                    sender.sendMessage("§cKhông tìm thấy hộp này.");
                    return true;
                }
                box.texture = args[2];
                boxManager.save();
                sender.sendMessage("§d✿ §fĐã thay đổi skin cho hộp §e" + box.displayName);
            }
            case "add" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cLệnh này cần dùng trong game (để lấy item đang cầm).");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§7Dùng: /exoticbox add <tên_hộp> item <số_lượng> <tỉ_lệ>");
                    sender.sendMessage("§7Dùng: /exoticbox add <tên_hộp> ecoin <số_ecoin> <tỉ_lệ>");
                    return true;
                }

                String id = BoxManager.toId(args[1]);
                if (!boxManager.exists(id)) {
                    sender.sendMessage("§cKhông tìm thấy hộp này. Dùng /exoticbox create trước.");
                    return true;
                }

                String rewardType = args[2].toLowerCase();

                try {
                    double amountArg = Double.parseDouble(args[3]);
                    double chance = args.length >= 5 ? Double.parseDouble(args[4]) : 1.0;

                    if (rewardType.equals("ecoin")) {
                        boxManager.addEcoinReward(id, amountArg, chance);
                        sender.sendMessage(String.format(
                                "§d✿ §fĐã thêm phần thưởng vào [%s]: §6%.0f Ecoin §f(tỉ lệ %.2f)",
                                args[1], amountArg, chance));
                    } else if (rewardType.equals("item")) {
                        ItemStack held = player.getInventory().getItemInMainHand();
                        if (held.getType() == Material.AIR) {
                            sender.sendMessage("§cBạn cần cầm vật phẩm trên tay trước khi thêm.");
                            return true;
                        }
                        boxManager.addItemReward(id, held, (int) amountArg, chance);
                        sender.sendMessage(String.format(
                                "§d✿ §fĐã thêm phần thưởng vào [%s]: §e%.0fx %s §f(tỉ lệ %.2f)",
                                args[1], amountArg, held.getType().name(), chance));
                    } else {
                        sender.sendMessage("§cLoại phần thưởng phải là 'item' hoặc 'ecoin'.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cSố lượng hoặc tỉ lệ không hợp lệ.");
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("§7Dùng: /exoticbox remove <tên_hộp> <số_thứ_tự>");
                    return true;
                }
                String id = BoxManager.toId(args[1]);
                if (!boxManager.exists(id)) {
                    sender.sendMessage("§cKhông tìm thấy hộp này.");
                    return true;
                }
                try {
                    int index = Integer.parseInt(args[2]) - 1;
                    if (boxManager.removeReward(id, index)) {
                        sender.sendMessage("§d✿ §fĐã xoá phần thưởng số §e" + args[2] + " §fkhỏi [" + args[1] + "]");
                    } else {
                        sender.sendMessage("§cSố thứ tự không hợp lệ.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cSố thứ tự không hợp lệ.");
                }
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage("§7Dùng: /exoticbox info <tên_hộp>");
                    sender.sendMessage("§7Danh sách hộp hiện có: §e" +
                            String.join(", ", boxManager.getAll().keySet()));
                    return true;
                }
                String id = BoxManager.toId(args[1]);
                BoxData box = boxManager.get(id);
                if (box == null) {
                    sender.sendMessage("§cKhông tìm thấy hộp này.");
                    return true;
                }

                List<RewardEntry> rewards = box.rewards;
                if (rewards.isEmpty()) {
                    sender.sendMessage("§7Hộp [" + box.displayName + "] chưa có phần thưởng nào.");
                    return true;
                }

                double total = rewards.stream().mapToDouble(r -> r.chance).sum();
                sender.sendMessage("§d✿ §f§lPhần thưởng hộp [" + box.displayName + "]:");
                for (int i = 0; i < rewards.size(); i++) {
                    RewardEntry r = rewards.get(i);
                    double percent = total > 0 ? (r.chance / total) * 100 : 0;
                    sender.sendMessage(String.format(
                            "§7%d. §f%s §7- %.2f%% §8(trọng số: %.2f)",
                            i + 1, r.describe(), percent, r.chance));
                }
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(" ");
        sender.sendMessage("§d✿ §lHƯỚNG DẪN LỆNH GACHA BOX §d✿");
        sender.sendMessage("§e/exoticbox create <tên_hộp> §f- Tạo một loại hộp mới.");
        sender.sendMessage("§e/exoticbox price <tên_hộp> <giá> §f- Đặt giá bán cho hộp bằng Ecoin.");
        sender.sendMessage("§e/exoticbox skin <tên_hộp> <base64> §f- Thay đổi skin (đầu hộp quà) bằng mã Base64.");
        sender.sendMessage("§e/exoticbox add <tên> item <SL> <tỉ_lệ> §f- Thêm item bạn đang cầm vào hộp.");
        sender.sendMessage("§e/exoticbox add <tên> ecoin <SL> <tỉ_lệ> §f- Thêm Ecoin vào hộp.");
        sender.sendMessage("§e/exoticbox info <tên_hộp> §f- Xem danh sách phần thưởng và tỉ lệ rớt.");
        sender.sendMessage("§e/exoticbox remove <tên_hộp> <số_TT> §f- Xoá phần thưởng dựa vào Số Thứ Tự (xem trong lệnh info).");
        sender.sendMessage(" ");
    }
}
