package com.belovexotic.gachabox;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class BoxManager {

    private final GachaBoxPlugin plugin;
    private final File file;
    private final Map<String, BoxData> boxes = new LinkedHashMap<>();
    private final Random random = new Random();

    public BoxManager(GachaBoxPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boxes.yml");
        load();
    }

    public static String toId(String rawName) {
        return rawName.toLowerCase().trim().replaceAll("\\s+", "_");
    }

    public boolean exists(String id) {
        return boxes.containsKey(id);
    }

    public BoxData get(String id) {
        return boxes.get(id);
    }

    public Map<String, BoxData> getAll() {
        return boxes;
    }

    public void createBox(String rawName) {
        String id = toId(rawName);
        BoxData data = new BoxData();
        data.id = id;
        data.displayName = rawName;
        boxes.put(id, data);
        save();
    }

    public void addItemReward(String id, ItemStack heldItem, int amount, double chance) {
        BoxData box = boxes.get(id);
        if (box == null) return;

        RewardEntry entry = new RewardEntry();
        entry.type = RewardEntry.Type.ITEM;
        entry.item = heldItem.clone();
        entry.item.setAmount(1);
        entry.amount = amount;
        entry.chance = chance;
        box.rewards.add(entry);
        save();
    }

    public void addEcoinReward(String id, double ecoinAmount, double chance) {
        BoxData box = boxes.get(id);
        if (box == null) return;

        RewardEntry entry = new RewardEntry();
        entry.type = RewardEntry.Type.ECOIN;
        entry.ecoinAmount = ecoinAmount;
        entry.chance = chance;
        box.rewards.add(entry);
        save();
    }

    public boolean removeReward(String id, int index) {
        BoxData box = boxes.get(id);
        if (box == null) return false;
        if (index < 0 || index >= box.rewards.size()) return false;
        box.rewards.remove(index);
        save();
        return true;
    }

    public RewardEntry rollReward(String id) {
        BoxData box = boxes.get(id);
        if (box == null || box.rewards.isEmpty()) return null;

        double total = box.rewards.stream().mapToDouble(r -> r.chance).sum();
        if (total <= 0) return null;

        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (RewardEntry entry : box.rewards) {
            cumulative += entry.chance;
            if (roll <= cumulative) return entry;
        }
        return box.rewards.get(box.rewards.size() - 1);
    }

    public void load() {
        boxes.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.contains("boxes")) return;

        for (String id : yaml.getConfigurationSection("boxes").getKeys(false)) {
            String base = "boxes." + id;
            BoxData box = new BoxData();
            box.id = id;
            box.displayName = yaml.getString(base + ".display-name", id);

            var rewardList = yaml.getList(base + ".rewards");
            if (rewardList != null) {
                for (Object obj : rewardList) {
                    if (!(obj instanceof Map<?, ?> map)) continue;

                    RewardEntry entry = new RewardEntry();
                    entry.type = RewardEntry.Type.valueOf(String.valueOf(map.get("type")));
                    entry.chance = Double.parseDouble(String.valueOf(map.get("chance")));

                    if (entry.type == RewardEntry.Type.ECOIN) {
                        entry.ecoinAmount = Double.parseDouble(String.valueOf(map.get("ecoin-amount")));
                    } else {
                        entry.amount = Integer.parseInt(String.valueOf(map.get("amount")));
                        entry.item = deserializeItem(String.valueOf(map.get("item-data")));
                    }

                    if (entry.type == RewardEntry.Type.ECOIN || entry.item != null) {
                        box.rewards.add(entry);
                    }
                }
            }

            boxes.put(id, box);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (BoxData box : boxes.values()) {
            String base = "boxes." + box.id;
            yaml.set(base + ".display-name", box.displayName);

            java.util.List<Map<String, Object>> rewardList = new java.util.ArrayList<>();
            for (RewardEntry entry : box.rewards) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("type", entry.type.name());
                map.put("chance", entry.chance);
                if (entry.type == RewardEntry.Type.ECOIN) {
                    map.put("ecoin-amount", entry.ecoinAmount);
                } else {
                    map.put("amount", entry.amount);
                    map.put("item-data", serializeItem(entry.item));
                }
                rewardList.add(map);
            }
            yaml.set(base + ".rewards", rewardList);
        }

        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[GachaBox] Loi luu boxes.yml: " + e.getMessage());
        }
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
