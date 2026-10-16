package com.free.hark;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

/**
 * HarkFree 启动控制台 (MainActivity)
 * 提供 4 个独立物理进程的槽位入口，杜绝多账号串号。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialCardView cardSlot0 = findViewById(R.id.card_slot0);
        MaterialCardView cardSlot1 = findViewById(R.id.card_slot1);
        MaterialCardView cardSlot2 = findViewById(R.id.card_slot2);
        MaterialCardView cardSlot3 = findViewById(R.id.card_slot3);

        cardSlot0.setOnClickListener(v -> launchSlot(SlotActivity.Slot0.class, "账号槽位 1"));
        cardSlot1.setOnClickListener(v -> launchSlot(SlotActivity.Slot1.class, "账号槽位 2"));
        cardSlot2.setOnClickListener(v -> launchSlot(SlotActivity.Slot2.class, "账号槽位 3"));
        cardSlot3.setOnClickListener(v -> launchSlot(SlotActivity.Slot3.class, "账号槽位 4"));
    }

    private void launchSlot(Class<?> slotClass, String slotName) {
        Intent intent = new Intent(this, slotClass);
        intent.putExtra("SLOT_NAME", slotName);
        startActivity(intent);
    }
}
