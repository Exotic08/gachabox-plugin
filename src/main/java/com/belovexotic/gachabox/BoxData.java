package com.belovexotic.gachabox;

import java.util.ArrayList;
import java.util.List;

public class BoxData {
    public String id;            // định danh nội bộ (chữ thường, không dấu cách)
    public String displayName;   // tên hiển thị thật (giữ nguyên như người dùng gõ)
    public double price = 1000.0; // giá mặc định
    // Mặc định là Skin đầu hộp quà màu đỏ
    public String texture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmNlZjlhYTE0ZTg4NDc3M2VhYzEzNGE0ZWU4OTcyMDYzZjQ2NmRlNjc4MzYzY2Y3YjFhMjFhODViNyJ9fX0=";
    public List<RewardEntry> rewards = new ArrayList<>();
}
