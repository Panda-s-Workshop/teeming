package com.panda.teeming.client.fieldguide;

import com.mojang.blaze3d.platform.InputConstants;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.roster.RosterStats.Dim;
import com.panda.teeming.roster.RosterStats.Entry;
import com.panda.teeming.spawn.TeemingSpawns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * The Field Guide: an open book. Left page: tabs Overworld / Nether / End with seen counts, and the tab's
 * creatures ("???" until seen). Right page: the chosen creature, turning, with its home and role; its tell and counter
 * once the player has killed it three times. Read-only: the data is the synced player attachment.
 */
public class FieldGuideScreen extends Screen {
    static final Identifier BOOK = TeemingMod.id("textures/gui/field_guide.png");
    static final int W = 300, H = 184;
    static final int L_X = 14, L_W = 128, R_X = 158, R_W = 128;
    static final int TAB_Y = 26, TAB_H = 12, TAB_GAP = 2, LIST_Y = 56, ROW_H = 12;
    static final int MODEL_Y = 10, MODEL_H = 58, FLIP_Y = H - 22;
    static final int INK = 0xFF34261A, INK_MUTED = 0xFF7A6650, ACCENT = 0xFF3E5A30, SELECT = 0x3343602F;

    private static final Dim[] TABS = Dim.values();
    private final Map<String, LivingEntity> portraits = new HashMap<>();
    private int x0, y0;
    private int tab;
    private int selected;
    /** Right page: 0 about (model, home, role), 1 tell and counter (once revealed). */
    private int side;
    private float ticks;

