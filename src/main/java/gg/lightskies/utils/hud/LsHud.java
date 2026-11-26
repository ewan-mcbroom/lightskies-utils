package gg.lightskies.utils.hud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import gg.lightskies.utils.LightSkiesUtils;
import gg.lightskies.utils.config.LSConfig;
import gg.lightskies.utils.scan.Locator;
import gg.lightskies.utils.tracker.BossTracker;
import gg.lightskies.utils.tracker.DropTracker;
import gg.lightskies.utils.tracker.MiningTracker;
import gg.lightskies.utils.tracker.QuestTracker;
import gg.lightskies.utils.util.Fmt;
import gg.lightskies.utils.util.Game;

/**
 * Draws the HUD as separate boxes (one per {@link Section}), each with its own position, scale and
 * on/off switch from the config. Lines may use § colour codes.
 *
 * While the HUD editor is open ({@link #editing}), every enabled box is drawn even when it has
 * nothing to show, and its on-screen bounds are recorded so the editor can drag and resize it.
 */
public final class LsHud implements HudElement {
	public enum Section {
		MINING("mining", "Mining", 4),
		QUESTS("quests", "Quests", 64),
		BOSSES("bosses", "Bosses", 124),
		RELICS("relics", "Relics", 184),
		TREASURE("treasure", "Treasure", 244),
		DROPS("drops", "Rare drops", 304);

		public final String id;
		public final String title;
		final int defaultY;

		Section(String id, String title, int defaultY) {
			this.id = id;
			this.title = title;
			this.defaultY = defaultY;
		}
	}

	/** Screen-space rectangle of a drawn box (after scaling). */
	public record Bounds(int x, int y, int w, int h) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 3.0f;
	private static final int PAD = 3;

	private boolean editing;
	private final Map<Section, Bounds> bounds = new EnumMap<>(Section.class);

	public void setEditing(boolean editing) {
		this.editing = editing;
		bounds.clear();
	}

	public boolean editing() {
		return editing;
	}

	/** Bounds from the last frame, only filled while editing. */
	public Map<Section, Bounds> bounds() {
		return bounds;
	}

	public static LSConfig.Element element(Section s) {
		return LSConfig.get().hud.elements.computeIfAbsent(s.id, id -> new LSConfig.Element(4, s.defaultY));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		LSConfig.Hud cfg = LSConfig.get().hud;
		if (editing) {
			bounds.clear();
		} else if (!cfg.enabled || !Game.active()) {
			return;
		}
		// F1 (hidden GUI) is handled by vanilla: HUD elements aren't drawn then.
		int max = Math.max(1, cfg.maxLinesPerSection);
		for (Section s : Section.values()) {
			LSConfig.Element el = element(s);
			if (!el.enabled) continue;
			List<String> lines = section(s, max, editing);
			if (lines.isEmpty()) continue;
			drawBox(graphics, s, el, lines, cfg.backgroundAlpha);
		}
	}

	private void drawBox(GuiGraphicsExtractor graphics, Section s, LSConfig.Element el, List<String> lines, int alpha) {
		Font font = Game.mc().font;
		float scale = clampScale(el.scale);
		int lineH = font.lineHeight + 1;
		int innerW = 0;
		for (String l : lines) innerW = Math.max(innerW, font.width(l));
		int w = innerW + PAD * 2;
		int h = lines.size() * lineH + PAD * 2 - 1;

		// keep the box fully on screen even if the window was resized
		int sw = Math.round(w * scale);
		int sh = Math.round(h * scale);
		int x = Math.max(0, Math.min(el.x, graphics.guiWidth() - sw));
		int y = Math.max(0, Math.min(el.y, graphics.guiHeight() - sh));
		if (editing) bounds.put(s, new Bounds(x, y, sw, sh));

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		int bg = Math.max(0, Math.min(255, alpha)) << 24;
		graphics.fill(0, 0, w, h, bg);
		int ty = PAD;
		for (String l : lines) {
			graphics.text(font, l, PAD, ty, 0xFFFFFFFF);
			ty += lineH;
		}
		graphics.pose().popMatrix();
	}

	public static float clampScale(float s) {
		if (Float.isNaN(s)) return 1f;
		return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
	}

	// ------------------------------------------------------------------ content

	/**
	 * Lines for one box, title first. Returns an empty list when there's nothing to show,
	 * unless {@code force} is set (editor, panel), in which case a placeholder line is used.
	 */
	public List<String> section(Section s, int max, boolean force) {
		List<String> out = new ArrayList<>();
		switch (s) {
			case MINING -> mining(out);
			case QUESTS -> quests(out, max, force);
			case BOSSES -> bosses(out, max, force);
			case RELICS -> relics(out, max, force);
			case TREASURE -> treasure(out, max, force);
			case DROPS -> drops(out, max, force);
		}
		return out;
	}

	/** Every box stacked together, used by the panel's Overview tab. */
	public List<String> all(int max) {
		List<String> out = new ArrayList<>();
		for (Section s : Section.values()) out.addAll(section(s, max, false));
		return out;
	}

	private static void header(List<String> out, String title) {
		out.add("§6" + title);
	}

