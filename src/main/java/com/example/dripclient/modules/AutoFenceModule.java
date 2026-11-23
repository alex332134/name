package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Automatycznie niszczy najblizszy plotek w zasiegu. Progres kopania jest wysylany bezposrednio
 * (interactionManager.attackBlock), wiec dziala nawet gdy nad plotkiem stoi wagonik albo jest inny
 * blok zaslaniajacy go z celownika - nie trzeba miec go dokladnie na muszce, jak wczesniej.
 * Wagoniki, ramki i armor standy stojace NA plotku sa dodatkowo usuwane po Twojej stronie.
 * Najlepiej miec w rece siekiere.
 */
public class AutoFenceModule extends Module {
    private final Setting.Num range = add(new Setting.Num("Zasięg", 4.5, 1.0, 5.0, 0.5));
    private final Setting.Bool gates = add(new Setting.Bool("Furtki też", false));
    private final Setting.Bool clear = add(new Setting.Bool("Usuń wagoniki/ramki", true));

    private BlockPos current;

    public AutoFenceModule() {
        super("AutoPłotek", Category.WORLD);
    }

    @Override
    public void onDisable() {
        stopMining(MinecraftClient.getInstance());
    }

    private void stopMining(MinecraftClient mc) {
        if (current != null && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
        current = null;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            stopMining(mc);
            return;
        }

        BlockPos target = pick(mc);
        if (target == null) {
            stopMining(mc);
            return;
        }

        if (clear.value) {
            Box box = new Box(target).expand(0.15);
            List<Integer> ids = new ArrayList<>();
            for (Entity e : mc.world.getEntities()) {
                boolean blocker = e instanceof AbstractMinecartEntity
                        || e instanceof AbstractDecorationEntity
                        || e instanceof ArmorStandEntity;
                if (blocker && e.getBoundingBox().intersects(box)) ids.add(e.getId());
            }
            for (int id : ids) mc.world.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }

        // obrot w strone plotka - tylko dla realizmu, nie jest wymagany do dzialania
        Vec3d eye = p.getEyePos();
        Vec3d center = Vec3d.ofCenter(target);
        double dx = center.x - eye.x, dy = center.y - eye.y, dz = center.z - eye.z;
        double hd = Math.sqrt(dx * dx + dz * dz);
        p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setPitch((float) -Math.toDegrees(Math.atan2(dy, hd)));

        if (!target.equals(current)) {
            // nowy cel: zacznij kopanie od zera (tak jak pierwsze klikniecie myszka)
            mc.interactionManager.cancelBlockBreaking();
            mc.interactionManager.attackBlock(target, Direction.UP);
            current = target;
        } else {
            // ten sam cel co przed chwila: kontynuuj progres (tak jak trzymanie przycisku myszy)
            mc.interactionManager.updateBlockBreakingProgress(target, Direction.UP);
        }
        p.swingHand(Hand.MAIN_HAND);
        // gdy plotek zniknie, nastepny skan pick() go juz nie znajdzie i current zostanie zastapiony same
    }

    /** Najblizszy plotek w zasiegu, bez wzgledu na to, co go zaslania. */
    private BlockPos pick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        Vec3d eye = p.getEyePos();
        int r = (int) Math.ceil(range.value);
        BlockPos base = p.getBlockPos();

        List<BlockPos> cands = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(base.add(-r, -r, -r), base.add(r, r, r))) {
            BlockState st = mc.world.getBlockState(pos);
            boolean fence = st.getBlock() instanceof FenceBlock || (gates.value && st.getBlock() instanceof FenceGateBlock);
            if (!fence) continue;
            if (Vec3d.ofCenter(pos).squaredDistanceTo(eye) > range.value * range.value) continue;
            cands.add(pos.toImmutable());
        }
        if (cands.isEmpty()) return null;
        cands.sort(Comparator.comparingDouble(c -> Vec3d.ofCenter(c).squaredDistanceTo(eye)));
        // trzymaj sie aktualnie kopanego plotka, dopoki jest w zasiegu - zeby nie zrzucac progresu
        if (current != null && cands.contains(current)) return current;
        return cands.get(0);
    }
}
