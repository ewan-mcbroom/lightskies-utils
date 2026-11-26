package gg.lightskies.utils.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
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

/** Draws the info panel. Lines may use § colour codes. */
public final class LsHud implements HudElement {
	public enum Page { ALL, MINING, QUESTS, BOSSES, LOCATOR, DROPS }

	private static final int PAD = 3;
	private Page page = Page.ALL;

	public Page cyclePage() {
		Page[] all = Page.values();
		page = all[(page.ordinal() + 1) % all.length];
		return page;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Game.mc();
		LSConfig.Hud cfg = LSConfig.get().hud;
		// F1 (hidden GUI) is handled by vanilla: HUD elements aren't drawn then.
		if (!cfg.enabled || !Game.active()) return;

		List<String> lines = buildLines(cfg);
		if (lines.isEmpty()) return;

		Font font = mc.font;
		int width = 0;
		for (String l : lines) width = Math.max(width, font.width(l));
		int lineH = font.lineHeight + 1;
		int x = cfg.x;
		int y = cfg.y;
		int bg = (Math.max(0, Math.min(255, cfg.backgroundAlpha)) << 24);
		graphics.fill(x, y, x + width + PAD * 2, y + lines.size() * lineH + PAD * 2 - 1, bg);

		int ty = y + PAD;
		for (String l : lines) {
			graphics.text(font, l, x + PAD, ty, 0xFFFFFFFF);
			ty += lineH;
		}
	}

	private List<String> buildLines(LSConfig.Hud cfg) {
		List<String> out = new ArrayList<>();
		int max = Math.max(1, cfg.maxLinesPerSection);
		out.add("§b§lLightSkies Utils§r §7[" + Fmt.titleCase(page.name().toLowerCase()) + "]");

		if (show(Page.MINING, cfg.showMining)) mining(out);
		if (show(Page.QUESTS, cfg.showQuests)) quests(out, max);
		if (show(Page.BOSSES, cfg.showBosses)) bosses(out, max);
		if (show(Page.LOCATOR, cfg.showRelics)) relics(out, max);
		if (show(Page.LOCATOR, cfg.showTreasure)) treasure(out, max);
		if (show(Page.DROPS, cfg.showDrops)) drops(out, max);
		return out;
	}

	private boolean show(Page section, boolean enabled) {
		return enabled && (page == Page.ALL || page == section);
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

	private void quests(List<String> out, int max) {
		List<QuestTracker.Quest> quests = LightSkiesUtils.quests().visible();
		if (quests.isEmpty() && page != Page.QUESTS) return;
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

	private void bosses(List<String> out, int max) {
		BossTracker b = LightSkiesUtils.bosses();
		Map<String, List<String>> summary = b.summary();
		List<BossTracker.LiveBoss> live = b.live();
		if (summary.isEmpty() && live.isEmpty() && page != Page.BOSSES) return;
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

	private void relics(List<String> out, int max) {
		Locator loc = LightSkiesUtils.locator();
		List<Locator.Sighting> near = loc.nearest(Locator.Kind.RELIC, max);
		String progress = loc.relicProgress();
		if (near.isEmpty() && progress == null && page != Page.LOCATOR) return;
		header(out, "Relics" + (progress != null ? " §7(" + progress + ")" : ""));
		if (near.isEmpty()) out.add(" §7None in range");
		for (Locator.Sighting s : near) out.add(sighting(s, "§b"));
	}

	private void treasure(List<String> out, int max) {
		Locator loc = LightSkiesUtils.locator();
		List<Locator.Sighting> near = loc.nearest(Locator.Kind.TREASURE, max);
		if (near.isEmpty() && page != Page.LOCATOR) return;
		int total = loc.count(Locator.Kind.TREASURE);
		header(out, "Treasure §7(" + total + " nearby" + (LightSkiesUtils.highlightsOn() ? "" : ", hidden") + ")");
		if (near.isEmpty()) out.add(" §7None in range");
		for (Locator.Sighting s : near) out.add(sighting(s, "§e"));
	}

	private static String sighting(Locator.Sighting s, String colour) {
		String dy = s.dy() == 0 ? "" : (s.dy() > 0 ? " §7▲" + s.dy() : " §7▼" + (-s.dy()));
		return " " + colour + s.label() + " §f" + Math.round(s.distance()) + "m " + s.arrow() + dy;
	}

	private void drops(List<String> out, int max) {
		DropTracker d = LightSkiesUtils.drops();
		List<DropTracker.Drop> recent = d.recent();
		if (recent.isEmpty() && page != Page.DROPS) return;
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
