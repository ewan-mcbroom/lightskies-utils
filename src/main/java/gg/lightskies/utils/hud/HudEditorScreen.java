package gg.lightskies.utils.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import gg.lightskies.utils.LightSkiesUtils;
import gg.lightskies.utils.config.LSConfig;

/**
 * Drag-and-drop HUD layout editor. The real HUD keeps drawing underneath (with placeholders for
 * empty boxes), this screen just adds outlines, mouse handling and a toolbar.
 *
 * - Click and drag a box to move it.
 * - Scroll over a box, or use the − / + buttons, to resize the selected box.
 * - The toggle row shows or hides each box.
 */
public final class HudEditorScreen extends Screen {
	private static final int OUTLINE = 0xFF8A8A8A;
	private static final int OUTLINE_SELECTED = 0xFFFFD84A;
	private static final float SCALE_STEP = 0.1f;

	private final Screen parent;
	private LsHud.Section selected;
	private boolean dragging;
	private double dragOffX, dragOffY;

	private final List<Button> visibilityButtons = new ArrayList<>();
	private Button sizeButton;
	private Button minusButton;
	private Button plusButton;

	public HudEditorScreen(Screen parent, LsHud.Section focus) {
		super(Component.literal("Edit HUD layout"));
		this.parent = parent;
		this.selected = focus != null ? focus : LsHud.Section.MINING;
	}

	private static LsHud hud() {
		return LightSkiesUtils.hud();
	}

