package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Module;
import com.example.dripclient.ModuleManager;
import com.example.dripclient.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoDrip: dziala czysto po Twojej stronie (samo otwiera/zamyka klapy), wiec dziala na kazdym
 * serwerze, takze na anarchiach typu anarchia.gg, bez instalowania czegokolwiek po stronie serwera.
 * Przelacza klapy w zasiegu, ale tylko wtedy, gdy pod/kolo nich stoi wrog (nie znajomy) - wiec jesli
 * masz tam wlasna pulapke ze stalaktytami, spadna automatycznie, gdy ktos w nia wejdzie.
 */
public class AutoDripModule extends Module {
    private final Setting.Num range = add(new Setting.Num("Zasięg klap", 5, 2, 8, 1));
    private final Setting.Num enemyRadius = add(new Setting.Num("Zasięg wykrywania wroga", 2, 1, 6, 1));
    private final Setting.Num interval = add(new Setting.Num("Co ile ticków", 3, 1, 10, 1));
    private final Setting.Bool notify = add(new Setting.Bool("Komunikaty", true));

    private int cooldown = 0;
    private int msgCooldown = 0;

    public AutoDripModule() {
        super("AutoDrip", Category.COMBAT);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || mc.interactionManager == null) return;
        if (msgCooldown > 0) msgCooldown--;
        if (++cooldown < interval.value) return;
        cooldown = 0;

        List<AbstractClientPlayerEntity> enemies = new ArrayList<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && !ModuleManager.isFriend(p.getName().getString())) enemies.add(p);
        }
        if (enemies.isEmpty()) return;

        int r = (int) Math.ceil(range.value);
        BlockPos base = mc.player.getBlockPos();
        double er2 = enemyRadius.value * enemyRadius.value;
        int triggered = 0;

        for (BlockPos pos : BlockPos.iterate(base.add(-r, -r, -r), base.add(r, r, r))) {
            BlockState st = mc.world.getBlockState(pos);
            if (!(st.getBlock() instanceof TrapdoorBlock) || st.isOf(Blocks.IRON_TRAPDOOR)) continue;

            Vec3d center = Vec3d.ofCenter(pos);
            boolean enemyNear = false;
            for (AbstractClientPlayerEntity e : enemies) {
                double dx = e.getX() - center.x, dz = e.getZ() - center.z;
                double dy = e.getY() - center.y;
                if (dx * dx + dz * dz <= er2 && dy > -4 && dy < 1.5) {
                    enemyNear = true;
                    break;
                }
            }
            if (!enemyNear) continue;
            if (triggered >= 2) break;

            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(center, Direction.UP, pos, false));
            triggered++;
            if (notify.value && msgCooldown == 0) {
                mc.player.sendMessage(net.minecraft.text.Text.literal("Veltrix AutoDrip: aktywowano pułapkę"), true);
                msgCooldown = 20;
            }
        }
    }
}