    public FieldGuideScreen() {
        super(Component.translatable("screen.teeming.field_guide.title"));
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The registered creatures of a dimension, in roster order. */
    static List<Entry> entries(Dim dim) {
        List<Entry> out = new ArrayList<>();
        for (Entry mob : RosterStats.ALL) if (mob.dim() == dim && TeemingSpawns.type(mob).isPresent()) out.add(mob);
        return out;
    }

    private FieldGuideProgress progress() {
        return minecraft.player == null ? FieldGuideProgress.EMPTY : FieldGuide.get(minecraft.player);
    }

    // ---- bounds (also used by the harness) ------------------------------------------------------------------

    /** Tabs sized to their names, left to right. */
    public int[] tabBounds(int t) {
        int x = x0 + L_X;
        for (int i = 0; i < t; i++) x += tabWidth(i) + TAB_GAP;
        return new int[] {x, y0 + TAB_Y, tabWidth(t), TAB_H};
    }

    private int tabWidth(int t) {
        return font.width(tabName(t)) + 6;
    }

    private static Component tabName(int t) {
        return Component.translatable("screen.teeming.field_guide.tab." + TABS[t].name().toLowerCase(java.util.Locale.ROOT));
    }

    /** The link at the foot of the right page that turns between its two sides. */
    public int[] flipBounds() {
        return new int[] {x0 + R_X, y0 + FLIP_Y, R_W, 10};
    }

    public int[] rowBounds(int i) {
        return new int[] {x0 + L_X - 2, y0 + LIST_Y + i * ROW_H, L_W + 4, ROW_H};
    }

    /** Selects a tab and an entry directly (the harness pages through the book this way). */
    public void show(int tab, int entry) {
        show(tab, entry, 0);
    }

    public void show(int tab, int entry, int side) {
        this.tab = Mth.clamp(tab, 0, TABS.length - 1);
        this.selected = Math.max(0, entry);
        this.side = side;
    }

    private static boolean in(double mx, double my, int[] b) {
        return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int t = 0; t < TABS.length; t++) {
                if (in(event.x(), event.y(), tabBounds(t)) && t != tab) {
                    show(t, 0);
                    turn();
                    return true;
                }
            }
            List<Entry> list = entries(TABS[tab]);
            for (int i = 0; i < list.size(); i++) {
                if (in(event.x(), event.y(), rowBounds(i)) && i != selected) {
                    show(tab, i);
                    turn();
                    return true;
                }
            }
            if (!list.isEmpty() && in(event.x(), event.y(), flipBounds())
                    && progress().revealed(list.get(Math.min(selected, list.size() - 1)).id(), FieldGuide.KILLS_TO_REVEAL)) {
                side = 1 - side;
                turn();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void turn() {
        if (minecraft.player != null) minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
    }

    // ---- drawing --------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        g.blit(RenderPipelines.GUI_TEXTURED, BOOK, x0, y0, 0, 0, W, H, 512, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractRenderState(g, mx, my, partial);
        FieldGuideProgress p = progress();
        drawLeft(g, p, mx, my);
        List<Entry> list = entries(TABS[tab]);
        if (!list.isEmpty()) drawRight(g, p, list.get(Math.min(selected, list.size() - 1)), partial);
    }

    private void drawLeft(GuiGraphicsExtractor g, FieldGuideProgress p, int mx, int my) {
        centered(g, title, x0 + L_X + L_W / 2, y0 + 12, INK);
        for (int t = 0; t < TABS.length; t++) {
            int[] b = tabBounds(t);
            boolean on = t == tab;
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], on ? ACCENT : 0x22000000);
            centered(g, tabName(t), b[0] + b[2] / 2, b[1] + 2, on ? 0xFFF4EAD0 : INK);
        }
        List<Entry> list = entries(TABS[tab]);
        long seen = list.stream().filter(m -> p.hasSeen(m.id())).count();
        String count = seen + " / " + list.size();
        g.text(font, count, x0 + L_X + L_W - font.width(count), y0 + TAB_Y + TAB_H + 4, INK_MUTED, false);
        if (list.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("screen.teeming.field_guide.empty"), x0 + L_X, y0 + LIST_Y, L_W, INK_MUTED, false);
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            int[] b = rowBounds(i);
            Entry mob = list.get(i);
            if (i == selected || in(mx, my, b)) g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], SELECT);
            boolean known = p.hasSeen(mob.id());
            Component name = known ? Component.translatable("entity.teeming." + mob.id()) : Component.translatable("screen.teeming.field_guide.unknown");
            g.text(font, String.format("%02d", mob.number()), b[0] + 3, b[1] + 2, INK_MUTED, false);
            g.text(font, name, b[0] + 20, b[1] + 2, known ? INK : INK_MUTED, false);
            if (p.revealed(mob.id(), FieldGuide.KILLS_TO_REVEAL)) g.text(font, "✦", b[0] + b[2] - 10, b[1] + 2, ACCENT, false);
        }
    }

    private void drawRight(GuiGraphicsExtractor g, FieldGuideProgress p, Entry mob, float partial) {
        int x = x0 + R_X, y = y0 + MODEL_Y;
        boolean known = p.hasSeen(mob.id());
        boolean revealed = p.revealed(mob.id(), FieldGuide.KILLS_TO_REVEAL);
        Component name = known ? Component.translatable("entity.teeming." + mob.id()) : Component.translatable("screen.teeming.field_guide.unknown");
        if (revealed && side == 1) {
            centered(g, name, x + R_W / 2, y, INK);
            int ty = labelled(g, "tell", mob, x, y + 14);
            labelled(g, "counter", mob, x, ty + 2);
            flip(g, "◀ " + Component.translatable("screen.teeming.field_guide.about").getString());
            return;
        }
        if (known) {
            LivingEntity portrait = portrait(mob, p);
            if (portrait != null) {
                int scale = (int) Math.max(8, Math.min(30, 46 / Math.max(mob.height(), mob.width() * 0.9F)));
                float spin = Mth.sin((ticks + partial) * 0.04F) * 70.0F;
                InventoryScreen.extractEntityInInventoryFollowsMouse(g, x, y, x + R_W, y + MODEL_H, scale, 0.0625F,
                        x + R_W / 2.0F + spin, y + MODEL_H / 2.0F, portrait);
            }
        } else {
            g.fill(x + R_W / 2 - 18, y + 6, x + R_W / 2 + 18, y + MODEL_H - 6, 0x33000000);
            centered(g, "?", x + R_W / 2, y + MODEL_H / 2 - 4, INK_MUTED);
        }
        int ty = y + MODEL_H + 3;
        centered(g, name, x + R_W / 2, ty, INK);
        ty += 12;
        if (!known) {
            g.textWithWordWrap(font, Component.translatable("screen.teeming.field_guide.unseen"), x, ty, R_W, INK_MUTED, false);
            return;
        }
        ty = para(g, Component.translatable("guide.teeming." + mob.id() + ".home"), x, ty, ACCENT, FLIP_Y - 12);
        para(g, Component.translatable("guide.teeming." + mob.id() + ".role"), x, ty, INK, FLIP_Y - 12);
        if (revealed) {
            int seenLooks = (int) mob.looks().stream().filter(l -> p.hasSeenLook(mob.id(), l)).count();
            g.text(font, Component.translatable("screen.teeming.field_guide.looks", seenLooks, mob.looks().size()), x, y0 + FLIP_Y - 11, INK_MUTED, false);
            flip(g, Component.translatable("screen.teeming.field_guide.fight").getString() + " ▶");
        } else {
            g.text(font, Component.translatable("screen.teeming.field_guide.kills", p.killsOf(mob.id()), FieldGuide.KILLS_TO_REVEAL), x, y0 + FLIP_Y, INK_MUTED, false);
        }
    }

    /** Centred ink without a shadow (shadows smear on parchment). */
    private void centered(GuiGraphicsExtractor g, Component text, int cx, int y, int colour) {
        g.text(font, text, cx - font.width(text) / 2, y, colour, false);
    }

    private void centered(GuiGraphicsExtractor g, String text, int cx, int y, int colour) {
        centered(g, Component.literal(text), cx, y, colour);
    }

    private void flip(GuiGraphicsExtractor g, String label) {
        int[] b = flipBounds();
        g.text(font, label, b[0] + b[2] - font.width(label), b[1], ACCENT, false);
    }

    private int labelled(GuiGraphicsExtractor g, String key, Entry mob, int x, int y) {
        g.text(font, Component.translatable("screen.teeming.field_guide." + key), x, y, ACCENT, false);
        return para(g, Component.translatable("guide.teeming." + mob.id() + "." + key), x, y + 10, INK, FLIP_Y - 2);
    }

    /** Wrapped text that stops above {@code foot} (page-relative); returns the next line's y. */
    private int para(GuiGraphicsExtractor g, Component text, int x, int y, int colour, int foot) {
        int bottom = y0 + foot;
        var lines = font.split(text, R_W);
        for (var line : lines) {
            if (y + 9 > bottom) break;
            g.text(font, line, x, y, colour, false);
            y += 9;
        }
        return y + 3;
    }

    /** A client-only copy of the creature in the first look the player saw (or its base look), for the page. */
    private LivingEntity portrait(Entry mob, FieldGuideProgress p) {
        String look = mob.looks().stream().filter(l -> p.hasSeenLook(mob.id(), l)).findFirst().orElse(mob.looks().getFirst());
        return portraits.computeIfAbsent(mob.id() + "/" + look, key -> {
            if (minecraft.level == null) return null;
            Mob created = TeemingSpawns.type(mob).map(t -> (Mob) t.create(minecraft.level, EntitySpawnReason.LOAD)).orElse(null);
            if (created == null) return null;
            created.setId(-1000 - portraits.size());   // the renderer reads the id (held-item models); never added to the level
            if (created instanceof TeemingCreature c) c.core().setLook(look, false);
            return created;
        });
    }
}
