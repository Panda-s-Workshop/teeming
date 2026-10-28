package com.panda.teeming.client.fieldguide;

import com.mojang.blaze3d.platform.InputConstants;
import com.panda.teeming.TeemingConfig;
import com.panda.teeming.TeemingMod;
import com.panda.teeming.fieldguide.FieldGuide;
import com.panda.teeming.fieldguide.FieldGuideProgress;
import com.panda.teeming.mob.TeemingCreature;
import com.panda.teeming.registry.ModComponents;
import com.panda.teeming.registry.ModItems;
import com.panda.teeming.roster.RosterStats;
import com.panda.teeming.roster.RosterStats.Dim;
import com.panda.teeming.roster.RosterStats.Entry;
import com.panda.teeming.spawn.TeemingSpawns;
import com.panda.teeming.trophy.TrophyMob;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * The Field Guide: an open book. Left page: tabs Overworld / Nether / End with seen and trophy counts, and the tab's
 * creatures ("???" until seen, a crown once its trophy is owned). Right page: the chosen creature, an ink silhouette
 * until seen, then turning, with its home and role; its tell, counter and drops once the player has killed it three
 * times. The right page's text is never cut: it flows onto as many sides as it needs ({@link #sides}), turned with the
 * link at the page's foot or the Left and Right keys. Read-only: the data is the synced player attachment.
 */
public class FieldGuideScreen extends Screen {
    static final Identifier BOOK = TeemingMod.id("textures/gui/field_guide.png");
    public static final int W = 300, H = 184;
    static final int L_X = 14, L_W = 128, R_X = 158;
    /** The right page's text width. */
    public static final int R_W = 128;
    static final int TAB_X = L_X - 3, TAB_Y = 26, TAB_H = 12, TAB_GAP = 1, TAB_PAD = 4, LIST_Y = 56, ROW_H = 12;
    /** Rows that fit on the left page above its bottom margin; a longer list scrolls (the wheel, or the selection). */
    public static final int ROWS = (H - 8 - LIST_Y) / ROW_H;
    /** Keeps the tabs and the count clear of the spine's shadow on the left page's inner edge. */
    static final int SPINE_MARGIN = 8;
    static final int MODEL_Y = 10, MODEL_H = 52;
    /** The row at the right page's foot that turns its sides (page-relative). */
    public static final int FLIP_Y = H - 22;
    /** Nothing on a side that has the turning row reaches below this (page-relative). */
    public static final int FOOT = FLIP_Y - 2;
    static final int SILHOUETTE_W = 124, SILHOUETTE_H = 50;
    /** A wrapped line's height, the room after a paragraph, a heading's advance and the room above a heading. */
    static final int LINE_H = 9, PARA_GAP = 3, HEAD_H = 10, HEAD_GAP = 2;
    /** Where a side's text starts under the name at its top, when it carries no portrait. */
    static final int TEXT_Y = MODEL_Y + 14;
    /** A heading moves to the next side unless this many of its lines (or all, if fewer) fit under it. */
    static final int HEAD_KEEP = 2;
    static final int INK = 0xFF34261A, INK_MUTED = 0xFF7A6650, ACCENT = 0xFF3E5A30, SELECT = 0x3343602F;
    /** The trophy crown, drawn from the book's sheet beside the book: a text glyph that small reads as a sprig. */
    static final int CROWN_U = 304, CROWN_V = 0, CROWN_W = 9, CROWN_H = 7;
    /** The reveal mark at a row's end, and the room left between a row's marks and between them and its name. */
    static final String STAR = "✦";
    static final int MARK_GAP = 2, NAME_GAP = 3;

    private static final Dim[] TABS = Dim.values();
    private final Map<String, LivingEntity> portraits = new HashMap<>();
    private int x0, y0;
    private int tab;
    private int selected;
    /** The list row shown at the top of the page. */
    private int top;
    /** The right page's side shown, 0 the first (portrait, home, role); see {@link #sides}. */
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

