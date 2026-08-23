package com.belovexotic.gachabox;

import org.bukkit.inventory.ItemStack;

public class RewardEntry {

    public enum Type { ITEM, ECOIN }

    public Type type;
    public ItemStack item;   // dùng khi type = ITEM
    public double ecoinAmount; // dùng khi type = ECOIN
    public int amount;       // số lượng item khi trúng (chỉ dùng cho ITEM)
    public double chance;    // trọng số (không cần cộng đúng 100, tự tính % khi hiển thị)

    public RewardEntry() {}

    public String describe() {
        if (type == Type.ECOIN) {
            return (int) ecoinAmount + " Ecoin";
        } else {
            String name = item.getType().name();
            return amount + "x " + name;
        }
    }
}
