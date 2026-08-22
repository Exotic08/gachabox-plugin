package com.belovexotic.gachabox;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

public class RewardManager {

    private final GachaBoxPlugin plugin;
    private final File file;
    private final List<RewardEntry> rewards = new ArrayList<>();
    private final Random random = new Random();

    public RewardManager(GachaBoxPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rewards.yml");
        load();
    }

    public void load() {
        rewards.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<?> list = yaml.getList("rewards");
        if (list == null) return;

        for (Object obj : list) {
            if (!(obj instanceof java.util.Map<?, ?> map)) continue;

            RewardEntry entry = new RewardEntry();
            String typeStr = String.valueOf(map.get("type"));
            entry.type = RewardEntry.Type.valueOf(typeStr);
            entry.chance = Double.parseDouble(String.valueOf(map.get("chance")));

            if (entry.type == RewardEntry.Type.ECOIN) {
                entry.ecoinAmount = Double.parseDouble(String.valueOf(map.get("ecoin-amount")));
            } else {
                entry.amount = Integer.parseInt(String.valueOf(map.get("amount")));
                String itemBase64 = String.valueOf(map.get("item-data"));
                entry.item = deserializeItem(itemBase64);
            }

            if (entry.type == RewardEntry.Type.ECOIN || entry.item != null) {
                rewards.add(entry);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<java.util.Map<String, Object>> list = new ArrayList<>();

        for (RewardEntry entry : rewards) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("type", entry.type.name());
            map.put("chance", entry.chance);

            if (entry.type == RewardEntry.Type.ECOIN) {
                map.put("ecoin-amount", entry.ecoinAmount);
            } else {
                map.put("amount", entry.amount);
                map.put("item-data", serializeItem(entry.item));
            }
            list.add(map);
        }

        yaml.set("rewards", list);
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[GachaBox] Loi luu rewards.yml: " + e.getMessage());
        }
    }

    public void addItemReward(ItemStack heldItem, int amount, double chance) {
        RewardEntry entry = new RewardEntry();
        entry.type = RewardEntry.Type.ITEM;
        entry.item = heldItem.clone();
        entry.item.setAmount(1); // lưu mẫu 1 cái, số lượng thật lấy từ "amount"
        entry.amount = amount;
        entry.chance = chance;
        rewards.add(entry);
        save();
    }

    public void addEcoinReward(double ecoinAmount, double chance) {
        RewardEntry entry = new RewardEntry();
        entry.type = RewardEntry.Type.ECOIN;
        entry.ecoinAmount = ecoinAmount;
        entry.chance = chance;
        rewards.add(entry);
        save();
    }

    public boolean removeReward(int index) {
        if (index < 0 || index >= rewards.size()) return false;
        rewards.remove(index);
        save();
        return true;
    }

    public List<RewardEntry> getRewards() {
        return rewards;
    }

    public RewardEntry rollReward() {
        if (rewards.isEmpty()) return null;

        double total = rewards.stream().mapToDouble(r -> r.chance).sum();
        if (total <= 0) return null;

        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (RewardEntry entry : rewards) {
            cumulative += entry.chance;
            if (roll <= cumulative) return entry;
        }
        return rewards.get(rewards.size() - 1);
    }

    private String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[GachaBox] Loi serialize item: " + e.getMessage());
            return "";
        }
    }

    private ItemStack deserializeItem(String base64) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            plugin.getLogger().warning("[GachaBox] Loi deserialize item: " + e.getMessage());
            return null;
        }
    }
}