    /**
     * The creatures of a dimension the guide lists, in roster order: registered roster mobs the config hasn't switched
     * off, the same set the Naturalist and Knowledge advancements count, so the tab's counts and theirs agree.
     */
    public static List<Entry> entries(Dim dim) {
        List<Entry> out = new ArrayList<>();
        for (Entry mob : RosterStats.ALL) {
            if (mob.dim() == dim && TeemingSpawns.type(mob).isPresent() && TeemingConfig.mobEnabled(mob.id())) out.add(mob);
        }
        return out;
    }

    private FieldGuideProgress progress() {
        return minecraft.player == null ? FieldGuideProgress.EMPTY : FieldGuide.get(minecraft.player);
    }

    // ---- bounds (also used by the harness) ------------------------------------------------------------------

    /** Tabs sized to their names, left to right. */
    public int[] tabBounds(int t) {
        int x = x0 + TAB_X;
        for (int i = 0; i < t; i++) x += tabWidth(i) + TAB_GAP;
        return new int[] {x, y0 + TAB_Y, tabWidth(t), TAB_H};
    }

    private int tabWidth(int t) {
        return font.width(tabName(t)) + TAB_PAD * 2;
    }

    private static Component tabName(int t) {
        return Component.translatable("screen.teeming.field_guide.tab." + TABS[t].name().toLowerCase(java.util.Locale.ROOT));
    }

    /** The row at the foot of the right page that turns its sides: the left half back, the right half on. */
    public int[] flipBounds() {
        return new int[] {x0 + R_X, y0 + FLIP_Y, R_W, 10};
    }

    /** Where list row {@code i} is drawn, or null when it is scrolled off the page. */
    public int[] rowBounds(int i) {
        if (i < top || i >= top + ROWS) return null;
        return new int[] {x0 + L_X - 2, y0 + LIST_Y + (i - top) * ROW_H, L_W + 4, ROW_H};
    }

    /** The first row on the page; the harness reads it to check the selection is in view. */
    public int top() {
        return top;
    }

    /** The selected row of the open tab. */
    public int selected() {
        return selected;
    }

    /**
     * How list row {@code i} lays out, or null when it is scrolled off the page: the marks sit at the row's end (the
     * reveal star outermost, the trophy crown inside it), and the name, cut with "…" when it is too long, ends
     * {@link #NAME_GAP} short of the first of them. {@code nameRight} is the name's right edge (exclusive),
     * {@code crownX} and {@code starX} the marks' left edges; a mark the row doesn't carry is -1.
     */
    public RowLayout rowLayout(int i) {
        int[] b = rowBounds(i);
        List<Entry> list = entries(TABS[tab]);
        if (b == null || i >= list.size()) return null;
        return layout(b, list.get(i), progress());
    }

    public record RowLayout(int nameX, String name, int nameRight, int crownX, int starX) {
        /** The left edge of the row's first mark, or -1 when it carries none. */
        public int marksX() {
            return crownX >= 0 ? crownX : starX;
        }
    }

    private RowLayout layout(int[] b, Entry mob, FieldGuideProgress p) {
        int right = b[0] + b[2] - 2;
        int starX = -1, crownX = -1;
        if (p.revealed(mob.id())) {
            starX = right - font.width(STAR);
            right = starX - MARK_GAP;
        }
        if (p.hasTrophy(mob.id())) {
            crownX = right - CROWN_W;
            right = crownX - MARK_GAP;
        }
        int end = crownX >= 0 ? crownX : starX >= 0 ? starX : b[0] + b[2] - 2 + NAME_GAP;
        int nameX = b[0] + 20, room = end - NAME_GAP - nameX;
        Component name = p.hasSeen(mob.id()) ? Component.translatable("entity.teeming." + mob.id()) : Component.translatable("screen.teeming.field_guide.unknown");
        String shown = name.getString();
        if (font.width(shown) > room) shown = font.plainSubstrByWidth(shown, room - font.width("…")) + "…";
        return new RowLayout(nameX, shown, nameX + font.width(shown), crownX, starX);
    }