	@Override
	protected void init() {
		hud().setEditing(true);
		visibilityButtons.clear();

		// row 1: show/hide toggles, one per box
		LsHud.Section[] sections = LsHud.Section.values();
		int tw = 68;
		int rowW = sections.length * (tw + 2) - 2;
		int x = (this.width - rowW) / 2;
		int row1 = this.height - 48;
		for (LsHud.Section s : sections) {
			Button b = this.addRenderableWidget(Button.builder(visibilityText(s), btn -> {
				LSConfig.Element el = LsHud.element(s);
				el.enabled = !el.enabled;
				selected = s;
				LSConfig.save();
				refreshButtons();
			}).pos(x, row1).size(tw, 20).build());
			visibilityButtons.add(b);
			x += tw + 2;
		}

		// row 2: size controls for the selected box, reset, done
		int row2 = this.height - 24;
		int cx = this.width / 2;
		minusButton = this.addRenderableWidget(Button.builder(Component.literal("−"), b -> changeScale(-SCALE_STEP))
				.pos(cx - 170, row2).size(20, 20).build());
		sizeButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> setScale(1.0f))
				.pos(cx - 148, row2).size(116, 20).build());
		plusButton = this.addRenderableWidget(Button.builder(Component.literal("+"), b -> changeScale(SCALE_STEP))
				.pos(cx - 30, row2).size(20, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Reset layout"), b -> resetLayout())
				.pos(cx - 6, row2).size(84, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.pos(cx + 82, row2).size(88, 20).build());

		refreshButtons();
	}

	private void refreshButtons() {
		LsHud.Section[] sections = LsHud.Section.values();
		for (int i = 0; i < sections.length && i < visibilityButtons.size(); i++) {
			visibilityButtons.get(i).setMessage(visibilityText(sections[i]));
		}
		LSConfig.Element el = LsHud.element(selected);
		int pct = Math.round(LsHud.clampScale(el.scale) * 100);
		sizeButton.setMessage(Component.literal(selected.title + ": " + pct + "%"));
		boolean on = el.enabled;
		sizeButton.active = on;
		minusButton.active = on && el.scale > LsHud.MIN_SCALE + 0.001f;
		plusButton.active = on && el.scale < LsHud.MAX_SCALE - 0.001f;
	}

	private static Component visibilityText(LsHud.Section s) {
		boolean on = LsHud.element(s).enabled;
		return Component.literal((on ? "§a✔ " : "§c✘ ") + "§r" + s.title);
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// No blurred background: the HUD underneath must stay visible.
		graphics.centeredText(this.font, "§eDrag boxes to move them  §7·  §eScroll over a box to resize  §7·  §eToggle boxes below",
				this.width / 2, 6, 0xFFFFFFFF);
		if (!LSConfig.get().hud.enabled) {
			graphics.centeredText(this.font, "§cThe HUD is switched off in Settings. Boxes are shown here so you can still arrange them.",
					this.width / 2, 18, 0xFFFFFFFF);
		}

		for (Map.Entry<LsHud.Section, LsHud.Bounds> e : hud().bounds().entrySet()) {
			LsHud.Bounds b = e.getValue();
			boolean sel = e.getKey() == selected;
			boolean hover = b.contains(mouseX, mouseY);
			outline(graphics, b, sel ? OUTLINE_SELECTED : (hover ? 0xFFFFFFFF : OUTLINE));
			if (sel) {
				String label = e.getKey().title + " " + Math.round(LsHud.clampScale(LsHud.element(e.getKey()).scale) * 100) + "%";
				int ly = b.y() >= 11 ? b.y() - 10 : b.y() + b.h() + 2;
				graphics.text(this.font, "§e" + label, b.x(), ly, 0xFFFFFFFF);
			}
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private static void outline(GuiGraphicsExtractor g, LsHud.Bounds b, int colour) {
		int x0 = b.x() - 1, y0 = b.y() - 1, x1 = b.x() + b.w() + 1, y1 = b.y() + b.h() + 1;
		g.fill(x0, y0, x1, y0 + 1, colour);
		g.fill(x0, y1 - 1, x1, y1, colour);
		g.fill(x0, y0, x0 + 1, y1, colour);
		g.fill(x1 - 1, y0, x1, y1, colour);
	}

	// ------------------------------------------------------------------ mouse

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		LsHud.Section hit = hitTest(event.x(), event.y());
		if (hit == null) return false;
		selected = hit;
		LsHud.Bounds b = hud().bounds().get(hit);
		LSConfig.Element el = LsHud.element(hit);
		// sync stored position with where it's actually drawn (it may have been clamped)
		el.x = b.x();
		el.y = b.y();
		dragOffX = event.x() - b.x();
		dragOffY = event.y() - b.y();
		dragging = true;
		refreshButtons();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!dragging) return super.mouseDragged(event, dx, dy);
		LsHud.Bounds b = hud().bounds().get(selected);
		int w = b != null ? b.w() : 0;
		int h = b != null ? b.h() : 0;
		LSConfig.Element el = LsHud.element(selected);
		el.x = (int) Math.round(Math.max(0, Math.min(this.width - w, event.x() - dragOffX)));
		el.y = (int) Math.round(Math.max(0, Math.min(this.height - h, event.y() - dragOffY)));
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			LSConfig.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		LsHud.Section hit = hitTest(mouseX, mouseY);
		if (hit != null) selected = hit;
		if (scrollY != 0) changeScale((float) Math.signum(scrollY) * SCALE_STEP);
		return true;
	}

	private LsHud.Section hitTest(double x, double y) {
		// last drawn is on top, so search backwards
		LsHud.Section found = null;
		for (Map.Entry<LsHud.Section, LsHud.Bounds> e : hud().bounds().entrySet()) {
			if (e.getValue().contains(x, y)) found = e.getKey();
		}
		return found;
	}

	// ------------------------------------------------------------------ actions

	private void changeScale(float delta) {
		LSConfig.Element el = LsHud.element(selected);
		setScale(el.scale + delta);
	}

	private void setScale(float scale) {
		LSConfig.Element el = LsHud.element(selected);
		float rounded = Math.round(LsHud.clampScale(scale) * 10) / 10f;
		el.scale = Float.parseFloat(String.format(Locale.ROOT, "%.1f", rounded));
		LSConfig.save();
		refreshButtons();
	}

	private void resetLayout() {
		LSConfig.get().hud.elements.clear(); // defaults are recreated on the next frame
		LSConfig.save();
		refreshButtons();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		hud().setEditing(false);
		LSConfig.save();
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void removed() {
		// covers closing by any route (e.g. disconnect), so the HUD never gets stuck in edit mode
		hud().setEditing(false);
		super.removed();
	}
}
