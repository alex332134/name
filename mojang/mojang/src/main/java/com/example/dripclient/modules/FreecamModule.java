package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Freecam: kamera odlatuje od postaci, a postac stoi w miejscu. Kliknieciem niszczysz i stawiasz bloki
 * tam, gdzie patrzy kamera - ale serwer przyjmie to tylko w normalnym zasiegu od ciala.
 */
public class FreecamModule extends Module {
    private final Setting.Num speed = add(new Setting.Num("Prędkość", 8, 1, 40, 1));

    private ArmorStandEntity cam;
    private Input savedInput;
    private Vec3d pos;
    private long last = 0;

    public FreecamModule() {
        super("Freecam", Category.RENDER);
    }

    @Override
    public void onLoad() {
        enabled = false; // freecam nigdy nie startuje wlaczony
    }

    @Override
    public void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            enabled = false;
            return;
        }
        try {
            pos = mc.player.getEyePos();
            cam = new ArmorStandEntity(mc.world, pos.x, pos.y, pos.z);
            cam.setInvisible(true);
            cam.setNoGravity(true);
            cam.setInvulnerable(true);
            savedInput = mc.player.input;
            mc.player.input = new Input(); // postac przestaje reagowac na klawisze
            mc.setCameraEntity(cam);
            last = System.nanoTime();
            place(mc.player.getYaw(), mc.player.getPitch());
        } catch (Throwable t) {
            stop();
            enabled = false;
        }
    }

    @Override
    public void onDisable() {
        stop();
    }

    private void stop() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            if (savedInput != null) mc.player.input = savedInput;
            mc.setCameraEntity(mc.player);
        }
        cam = null;
        savedInput = null;
    }

    private void place(float yaw, float pitch) {
        double y = pos.y - cam.getStandingEyeHeight();
        cam.setPosition(pos.x, y, pos.z);
        cam.prevX = pos.x;
        cam.prevY = y;
        cam.prevZ = pos.z;
        cam.setYaw(yaw);
        cam.setPitch(pitch);
        cam.prevYaw = yaw;
        cam.prevPitch = pitch;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            // swiat zniknal (wyjscie z gry) - wylacz bez ruszania nieistniejacego gracza
            enabled = false;
            cam = null;
            savedInput = null;
        }
    }

    private static boolean held(MinecraftClient mc, KeyBinding kb, int rawKey) {
        return kb.isPressed() || InputUtil.isKeyPressed(mc.getWindow().getHandle(), rawKey);
    }

    @Override
    public void onFrame(MinecraftClient mc, float delta) {
        if (cam == null || mc.player == null) return;
        long now = System.nanoTime();
        float dt = MathHelper.clamp((now - last) / 1_000_000_000f, 0f, 0.1f);
        last = now;

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        Vec3d forward = Vec3d.fromPolar(pitch, yaw);
        double yawRad = Math.toRadians(yaw);
        // Wektor "w prawo" wzgledem kierunku patrzenia (ten sam wzor co uzywa gra do ruchu bokiem).
        // Zly znak tutaj byl przyczyna, dla ktorej A/D nic nie robily - teraz jest poprawiony.
        Vec3d right = new Vec3d(Math.cos(yawRad), 0, Math.sin(yawRad));

        // klawisze: przypisane w grze LUB standardowe WASD / spacja / shift / ctrl (surowy odczyt)
        Vec3d move = Vec3d.ZERO;
        if (held(mc, mc.options.forwardKey, GLFW.GLFW_KEY_W)) move = move.add(forward);
        if (held(mc, mc.options.backKey, GLFW.GLFW_KEY_S)) move = move.subtract(forward);
        if (held(mc, mc.options.rightKey, GLFW.GLFW_KEY_D)) move = move.add(right);
        if (held(mc, mc.options.leftKey, GLFW.GLFW_KEY_A)) move = move.subtract(right);
        if (held(mc, mc.options.jumpKey, GLFW.GLFW_KEY_SPACE)) move = move.add(0, 1, 0);
        if (held(mc, mc.options.sneakKey, GLFW.GLFW_KEY_LEFT_SHIFT)) move = move.add(0, -1, 0);

        double boost = held(mc, mc.options.sprintKey, GLFW.GLFW_KEY_LEFT_CONTROL) ? 2.5 : 1.0;
        if (move.lengthSquared() > 1e-6) {
            pos = pos.add(move.normalize().multiply(speed.value * boost * dt));
        }
        place(yaw, pitch);
    }
}