    /** Scrolls so row {@code i} is on the page, moving as little as it can. */
    private void reveal(int i) {
        if (i < top) top = i;
        else if (i >= top + ROWS) top = i - ROWS + 1;
        clampTop();
    }

    private void clampTop() {
        top = Mth.clamp(top, 0, Math.max(0, entries(TABS[tab]).size() - ROWS));
    }

    /** Selects a tab and an entry directly (the harness pages through the book this way). */
    public void show(int tab, int entry) {
        show(tab, entry, 0);
    }

    public void show(int tab, int entry, int side) {
        this.tab = Mth.clamp(tab, 0, TABS.length - 1);
        this.selected = Math.max(0, entry);
        this.side = side;
        reveal(selected);
    }

    /** The right page's side shown, kept within the shown creature's sides. */
    public int side() {
        Entry mob = shown();
        return mob == null ? 0 : Mth.clamp(side, 0, sides(mob).size() - 1);
    }

    private Entry shown() {
        List<Entry> list = entries(TABS[tab]);
        return list.isEmpty() ? null : list.get(Math.min(selected, list.size() - 1));
    }

    // ---- the right page's sides (also used by the harness) --------------------------------------------------

    /** What a piece of a side is, and how tall it draws. */
    public enum Part {
        PORTRAIT(MODEL_H), NAME(LINE_H), HEADING(LINE_H), LINE(LINE_H), STATUS(LINE_H), DROPS(26);

        public final int height;

        Part(int height) {
            this.height = height;
        }
    }

    /**
     * One thing drawn on a side at page-relative {@code y}: a wrapped line of {@code section} ("home", "role", "tell",
     * "counter"), the heading over one, the name, the portrait, the looks or kills line, or the drops row. {@code width}
     * is how wide it draws (a name with its crown).
     */
    public record Piece(Part part, String section, String text, int y, int width, int colour) {
        public int bottom() {
            return y + part.height;
        }
    }

    /** One side of the right page: its pieces top to bottom, nothing reaching below {@code foot}; {@code fight} for the tell's sides. */
    public record Side(List<Piece> pieces, int foot, boolean fight) {}

