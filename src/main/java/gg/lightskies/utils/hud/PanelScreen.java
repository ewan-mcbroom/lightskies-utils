package gg.lightskies.utils.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;


import gg.lightskies.utils.LightSkiesUtils;
import gg.lightskies.utils.config.DataStore;
import gg.lightskies.utils.config.LSConfig;
import gg.lightskies.utils.tracker.DropTracker;
import gg.lightskies.utils.tracker.MiningTracker;
import gg.lightskies.utils.util.Fmt;

/**
 * The LightSkies Utils panel: one tab per feature with full (not HUD-truncated) details,
 * plus a Settings tab with toggles. Opened with the "Open panel" keybind or /lsu panel.
 */
public final class PanelScreen extends Screen {
	public enum Tab {
		OVERVIEW("Overview"), MINING("Mining"), QUESTS("Quests"), BOSSES("Bosses"),
		LOCATOR("Locator"), DROPS("Drops"), SETTINGS("Settings");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static final int TOP = 32;
	private static final int BOTTOM_BAR = 30;
	private static final int PANEL_BG = 0xC0101018;
	private static final int TAB_LINE = 0xFF55CCFF;

	/** Remembered between openings so the panel reopens where you left it. */
	private static Tab lastTab = Tab.OVERVIEW;

	private final Tab tab;
	private int scroll;
	private int contentBottomLimit;

	public PanelScreen() {
		this(lastTab);
	}

	private PanelScreen(Tab tab) {
		super(Component.literal("LightSkies Utils"));
		this.tab = tab;
		lastTab = tab;
	}

	@Override
	protected void init() {
		// --- tab row
		Tab[] tabs = Tab.values();
		int tabW = Math.min(70, (this.width - 20) / tabs.length - 2);
		int rowW = tabs.length * (tabW + 2) - 2;
		int x = (this.width - rowW) / 2;
		for (Tab t : tabs) {
			Button b = this.addRenderableWidget(Button.builder(Component.literal(t.label), btn -> switchTo(t))
					.pos(x, 6).size(tabW, 20).build());
			b.active = t != this.tab;
			x += tabW + 2;
		}

		// --- bottom row
		List<Button> bottom = new ArrayList<>();
		switch (this.tab) {
			case MINING -> bottom.add(button("Reset mining", () -> LightSkiesUtils.mining().reset()));
			case QUESTS -> bottom.add(button("Clear quests", () -> LightSkiesUtils.quests().clear()));
			case BOSSES -> bottom.add(button("Reset bosses", () -> {
				LightSkiesUtils.bosses().reset();
				DataStore.save();
			}));
			case LOCATOR -> {
				bottom.add(button("Rescan", () -> LightSkiesUtils.locator().clear()));
				bottom.add(button("Reset relics", () -> LightSkiesUtils.locator().resetRelicProgress()));
			}
			case DROPS -> bottom.add(button("Reset session", () -> LightSkiesUtils.drops().reset()));
			case SETTINGS -> bottom.add(button("Reload config", () -> {
				LSConfig.load();
				LightSkiesUtils.locator().reloadConfig();
				switchTo(Tab.SETTINGS);
			}));
			default -> {
			}
		}
		bottom.add(button("Close", this::onClose));

		int bw = 100;
		int total = bottom.size() * (bw + 4) - 4;
		int bx = (this.width - total) / 2;
		for (Button b : bottom) {
			b.setX(bx);
			b.setY(this.height - BOTTOM_BAR + 5);
			this.addRenderableWidget(b);
			bx += bw + 4;
		}

		if (this.tab == Tab.SETTINGS) initSettings();
	}

