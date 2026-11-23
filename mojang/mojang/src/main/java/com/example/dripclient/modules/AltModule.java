package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * "Alt": zamraza Cie w miejscu, w ktorym bylo sie w momencie wlaczenia - takze w powietrzu,
 * bez spadania. Dziala czysto po Twojej stronie (po prostu nie wysylasz ruchu), wiec dziala
 * na kazdym serwerze, takze bez moda, np. anarchia.gg.
 */
public class AltModule extends Module {
    private Vec3d frozen;

    public AltModule() {
        super("Alt", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        frozen = mc.player != null ? mc.player.getPos() : null;
    }

    @Override
    public void onDisable() {
        frozen = null;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        if (frozen == null) frozen = mc.player.getPos();
        mc.player.setVelocity(0, 0, 0);
        mc.player.setPosition(frozen.x, frozen.y, frozen.z);
        mc.player.fallDistance = 0f;
    }
}
