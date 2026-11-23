package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Draw;
import com.example.dripclient.Module;
import com.example.dripclient.Projection;
import com.example.dripclient.Setting;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wykrywa trapy po duzej liczbie obrazow, ramek, wagonikow, tabliczek i banerow w jednym miejscu.
 * Nad trapem pokazuje napis TRAP z odlegloscia i wysyla komunikat. Trap jest pamietany 30 s,
 * nawet jesli AntiTrap usunie obiekty.
 */
public class TrapDetectModule extends Module {
    private final Setting.Num threshold = add(new Setting.Num("Czułość (obiekty)", 8, 3, 30, 1));
    private final Setting.Num range = add(new Setting.Num("Zasięg", 64, 16, 128, 16));
    private final Setting.Bool notify = add(new Setting.Bool("Komunikat", true));

    private static final class Trap {
        Vec3d pos;
        long until;

        Trap(Vec3d pos, long until) {
            this.pos = pos;
            this.until = until;
        }
    }

    private final List<Trap> traps = new ArrayList<>();
    private final List<Vec3d> signPoints = new ArrayList<>();
    private int tickN = 0;

    public TrapDetectModule() {
        super("TrapDetect", Category.RENDER);
    }

    @Override
    public void onDisable() {
        traps.clear();
        signPoints.clear();
    }

    private static long key(int cx, int cy, int cz) {
        return ((long) cx & 0x1FFFFF) << 42 | ((long) cy & 0x1FFFFF) << 21 | ((long) cz & 0x1FFFFF);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        tickN++;
        long now = System.nanoTime();
        Vec3d me = mc.player.getPos();
        double r2 = range.value * range.value;

        // tabliczki i banery (bloki z danymi) - skan co sekunde
        if (tickN % 20 == 1) {
            signPoints.clear();
            int cr = (int) Math.ceil(range.value / 16.0) + 1;
            int pcx = mc.player.getBlockPos().getX() >> 4;
            int pcz = mc.player.getBlockPos().getZ() >> 4;
            for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
                for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (!(be instanceof SignBlockEntity) && !(be instanceof BannerBlockEntity)) continue;
                        Vec3d c = Vec3d.ofCenter(be.getPos());
                        if (c.squaredDistanceTo(me) <= r2) signPoints.add(c);
                    }
                }
            }
        }

        // byty co tick (zanim AntiTrap zdazy je usunac) - grupowanie w komorki 8x8x8
        Map<Long, double[]> cells = new HashMap<>();
        for (Entity e : mc.world.getEntities()) {
            boolean relevant = (e instanceof AbstractDecorationEntity) || (e instanceof AbstractMinecartEntity);
            if (!relevant || e.squaredDistanceTo(me) > r2) continue;
            add(cells, e.getPos());
        }
        for (Vec3d s : signPoints) add(cells, s);

        for (double[] c : cells.values()) {
            if (c[0] < threshold.value) continue;
            Vec3d pos = new Vec3d(c[1] / c[0], c[2] / c[0], c[3] / c[0]);
            Trap existing = null;
            for (Trap t : traps) {
                if (t.pos.squaredDistanceTo(pos) < 100) {
                    existing = t;
                    break;
                }
            }
            long until = now + 30_000_000_000L;
            if (existing != null) {
                existing.pos = pos;
                existing.until = until;
            } else if (traps.size() < 8) {
                traps.add(new Trap(pos, until));
                if (notify.value) {
                    mc.player.sendMessage(Text.literal("Veltrix: TRAP w pobliżu (" + Math.round(Math.sqrt(pos.squaredDistanceTo(me))) + " m)"), true);
                }
            }
        }
        traps.removeIf(t -> t.until < now);
    }

    private static void add(Map<Long, double[]> cells, Vec3d p) {
        long k = key(Math.floorDiv((int) Math.floor(p.x), 8), Math.floorDiv((int) Math.floor(p.y), 8), Math.floorDiv((int) Math.floor(p.z), 8));
        double[] c = cells.computeIfAbsent(k, x -> new double[4]);
        c[0] += 1;
        c[1] += p.x;
        c[2] += p.y;
        c[3] += p.z;
    }

    @Override
    public void onHud(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        long now = System.nanoTime();
        for (Trap t : traps) {
            if (t.until < now) continue;
            double[] sp = Projection.projectStrict(t.pos.add(0, 2.5, 0), 0.3);
            if (sp == null || sp[0] < 0 || sp[1] < 0 || sp[0] > Projection.width() || sp[1] > Projection.height()) continue;
            long dist = Math.round(Math.sqrt(t.pos.squaredDistanceTo(mc.player.getPos())));
            String s = "TRAP " + dist + "m";
            int w = mc.textRenderer.getWidth(s);
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate((float) sp[0], (float) sp[1], 0f);
            m.scale(1.2f, 1.2f, 1f);
            ctx.fill(-w / 2 - 3, -2, w / 2 + 3, 10, Draw.withAlpha(0xFFB00020, 0.75f));
            ctx.drawTextWithShadow(mc.textRenderer, s, -w / 2, 0, 0xFFFFFFFF);
            m.pop();
        }
    }
}