	private void initSettings() {
		LSConfig cfg = LSConfig.get();
		int colW = 150;
		int gap = 6;
		int left = this.width / 2 - colW - gap / 2;
		int right = left + colW + gap;
		int y = TOP + 6;

		// HUD boxes: show/hide + "Move" (opens the drag-and-drop editor focused on that box)
		LsHud.Section[] sections = LsHud.Section.values();
		for (int i = 0; i < sections.length; i++) {
			LsHud.Section sec = sections[i];
			int bx = i % 2 == 0 ? left : right;
			int by = y + (i / 2) * 22;
			LSConfig.Element el = LsHud.element(sec);
			Button t = toggle(sec.title, () -> el.enabled, v -> el.enabled = v);
			t.setX(bx);
			t.setY(by);
			t.setWidth(colW - 48);
			this.addRenderableWidget(t);
			this.addRenderableWidget(Button.builder(Component.literal("Move"), btn -> openEditor(sec))
					.pos(bx + colW - 46, by).size(46, 20).build());
		}
		y += ((sections.length + 1) / 2) * 22 + 6;

		List<Button> toggles = new ArrayList<>();
		toggles.add(toggle("HUD", () -> cfg.hud.enabled, v -> cfg.hud.enabled = v));
		toggles.add(Button.builder(Component.literal("§eEdit HUD layout…"), btn -> openEditor(null)).size(colW, 20).build());
		toggles.add(toggle("World highlights", LightSkiesUtils::highlightsOn, v -> LightSkiesUtils.toggleHighlights()));
		toggles.add(toggle("Rare drop sound", () -> cfg.drops.alertSound, v -> cfg.drops.alertSound = v));
		toggles.add(toggle("Relic locator", () -> cfg.relics.enabled, v -> {
			cfg.relics.enabled = v;
			LightSkiesUtils.locator().reloadConfig();
		}));
		toggles.add(toggle("Treasure finder", () -> cfg.treasure.enabled, v -> {
			cfg.treasure.enabled = v;
			LightSkiesUtils.locator().reloadConfig();
		}));
		toggles.add(toggle("Relic ping", () -> cfg.relics.pingSound, v -> cfg.relics.pingSound = v));
		toggles.add(toggle("Treasure ping", () -> cfg.treasure.pingSound, v -> cfg.treasure.pingSound = v));
		toggles.add(toggle("Only on LightSkies", () -> cfg.onlyOnLightSkies, v -> cfg.onlyOnLightSkies = v));
		toggles.add(toggle("Read sidebar quests", () -> cfg.quests.readSidebar, v -> cfg.quests.readSidebar = v));
		toggles.add(cycle("HUD background", () -> cfg.hud.backgroundAlpha + "", () -> {
			int[] steps = {0, 60, 110, 170, 230};
			int next = steps[0];
			for (int st : steps) {
				if (st > cfg.hud.backgroundAlpha) {
					next = st;
					break;
				}
			}
			cfg.hud.backgroundAlpha = next;
		}));
		toggles.add(cycle("Lines per box", () -> cfg.hud.maxLinesPerSection + "", () ->
				cfg.hud.maxLinesPerSection = cfg.hud.maxLinesPerSection >= 10 ? 1 : cfg.hud.maxLinesPerSection + 1));

		for (int i = 0; i < toggles.size(); i++) {
			Button b = toggles.get(i);
			b.setX(i % 2 == 0 ? left : right);
			b.setY(y + (i / 2) * 22);
			b.setWidth(colW);
			this.addRenderableWidget(b);
		}
		contentBottomLimit = y + ((toggles.size() + 1) / 2) * 22 + 2;
	}

	private void openEditor(LsHud.Section focus) {
		LSConfig.save();
		this.minecraft.gui.setScreen(new HudEditorScreen(this, focus));
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.extractBackground(graphics, mouseX, mouseY, delta);

		int left = Math.max(10, this.width / 2 - 200);
		int right = Math.min(this.width - 10, this.width / 2 + 200);
		graphics.fill(left - 6, TOP - 4, right + 6, this.height - BOTTOM_BAR, PANEL_BG);
		graphics.fill(left - 6, TOP - 4, right + 6, TOP - 3, TAB_LINE);

		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (this.tab == Tab.SETTINGS) {
			int y = Math.min(contentBottomLimit, this.height - BOTTOM_BAR - 24);
			graphics.text(this.font, "§7Click §fMove§7 to drag a box around and resize it.  Config: config/lightskies_utils.json",
					left, y + 4, 0xFFFFFFFF);
			return;
		}

		List<String> lines = content();
		int lineH = this.font.lineHeight + 2;
		int areaTop = TOP + 4;
		int visible = Math.max(1, (this.height - BOTTOM_BAR - 4 - areaTop) / lineH);
		int maxScroll = Math.max(0, lines.size() - visible);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		int y = areaTop;
		for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
			graphics.text(this.font, lines.get(i), left, y, 0xFFFFFFFF);
			y += lineH;
		}
		if (maxScroll > 0) {
			// simple scrollbar
			int trackTop = areaTop;
			int trackBottom = this.height - BOTTOM_BAR - 4;
			int trackH = trackBottom - trackTop;
			int thumbH = Math.max(10, trackH * visible / lines.size());
			int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
			graphics.fill(right + 2, trackTop, right + 4, trackBottom, 0x40FFFFFF);
			graphics.fill(right + 2, thumbY, right + 4, thumbY + thumbH, 0xC0FFFFFF);
		}
		if (lines.isEmpty()) graphics.text(this.font, "§7Nothing here yet.", left, areaTop, 0xFFFFFFFF);
	}