	private void mining(List<String> out) {
		MiningTracker m = LightSkiesUtils.mining();
		header(out, "Mining");
		out.add(" §fBlocks: §a" + Fmt.compact(m.blocks()) + " §7(" + Fmt.duration(m.elapsedMs()) + ")");
		out.add(" §fNow: §a" + Fmt.compact(m.currentBpm()) + "§7/min  §fAvg: §a" + Fmt.compact(m.sessionBpm()) + "§7/min");
		if (m.money() > 0) {
			out.add(" §fMoney: §a$" + Fmt.compact(m.money()) + " §7($" + Fmt.compact(m.perHour(m.money())) + "/h)");
		}
		if (m.tokens() > 0) {
			out.add(" §fTokens: §d" + Fmt.compact(m.tokens()) + " §7(" + Fmt.compact(m.perHour(m.tokens())) + "/h)");
		}
	}

	private void quests(List<String> out, int max, boolean force) {
		List<QuestTracker.Quest> quests = LightSkiesUtils.quests().visible();
		if (quests.isEmpty() && !force) return;
		header(out, "Quests");
		if (quests.isEmpty()) out.add(" §7No tracked quests");
		int n = 0;
		for (QuestTracker.Quest q : quests) {
			if (n++ >= max) {
				out.add(" §7+" + (quests.size() - max) + " more");
				break;
			}
			if (q.completedAt > 0) {
				out.add(" §a✔ " + q.name);
			} else if (q.max > 0) {
				out.add(" §f" + q.name + " " + bar(q.fraction()) + " §7" + Fmt.compact(q.current) + "/" + Fmt.compact(q.max));
			} else {
				out.add(" §f" + q.name);
			}
		}
	}

	private void bosses(List<String> out, int max, boolean force) {
		BossTracker b = LightSkiesUtils.bosses();
		Map<String, List<String>> summary = b.summary();
		List<BossTracker.LiveBoss> live = b.live();
		if (summary.isEmpty() && live.isEmpty() && !force) return;
		header(out, "Bosses");
		for (BossTracker.LiveBoss l : live.subList(0, Math.min(max, live.size()))) {
			out.add(" §c☠ " + l.name() + " §f" + Math.round(l.distance()) + "m " + l.arrow());
		}
		String here = Game.dimensionName();
		// current dimension first
		List<String> hereLines = summary.remove(here);
		if (hereLines != null) addDim(out, here + " §7(here)", hereLines, max);
		for (Map.Entry<String, List<String>> e : summary.entrySet()) addDim(out, e.getKey(), e.getValue(), max);
		if (summary.isEmpty() && hereLines == null && live.isEmpty()) out.add(" §7Nothing seen yet");
	}

	private static void addDim(List<String> out, String dim, List<String> lines, int max) {
		out.add(" §e" + dim);
		for (int i = 0; i < Math.min(max, lines.size()); i++) out.add("  §f" + lines.get(i));
	}

	private void relics(List<String> out, int max, boolean force) {
		Locator loc = LightSkiesUtils.locator();
		List<Locator.Sighting> near = loc.nearest(Locator.Kind.RELIC, max);
		String progress = loc.relicProgress();
		if (near.isEmpty() && progress == null && !force) return;
		header(out, "Relics" + (progress != null ? " §7(" + progress + ")" : ""));
		if (near.isEmpty()) out.add(" §7None in range");
		for (Locator.Sighting s : near) out.add(sighting(s, "§b"));
	}

	private void treasure(List<String> out, int max, boolean force) {
		Locator loc = LightSkiesUtils.locator();
		List<Locator.Sighting> near = loc.nearest(Locator.Kind.TREASURE, max);
		if (near.isEmpty() && !force) return;
		int total = loc.count(Locator.Kind.TREASURE);
		header(out, "Treasure §7(" + total + " nearby" + (LightSkiesUtils.highlightsOn() ? "" : ", hidden") + ")");
		if (near.isEmpty()) out.add(" §7None in range");
		for (Locator.Sighting s : near) out.add(sighting(s, "§e"));
	}

	private static String sighting(Locator.Sighting s, String colour) {
		String dy = s.dy() == 0 ? "" : (s.dy() > 0 ? " §7▲" + s.dy() : " §7▼" + (-s.dy()));
		return " " + colour + s.label() + " §f" + Math.round(s.distance()) + "m " + s.arrow() + dy;
	}

	private void drops(List<String> out, int max, boolean force) {
		DropTracker d = LightSkiesUtils.drops();
		List<DropTracker.Drop> recent = d.recent();
		if (recent.isEmpty() && !force) return;
		header(out, "Rare drops §7(" + d.sessionTotal() + " this session)");
		if (recent.isEmpty()) out.add(" §7None yet");
		for (int i = 0; i < Math.min(max, recent.size()); i++) {
			DropTracker.Drop drop = recent.get(i);
			out.add(" §d" + (drop.amount() > 1 ? drop.amount() + "x " : "") + drop.item() + " §7" + Fmt.ago(drop.time()));
		}
	}

	private static String bar(double fraction) {
		int filled = (int) Math.round(fraction * 10);
		return "§a" + "|".repeat(filled) + "§8" + "|".repeat(10 - filled);
	}
}
