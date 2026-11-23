package com.example.dripclient.modules;

import com.example.dripclient.Category;
import com.example.dripclient.Draw;
import com.example.dripclient.Module;
import com.example.dripclient.Projection;
import com.example.dripclient.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pokazuje nad graczem, ile procent ma przedmiot w rece. Na serwerach, ktore same wpisuja procent
 * w nazwe lub opis przedmiotu (np. anarchie z "znaczkami"), odczytuje ten tekst. W przeciwnym razie
 * liczy procent z trwalosci przedmiotu (dziala wszedzie, bez wzgledu na serwer).
 */
public class SwordInfoModule extends Module {
    private final Setting.Num range = add(new Setting.Num("Zasięg", 48, 8, 128, 8));
    private final Setting.Bool preferServerText = add(new Setting.Bool("Odczytaj z nazwy/opisu", true));
    private final Setting.Num scale = add(new Setting.Num("Rozmiar", 0.8, 0.5, 1.5, 0.1));

    private static final Pattern PCT = Pattern.compile("(\\d{1,3})\\s?%");

    public SwordInfoModule() {
        super("MieczInfo", Category.COMBAT);
    }

    private static Integer clampPct(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static Integer fromText(String s) {
        if (s == null) return null;
        Matcher m = PCT.matcher(s);
        return m.find() ? clampPct(Integer.parseInt(m.group(1))) : null;
    }

    /** Szuka procentu wpisanego przez serwer w nazwie/opisie, a jak go nie ma, liczy z trwalosci. */
    private Integer findPercent(ItemStack st) {
        if (preferServerText.value) {
            Integer fromName = fromText(st.getName().getString());
            if (fromName != null) return fromName;
            try {
                var lore = st.get(DataComponentTypes.LORE);
                if (lore != null) {
                    for (Text line : lore.lines()) {
                        Integer p = fromText(line.getString());
                        if (p != null) return p;
                    }
                }
            } catch (Throwable ignored) {
                // inna wersja API skladnikow - po prostu pomijamy opis
            }
        }
        if (st.isDamageable()) {
            int max = st.getMaxDamage();
            if (max > 0) {
                int dmg = st.getDamage();
                return clampPct(Math.round((max - dmg) * 100f / max));
            }
        }
        return null;
    }

    private static int colorFor(int pct) {
        if (pct >= 60) return 0xFF6BFF7A;
        if (pct >= 25) return 0xFFFFD36B;
        return 0xFFFF6B6B;
    }

    @Override
    public void onHud(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        double r2 = range.value * range.value;

        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || p.squaredDistanceTo(mc.player) > r2) continue;
            ItemStack st = p.getMainHandStack();
            if (st.isEmpty()) continue;
            Integer pct = findPercent(st);
            if (pct == null) continue;

            double[] sp = Projection.projectStrict(p.getLerpedPos(delta).add(0, p.getHeight() * 0.72, 0), 0.3);
            if (sp == null || sp[0] < 0 || sp[1] < 0 || sp[0] > Projection.width() || sp[1] > Projection.height()) continue;

            String text = pct + "%";
            int w = mc.textRenderer.getWidth(text);
            MatrixStack m = ctx.getMatrices();
            m.push();
            m.translate((float) sp[0] + 12f, (float) sp[1] - 4f, 0f);
            float s = (float) scale.value;
            m.scale(s, s, 1f);
            ctx.fill(-3, -2, w + 3, 10, Draw.withAlpha(0xFF0A0B10, 0.65f));
            ctx.drawTextWithShadow(mc.textRenderer, text, 0, 0, colorFor(pct));
            m.pop();
        }
    }
}
