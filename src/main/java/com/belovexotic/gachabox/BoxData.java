package com.belovexotic.gachabox;

import java.util.ArrayList;
import java.util.List;

public class BoxData {
    public String id;            // định danh nội bộ (chữ thường, không dấu cách)
    public String displayName;   // tên hiển thị thật (giữ nguyên như người dùng gõ)
    public List<RewardEntry> rewards = new ArrayList<>();
}