	private List<String> content() {
		LsHud hud = LightSkiesUtils.hud();
		return switch (this.tab) {
			case OVERVIEW -> hud.all(5);
			case MINING -> mining();
			case QUESTS -> hud.section(LsHud.Section.QUESTS, 100, true);
			case BOSSES -> hud.section(LsHud.Section.BOSSES, 100, true);
			case LOCATOR -> {
				List<String> l = new ArrayList<>(hud.section(LsHud.Section.RELICS, 30, true));
				l.addAll(hud.section(LsHud.Section.TREASURE, 30, true));
				yield l;
			}
			case DROPS -> drops();
			case SETTINGS -> List.of();
		};
	}

	private static List<String> mining() {
		MiningTracker m = LightSkiesUtils.mining();
		List<String> out = new ArrayList<>(LightSkiesUtils.hud().section(LsHud.Section.MINING, 5, true));
		out.add(" §fAll-time blocks: §a" + Fmt.compact(DataStore.get().totalBlocksMined));
		if (!m.perBlock().isEmpty()) {
			out.add("§6Blocks this session");
			m.perBlock().entrySet().stream()
					.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
					.forEach(e -> out.add(" §f" + prettyId(e.getKey()) + " §7× §a" + Fmt.compact(e.getValue())));
		}
		return out;
	}

	private static List<String> drops() {
		DropTracker d = LightSkiesUtils.drops();
		List<String> out = new ArrayList<>(LightSkiesUtils.hud().section(LsHud.Section.DROPS, 15, true));
		if (!d.session().isEmpty()) {
			out.add("§6Session totals");
			d.session().entrySet().stream()
					.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
					.forEach(e -> out.add(" §d" + e.getValue() + "x §f" + e.getKey()));
		}
		Map<String, Long> allTime = DataStore.get().allTimeDrops;
		if (!allTime.isEmpty()) {
			long total = allTime.values().stream().mapToLong(Long::longValue).sum();
			out.add("§6All time §7(" + total + ")");
			allTime.entrySet().stream()
					.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
					.forEach(e -> out.add(" §d" + e.getValue() + "x §f" + e.getKey()));
		}
		return out;
	}

	private static String prettyId(String id) {
		int colon = id.indexOf(':');
		return Fmt.titleCase(colon >= 0 && id.startsWith("minecraft:") ? id.substring(colon + 1) : id);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= (int) Math.signum(scrollY) * 3;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// The same key that opens the panel also closes it.
		if (LightSkiesUtils.openPanelKey().matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		LSConfig.save();
		super.onClose();
	}

	// ------------------------------------------------------------------ helpers

	private void switchTo(Tab t) {
		this.minecraft.gui.setScreen(new PanelScreen(t));
	}

	private static Button button(String label, Runnable action) {
		return Button.builder(Component.literal(label), b -> action.run()).size(100, 20).build();
	}

	private static Button toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
		return Button.builder(toggleText(label, get.getAsBoolean()), b -> {
			set.accept(!get.getAsBoolean());
			b.setMessage(toggleText(label, get.getAsBoolean()));
			LSConfig.save();
		}).size(150, 20).build();
	}

	private static Component toggleText(String label, boolean on) {
		return Component.literal(label + ": " + (on ? "§aON" : "§cOFF"));
	}

	private static Button cycle(String label, java.util.function.Supplier<String> value, Runnable next) {
		return Button.builder(Component.literal(label + ": " + value.get()), b -> {
			next.run();
			b.setMessage(Component.literal(label + ": " + value.get()));
			LSConfig.save();
		}).size(150, 20).build();
	}
}
