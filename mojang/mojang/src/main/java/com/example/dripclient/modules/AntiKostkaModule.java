package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Properties;

/**
 * Zapamietuje uklad dolnego paska (hotbara). Na wybrany klawisz przywraca go z calego ekwipunku.
 * Przywracanie idzie po jednej zamianie na tick (z regulowanym odstepem), wiec serwer nie dostaje
 * paczki pakietow naraz i nic sie nie zacina.
 */
public class AntiKostkaModule extends Module {
    private final String[] layout = new String[9];

    private final Setting.Key restoreKey = new Setting.Key("Klawisz przywróć", GLFW.GLFW_KEY_K);
    private final Setting.Key saveKey = new Setting.Key("Klawisz zapisz", -1);
    private final Setting.Num delay = new Setting.Num("Odstęp (ticki)", 1, 0, 5, 1);

    private boolean restoreWasDown = false;
    private boolean saveWasDown = false;

    private boolean restoring = false;
    private int attempts = 0;
    private int wait = 0;

    public AntiKostkaModule() {
        super("AntyKostka", Category.PLAYER);
        for (int i = 0; i < 9; i++) layout[i] = "";
        add(new Setting.Action("Zapisz obecny układ", this::saveLayout));
        add(new Setting.Action("Przywróć układ teraz", () -> restore(MinecraftClient.getInstance())));
        add(restoreKey);
        add(saveKey);
        add(delay);
    }

    private static String idOf(ItemStack st) {
        return st.isEmpty() ? "" : Registries.ITEM.getId(st.getItem()).toString();
    }

    private static boolean down(MinecraftClient mc, int code) {
        return code > 0 && InputUtil.isKeyPressed(mc.getWindow().getHandle(), code);
    }

    @Override
    public void onDisable() {
        restoring = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.getWindow() == null) return;
        boolean r = mc.currentScreen == null && down(mc, restoreKey.code);
        boolean s = mc.currentScreen == null && down(mc, saveKey.code);
        if (r && !restoreWasDown) restore(mc);
        if (s && !saveWasDown) saveLayout();
        restoreWasDown = r;
        saveWasDown = s;

        if (restoring) step(mc);
    }

    public void saveLayout() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        for (int i = 0; i < 9; i++) layout[i] = idOf(p.getInventory().getStack(i));
        p.sendMessage(Text.literal("Veltrix: zapisano układ paska"), true);
    }

    /** Rozpoczyna przywracanie; same zamiany robi step() po jednej na tick. */
    public void restore(MinecraftClient mc) {
        if (mc.player == null) return;
        restoring = true;
        attempts = 0;
        wait = 0;
    }

    private void finish(MinecraftClient mc) {
        restoring = false;
        if (mc.player != null) mc.player.sendMessage(Text.literal("Veltrix: przywrócono układ paska"), true);
    }

    private void step(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) {
            restoring = false;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        // otwarta skrzynia / inny kontener: nie ruszaj ekwipunku
        if (p.currentScreenHandler != p.playerScreenHandler) {
            restoring = false;
            return;
        }
        if (attempts > 30) {
            finish(mc);
            return;
        }

        ScreenHandler h = p.playerScreenHandler;
        for (int i = 0; i < 9; i++) {
            String want = layout[i];
            if (want == null || want.isEmpty()) continue;
            if (idOf(h.getSlot(36 + i).getStack()).equals(want)) continue;

            int src = -1;
            for (int s = 9; s <= 44; s++) {
                if (s >= 36) {
                    int j = s - 36;
                    if (j == i) continue;
                    // nie zabieraj przedmiotu, ktory juz lezy na swoim miejscu w pasku
                    if (!layout[j].isEmpty() && idOf(h.getSlot(s).getStack()).equals(layout[j])) continue;
                }
                if (idOf(h.getSlot(s).getStack()).equals(want)) {
                    src = s;
                    break;
                }
            }
            if (src >= 0) {
                mc.interactionManager.clickSlot(h.syncId, src, i, SlotActionType.SWAP, p);
                attempts++;
                wait = (int) delay.value;
                return; // tylko jedna zamiana na tick
            }
        }
        finish(mc); // nic wiecej do zrobienia
    }

    @Override
    public void saveExtra(Properties p) {
        p.setProperty("mod." + name + ".layout", String.join(",", layout));
    }

    @Override
    public void loadExtra(Properties p) {
        String v = p.getProperty("mod." + name + ".layout");
        if (v == null) return;
        String[] parts = v.split(",", -1);
        for (int i = 0; i < 9 && i < parts.length; i++) layout[i] = parts[i];
    }
}