    /**
     * How a creature's right page lays out with the open player's progress, for the renderer and the harness alike. A
     * seen creature's portrait, name, home, role and its looks (or kills) line flow down the first side and onto more
     * as they need; once revealed, its tell, counter and drops start a side of their own and flow the same way. Every
     * side after the first carries the name at its top, a heading keeps its first lines with it, and nothing is cut.
     * An unseen creature has one side, its silhouette, drawn on its own.
     */
    public List<Side> sides(Entry mob) {
        FieldGuideProgress p = progress();
        if (!p.hasSeen(mob.id())) return List.of(new Side(List.of(), FOOT, false));
        boolean revealed = p.revealed(mob.id());
        String name = Component.translatable("entity.teeming." + mob.id()).getString();
        int nameW = font.width(name) + (p.hasTrophy(mob.id()) ? NAME_GAP + CROWN_W : 0);
        Flow flow = new Flow(name, nameW);
        flow.add(Part.PORTRAIT, null, "", R_W, 0, MODEL_H + 3);
        flow.add(Part.NAME, null, name, nameW, INK, 12);
        flow.para("home", null, ACCENT, mob);
        flow.para("role", null, INK, mob);
        String status = revealed
                ? Component.translatable("screen.teeming.field_guide.looks", (int) mob.looks().stream().filter(l -> p.hasSeenLook(mob.id(), l)).count(), mob.looks().size()).getString()
                : Component.translatable("screen.teeming.field_guide.kills", p.killsOf(mob.id()), p.revealAt()).getString();
        flow.room(LINE_H);
        Piece looks = flow.add(Part.STATUS, null, status, font.width(status), INK_MUTED, LINE_H);
        int aboutSides = flow.close();
        if (revealed) {
            flow.side(true);
            flow.para("tell", "tell", INK, mob);
            flow.para("counter", "counter", INK, mob);
            int icons = (int) MobDrops.of(mob.id()).stream().filter(id -> BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).isPresent()).count();
            Component drops = Component.translatable("screen.teeming.field_guide.drops");
            flow.gap(Part.DROPS.height);
            flow.add(Part.DROPS, null, drops.getString(), Math.max(font.width(drops), icons * 18 - 2), ACCENT, Part.DROPS.height);
            flow.close();
        }
        // the looks or kills line sits at the foot of the last about side; alone on the page, in the turning row
        List<Side> sides = flow.sides;
        boolean alone = sides.size() == 1;
        Side last = sides.get(aboutSides - 1);
        List<Piece> pieces = new ArrayList<>(last.pieces());
        pieces.set(pieces.indexOf(looks), new Piece(looks.part(), null, looks.text(), alone ? FLIP_Y : FLIP_Y - 11, looks.width(), looks.colour()));
        sides.set(aboutSides - 1, new Side(List.copyOf(pieces), alone ? FLIP_Y + LINE_H : FOOT, false));
        return List.copyOf(sides);
    }

    /** Lays pieces down the right page's sides, starting a side when the next one would reach below {@link #FOOT}. */
    private final class Flow {
        final List<Side> sides = new ArrayList<>();
        final String name;
        final int nameW;
        List<Piece> pieces = new ArrayList<>();
        int y = MODEL_Y;
        boolean fight;
        /** Nothing but the name is on the side yet, so a heading needs no room above it. */
        boolean fresh = true;

        Flow(String name, int nameW) {
            this.name = name;
            this.nameW = nameW;
        }

        Piece add(Part part, String section, String text, int width, int colour, int advance) {
            Piece piece = new Piece(part, section, text, y, width, colour);
            pieces.add(piece);
            y += advance;
            fresh = false;
            return piece;
        }

        /** Starts a new side, the name at its top. */
        void side(boolean fight) {
            close();
            this.fight = fight;
            y = MODEL_Y;
            add(Part.NAME, null, name, nameW, INK, TEXT_Y - MODEL_Y);
            fresh = true;
        }

        /** Turns to a new side unless {@code height} more fits on this one. */
        void room(int height) {
            if (y + height > FOOT) side(fight);
        }

        /** The room above a heading or the drops row, then a new side unless {@code height} fits under it. */
        void gap(int height) {
            if (!fresh) y += HEAD_GAP;
            if (y + height > FOOT) side(fight);
        }

        /** A paragraph of the creature's {@code section} text, wrapped to the page, under its heading if it has one. */
        void para(String section, String heading, int colour, Entry mob) {
            List<String> lines = font.getSplitter().splitLines(Component.translatable("guide.teeming." + mob.id() + "." + section), R_W, Style.EMPTY)
                    .stream().map(FormattedText::getString).toList();
            if (heading != null) {
                Component label = Component.translatable("screen.teeming.field_guide." + heading);
                gap(HEAD_H + Math.min(HEAD_KEEP, lines.size()) * LINE_H);
                add(Part.HEADING, section, label.getString(), font.width(label), ACCENT, HEAD_H);
            }
            for (String line : lines) {
                room(LINE_H);
                add(Part.LINE, section, line, font.width(line), colour, LINE_H);
            }
            y += PARA_GAP;
        }

        /** Ends the side being laid; returns how many sides there are. */
        int close() {
            if (!pieces.isEmpty()) sides.add(new Side(List.copyOf(pieces), FOOT, fight));
            pieces = new ArrayList<>();
            return sides.size();
        }
    }

    private static boolean in(double mx, double my, int[] b) {
        return b != null && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy == 0.0 || entries(TABS[tab]).size() <= ROWS) return super.mouseScrolled(mx, my, dx, dy);
        top -= (int) Math.signum(dy);
        clampTop();
        return true;
    }

    /**
     * Up and down move the selection a row, Page Up and Page Down a page; the list scrolls to keep it in view. Left and
     * right turn the right page's sides.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isLeft() || event.isRight()) {
            turnSide(event.isLeft() ? -1 : 1);
            return true;
        }
        int step = event.isUp() ? -1 : event.isDown() ? 1
                : event.key() == InputConstants.KEY_PAGEUP ? -ROWS : event.key() == InputConstants.KEY_PAGEDOWN ? ROWS : 0;
        if (step == 0 || entries(TABS[tab]).isEmpty()) return super.keyPressed(event);
        move(step);
        return true;
    }

    /** Moves the selection {@code step} rows; a page's step scrolls the page with it, so the selection keeps its place on it. */
    private void move(int step) {
        int size = entries(TABS[tab]).size();
        int to = Mth.clamp(Math.min(selected, size - 1) + step, 0, size - 1);
        if (Math.abs(step) >= ROWS) {
            top += step;
            clampTop();
        }
        if (to != selected) {
            show(tab, to);
            turn();
        }
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
            int[] flip = flipBounds();
            if (!list.isEmpty() && in(event.x(), event.y(), flip)) {
                // two sides, about and the tell: the link turns to the other; more: the half clicked turns back or on
                int count = sides(shown()).size(), at = side();
                if (count == 2) turnSide(at == 0 ? 1 : -1);
                else turnSide(event.x() < flip[0] + flip[2] / 2.0 ? -1 : 1);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Turns the right page {@code dir} sides, if it has a side there; returns whether it turned. */
    private boolean turnSide(int dir) {
        Entry mob = shown();
        if (mob == null) return false;
        int to = side() + dir;
        if (to < 0 || to >= sides(mob).size()) return false;
        side = to;
        turn();
        return true;
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
        if (!list.isEmpty()) drawRight(g, p, list.get(Math.min(selected, list.size() - 1)), partial, mx, my);
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
        int countY = y0 + TAB_Y + TAB_H + 4;
        long seen = list.stream().filter(m -> p.hasSeen(m.id())).count();
        Component count = Component.translatable("screen.teeming.field_guide.seen_count", seen, list.size());
        g.text(font, count, x0 + L_X + L_W - SPINE_MARGIN - font.width(count), countY, INK_MUTED, false);
        long owned = list.stream().filter(m -> p.hasTrophy(m.id())).count();      // trophies owned, under the same crown as the rows
        crown(g, x0 + L_X, countY);
        g.text(font, owned + " / " + list.size(), x0 + L_X + CROWN_W + NAME_GAP, countY, INK_MUTED, false);
        if (list.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("screen.teeming.field_guide.empty"), x0 + L_X, y0 + LIST_Y, L_W, INK_MUTED, false);
            return;
        }
        for (int i = top; i < Math.min(list.size(), top + ROWS); i++) {
            int[] b = rowBounds(i);
            Entry mob = list.get(i);
            if (i == selected || in(mx, my, b)) g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], SELECT);
            g.text(font, String.format("%02d", mob.number()), b[0] + 3, b[1] + 2, INK_MUTED, false);
            RowLayout row = layout(b, mob, p);           // a long name stops short of the marks at the row's end
            g.text(font, row.name(), row.nameX(), b[1] + 2, p.hasSeen(mob.id()) ? INK : INK_MUTED, false);
            if (row.starX() >= 0) g.text(font, STAR, row.starX(), b[1] + 2, ACCENT, false);
            if (row.crownX() >= 0) crown(g, row.crownX(), b[1] + 2);
        }
        // more rows above or below: a mark at the list's outer edge, where the wheel scrolls them in
        int edge = x0 + L_X - 7;
        if (top > 0) g.text(font, "▲", edge, y0 + LIST_Y + 2, INK_MUTED, false);
        if (top + ROWS < list.size()) g.text(font, "▼", edge, y0 + LIST_Y + (ROWS - 1) * ROW_H + 2, INK_MUTED, false);
    }

    private void drawRight(GuiGraphicsExtractor g, FieldGuideProgress p, Entry mob, float partial, int mx, int my) {
        int x = x0 + R_X, y = y0 + MODEL_Y;
        if (!p.hasSeen(mob.id())) {
            g.blit(RenderPipelines.GUI_TEXTURED, silhouette(mob), x + (R_W - SILHOUETTE_W) / 2, y + 1, 0, 0, SILHOUETTE_W, SILHOUETTE_H,
                    SILHOUETTE_W, SILHOUETTE_H);
            int ty = y + MODEL_H + 3;
            centered(g, Component.translatable("screen.teeming.field_guide.unknown"), x + R_W / 2, ty, INK);
            g.textWithWordWrap(font, Component.translatable("screen.teeming.field_guide.unseen"), x, ty + 12, R_W, INK_MUTED, false);
            return;
        }
        List<Side> sides = sides(mob);
        int at = side();
        for (Piece piece : sides.get(at).pieces()) {
            int py = y0 + piece.y();
            switch (piece.part()) {
                case PORTRAIT -> {
                    LivingEntity portrait = portrait(mob, p);
                    if (portrait != null) {
                        int scale = (int) Math.max(8, Math.min(30, 46 / Math.max(mob.height(), mob.width() * 0.9F)));
                        float spin = Mth.sin((ticks + partial) * 0.04F) * 70.0F;
                        InventoryScreen.extractEntityInInventoryFollowsMouse(g, x, py, x + R_W, py + MODEL_H, scale, 0.0625F,
                                x + R_W / 2.0F + spin, py + MODEL_H / 2.0F, portrait);
                    }
                }
                case NAME -> {                                   // an owned trophy: the name and the crown, centred together
                    int nx = x + (R_W - piece.width()) / 2;
                    g.text(font, piece.text(), nx, py, piece.colour(), false);
                    if (p.hasTrophy(mob.id())) crown(g, nx + font.width(piece.text()) + NAME_GAP, py);
                }
                case DROPS -> drops(g, mob, x, py, mx, my);
                default -> g.text(font, piece.text(), x, py, piece.colour(), false);
            }
        }
        if (sides.size() == 2 && p.revealed(mob.id())) {      // about and the tell: a named link to the other
            flip(g, at == 0 ? Component.translatable("screen.teeming.field_guide.fight").getString() + " ▶"
                    : "◀ " + Component.translatable("screen.teeming.field_guide.about").getString());
        } else if (sides.size() > 1) {                        // more: back and on, and which side of how many
            int[] b = flipBounds();
            if (at > 0) g.text(font, "◀", b[0], b[1], ACCENT, false);
            if (at < sides.size() - 1) g.text(font, "▶", b[0] + b[2] - font.width("▶"), b[1], ACCENT, false);
            centered(g, (at + 1) + " / " + sides.size(), b[0] + b[2] / 2, b[1], INK_MUTED);
        }
    }

    /** The trophy crown, its top on the cap line of text drawn at {@code y}. */
    private void crown(GuiGraphicsExtractor g, int x, int y) {
        g.blit(RenderPipelines.GUI_TEXTURED, BOOK, x, y, CROWN_U, CROWN_V, CROWN_W, CROWN_H, 512, 256);
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

    /** The drops row: a label and the items' icons, each naming itself on hover. */
    private void drops(GuiGraphicsExtractor g, Entry mob, int x, int y, int mx, int my) {
        g.text(font, Component.translatable("screen.teeming.field_guide.drops"), x, y, ACCENT, false);
        int ix = x;
        for (String id : MobDrops.of(mob.id())) {
            ItemStack stack = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) continue;
            if (stack.is(ModItems.TROPHY.get())) stack.set(ModComponents.TROPHY_MOB.get(), new TrophyMob(mob.id(), "base"));
            g.item(stack, ix, y + 10);
            if (mx >= ix && mx < ix + 16 && my >= y + 10 && my < y + 26) g.setTooltipForNextFrame(font, stack, mx, my);
            ix += 18;
        }
    }

    private final Map<String, Identifier> silhouettes = new HashMap<>();

    private Identifier silhouette(Entry mob) {
        return silhouettes.computeIfAbsent(mob.id(), id -> TeemingMod.id("textures/gui/field_guide/silhouette/" + id + ".png"));
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
