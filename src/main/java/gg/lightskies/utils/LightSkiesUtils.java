package gg.lightskies.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;

import gg.lightskies.utils.command.LsuCommand;
import gg.lightskies.utils.config.DataStore;
import gg.lightskies.utils.config.LSConfig;
import gg.lightskies.utils.config.Patterns;
import gg.lightskies.utils.hud.HudEditorScreen;
import gg.lightskies.utils.hud.LsHud;
import gg.lightskies.utils.hud.PanelScreen;
import gg.lightskies.utils.render.HighlightRenderer;
import gg.lightskies.utils.scan.Locator;
import gg.lightskies.utils.tracker.BossTracker;
import gg.lightskies.utils.tracker.DropTracker;
import gg.lightskies.utils.tracker.MiningTracker;
import gg.lightskies.utils.tracker.QuestTracker;
import gg.lightskies.utils.tracker.SidebarReader;
import gg.lightskies.utils.util.Game;

public final class LightSkiesUtils implements ClientModInitializer {
	public static final String MOD_ID = "lightskies_utils";
	public static final Logger LOGGER = LoggerFactory.getLogger("LightSkiesUtils");

	private static final MiningTracker MINING = new MiningTracker();
	private static final QuestTracker QUESTS = new QuestTracker();
	private static final BossTracker BOSSES = new BossTracker();
	private static final DropTracker DROPS = new DropTracker();
	private static final Locator LOCATOR = new Locator();
	private static final LsHud HUD = new LsHud();

	private static boolean highlights = true;
	private static boolean debug;
	private static int ticks;
	/** Sidebar lines processed last time, so unchanged lines aren't re-parsed every second. */
	private static Set<String> lastSidebar = new HashSet<>();

	private static KeyMapping toggleHudKey;
	private static KeyMapping toggleHighlightsKey;
	private static KeyMapping editHudKey;
	private static KeyMapping openPanelKey;
	/** Set by /lsu panel; opened next tick because the chat screen closes right after a command runs. */
	private static boolean openPanelNextTick;
	private static boolean openHudEditorNextTick;

	@Override
	public void onInitializeClient() {
		LSConfig.load();
		DataStore.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lightskies_utils.toggle_hud", InputConstants.KEY_H, category));
		toggleHighlightsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lightskies_utils.toggle_highlights", InputConstants.KEY_J, category));
		editHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lightskies_utils.edit_hud", InputConstants.UNKNOWN.getValue(), category));
		// Unbound by default: players pick their own key in Options > Controls > Key Binds > LightSkies Utils.
		openPanelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lightskies_utils.open_panel", InputConstants.UNKNOWN.getValue(), category));

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), HUD);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(HighlightRenderer::render);

		// Server messages: chat, action bar, titles and subtitles all go through the same handler.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleServerText(message, overlay ? "actionbar" : "chat"));
		ClientReceiveMessageEvents.TITLE.register(title -> handleServerText(title, "title"));
		ClientReceiveMessageEvents.SUBTITLE.register(subtitle -> handleServerText(subtitle, "subtitle"));

		ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
			if (!Game.active()) return;
			MINING.onBlockBroken(state);
			LOCATOR.onBlockBroken(pos);
		});

		ClientTickEvents.END_CLIENT_TICK.register(LightSkiesUtils::tick);

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			LOCATOR.clear();
			DROPS.resetInventoryBaseline();
			lastSidebar.clear();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> DataStore.save());

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> LsuCommand.register(dispatcher));

		LOGGER.info("LightSkies Utils loaded");
	}

	private static void tick(Minecraft mc) {
		while (toggleHudKey.consumeClick()) {
			LSConfig.get().hud.enabled = !LSConfig.get().hud.enabled;
			LSConfig.save();
		}
		while (toggleHighlightsKey.consumeClick()) {
			Game.say("Highlights " + (toggleHighlights() ? "§aon" : "§coff"));
		}
		boolean editHud = openHudEditorNextTick;
		openHudEditorNextTick = false;
		while (editHudKey.consumeClick()) editHud = true;
		if (editHud && mc.player != null && mc.gui.screen() == null) {
			mc.gui.setScreen(new HudEditorScreen(null, null));
		}
		boolean openPanel = openPanelNextTick;
		openPanelNextTick = false;
		while (openPanelKey.consumeClick()) openPanel = true;
		if (openPanel && mc.player != null && mc.gui.screen() == null) {
			mc.gui.setScreen(new PanelScreen());
		}

		if (!Game.active()) return;
		ticks++;

		LOCATOR.tick();
		if (ticks % 5 == 0) DROPS.tickInventory();
		if (ticks % 10 == 0) {
			LOCATOR.tickEntities();
			BOSSES.scanEntities();
		}
		if (ticks % 20 == 0 && LSConfig.get().quests.readSidebar) readSidebar();
		if (ticks % (20 * 60 * 5) == 0) DataStore.save();
	}

	private static void readSidebar() {
		List<String> lines = SidebarReader.lines();
		Set<String> now = new HashSet<>(lines);
		for (String line : lines) {
			if (lastSidebar.contains(line)) continue;
			QUESTS.onMessage(line);
			BOSSES.onMessage(line);
		}
		lastSidebar = now;
	}

	public static void handleServerText(Component message, String channel) {
		handleServerText(message.getString(), channel);
	}

	/** Runs one server line through every tracker. Public so /lsu test can call it. */
	public static void handleServerText(String raw, String channel) {
		// /lsu test works anywhere (including singleplayer) so regexes can be tried offline.
		if (!Game.active() && !channel.equals("test")) return;
		String text = Patterns.strip(raw);
		if (text.isEmpty()) return;
		if (debug) LOGGER.info("[LSU msg] [{}] {}", channel, text);
		try {
			MINING.onMessage(text);
			QUESTS.onMessage(text);
			BOSSES.onMessage(text);
			DROPS.onMessage(text);
			LOCATOR.onMessage(text);
		} catch (RuntimeException e) {
			LOGGER.error("Error handling message '{}'", text, e);
		}
	}

	public static void requestOpenPanel() {
		openPanelNextTick = true;
	}

	public static void requestOpenHudEditor() {
		openHudEditorNextTick = true;
	}

	public static KeyMapping openPanelKey() {
		return openPanelKey;
	}

	public static boolean toggleHighlights() {
		highlights = !highlights;
		return highlights;
	}

	public static boolean toggleDebug() {
		debug = !debug;
		return debug;
	}

	public static boolean highlightsOn() {
		return highlights;
	}

	public static MiningTracker mining() {
		return MINING;
	}

	public static QuestTracker quests() {
		return QUESTS;
	}

	public static BossTracker bosses() {
		return BOSSES;
	}

	public static DropTracker drops() {
		return DROPS;
	}

	public static Locator locator() {
		return LOCATOR;
	}

	public static LsHud hud() {
		return HUD;
	}
}
