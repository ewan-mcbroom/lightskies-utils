package gg.lightskies.utils.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import gg.lightskies.utils.LightSkiesUtils;

/**
 * All settings live in config/lightskies_utils.json. Every server message the mod reacts to is a
 * regex here, so staff can match the real LightSkies wording without recompiling.
 *
 * Regexes run against the message with colour codes stripped. Named groups are used to pull
 * values out, e.g. (?<amount>...), (?<boss>...), (?<dimension>...). See README.md for the full list.
 */
public final class LSConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("lightskies_utils.json");

	private static LSConfig instance = new LSConfig();

	// ---------------------------------------------------------------- general
	/** When true the mod is idle on any server whose address doesn't contain one of the strings below. */
	public boolean onlyOnLightSkies = true;
	public List<String> serverAddressContains = new ArrayList<>(List.of("lightskies"));

	// ---------------------------------------------------------------- HUD
	public Hud hud = new Hud();

	public static final class Hud {
		/** Master switch for all HUD boxes. */
		public boolean enabled = true;
		/** 0-255 */
		public int backgroundAlpha = 110;
		/** Max lines per list box (quests, relics, treasure, drops, bosses). */
		public int maxLinesPerSection = 5;
		/**
		 * One entry per HUD box, keyed by id: mining, quests, bosses, relics, treasure, drops.
		 * Missing ids are filled with defaults when first drawn. Edit these in-game with the HUD editor.
		 */
		public Map<String, Element> elements = new LinkedHashMap<>();
	}

	public static final class Element {
		public boolean enabled = true;
		/** Top-left corner in GUI pixels (same units as the GUI scale). */
		public int x;
		public int y;
		/** 0.5 - 3.0 */
		public float scale = 1.0f;

		public Element() {
		}

		public Element(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}

	// ---------------------------------------------------------------- mining
	public Mining mining = new Mining();

	public static final class Mining {
		/** Only count these block ids as "mined" (e.g. "minecraft:diamond_ore"). Empty = count everything. */
		public List<String> countedBlockIds = new ArrayList<>();
		/**
		 * Chat lines that pay you (sell messages, auto-compressor payouts...). Needs group (?<amount>).
		 * Commas in the number are ignored. Optional (?<multiplier>k|m|b) suffix group.
		 */
		public List<String> moneyPatterns = new ArrayList<>(List.of(
				"(?i)^\\s*(?:\\+|sold .* for )\\$(?<amount>[\\d,]+(?:\\.\\d+)?)(?<multiplier>[kmb])?"
		));
		/** Chat lines that grant tokens/XP/etc. Needs (?<amount>). Shown as a second rate line. */
		public List<String> tokenPatterns = new ArrayList<>(List.of(
				"(?i)^\\s*\\+(?<amount>[\\d,]+) tokens?"
		));
		/** Reset the session stats after this many minutes AFK (no blocks broken). 0 = never. */
		public int autoResetAfterIdleMinutes = 0;
	}

	// ---------------------------------------------------------------- quests
	public Quests quests = new Quests();

	public static final class Quests {
		/** Needs (?<name>), (?<current>), (?<max>). */
		public List<String> progressPatterns = new ArrayList<>(List.of(
				"(?i)^\\s*quest(?: progress)?\\s*[:»>-]\\s*(?<name>.+?)\\s*[\\(\\[]?(?<current>[\\d,]+)\\s*/\\s*(?<max>[\\d,]+)[\\)\\]]?\\s*$"
		));
		/** Needs (?<name>). Marks a quest done; it is removed from the HUD after a few seconds. */
		public List<String> completePatterns = new ArrayList<>(List.of(
				"(?i)quest complete(?:d)?\\s*[:!»>-]*\\s*(?<name>.+?)\\s*!?$"
		));
		/** Needs (?<name>). Adds a quest at 0 progress, optional (?<max>). */
		public List<String> startPatterns = new ArrayList<>(List.of(
				"(?i)new quest[:!»>-]?\\s*(?<name>.+?)(?:\\s*\\(0\\s*/\\s*(?<max>[\\d,]+)\\))?\\s*$"
		));
		/** Also read quest lines from the sidebar scoreboard matching progressPatterns. */
		public boolean readSidebar = true;
	}

	// ---------------------------------------------------------------- bosses
	public Bosses bosses = new Bosses();

	public static final class Bosses {
		/** Needs (?<boss>). Optional (?<dimension>), otherwise the dimension you're in is used. */
		public List<String> spawnPatterns = new ArrayList<>(List.of(
				"(?i)(?:the )?(?<boss>[\\w' ]+?) has (?:spawned|appeared|awoken)(?: in (?:the )?(?<dimension>[\\w' ]+))?[!.]?$"
		));
		/** Needs (?<boss>). Optional (?<dimension>). */
		public List<String> deathPatterns = new ArrayList<>(List.of(
				"(?i)(?:the )?(?<boss>[\\w' ]+?) has been (?:slain|defeated|killed)(?: in (?:the )?(?<dimension>[\\w' ]+))?.*$"
		));
		/** "Blocks until boss" style progress. Needs (?<current>) and (?<max>), optional (?<boss>)/(?<dimension>). */
		public List<String> progressPatterns = new ArrayList<>(List.of(
				"(?i)boss(?: spawn)?(?: progress)?\\s*[:»>-]\\s*(?<current>[\\d,]+)\\s*/\\s*(?<max>[\\d,]+)"
		));
		/** Known respawn timers, boss name (lower case) -> minutes. Used to show "next in ~X". */
		public Map<String, Integer> respawnMinutes = new LinkedHashMap<>();
		/** Regex on entity custom names that identify a live boss near you. */
		public List<String> bossEntityNamePatterns = new ArrayList<>();
		/** Friendly names for dimension ids, e.g. "minecraft:the_nether" -> "Nether". */
		public Map<String, String> dimensionNames = new LinkedHashMap<>(Map.of(
				"minecraft:overworld", "Overworld",
				"minecraft:the_nether", "Nether",
				"minecraft:the_end", "End"
		));
	}

	// ---------------------------------------------------------------- relics
	public Relics relics = new Relics();

	public static final class Relics {
		public boolean enabled = true;
		/** dimension id (or "*" for all) -> relic block ids. */
		public Map<String, List<String>> blockIdsByDimension = new LinkedHashMap<>();
		/** Relics that are entities (armor stands, item/block displays...): regex on custom name. */
		public List<String> entityNamePatterns = new ArrayList<>(List.of("(?i)relic"));
		/** Needs (?<name>). Optional (?<current>)/(?<max>) to track "relics found 3/10". */
		public List<String> foundPatterns = new ArrayList<>(List.of(
				"(?i)you (?:found|discovered) (?:a |the )?relic[:!»>-]?\\s*(?<name>.*?)(?:\\s*\\((?<current>\\d+)\\s*/\\s*(?<max>\\d+)\\))?\\s*$"
		));
		/** ARGB colour of the highlight box. */
		public int color = 0x6000E5FF;
		public boolean pingSound = true;
	}

	// ---------------------------------------------------------------- treasure
	public Treasure treasure = new Treasure();

	public static final class Treasure {
		public boolean enabled = true;
		public List<String> blockIds = new ArrayList<>(List.of(
				"minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:ender_chest"
		));
		public List<String> entityNamePatterns = new ArrayList<>(List.of("(?i)treasure"));
		public int color = 0x60FFC400;
		public boolean pingSound = true;
	}

	// ---------------------------------------------------------------- scanner
	public Scan scan = new Scan();

	public static final class Scan {
		public int horizontalRadius = 32;
		public int verticalRadius = 24;
		/** How many blocks are checked per client tick. Higher = faster updates, more CPU. */
		public int blocksPerTick = 25000;
		/** Max boxes drawn in the world at once. */
		public int maxHighlights = 200;
	}

	// ---------------------------------------------------------------- rare drops
	public Drops drops = new Drops();

	public static final class Drops {
		/** Needs (?<item>). Optional (?<amount>) and (?<rarity>). */
		public List<String> chatPatterns = new ArrayList<>(List.of(
				"(?i)^\\s*(?:rare|epic|legendary|mythic)?\\s*drop[!:»>-]+\\s*(?:you (?:found|got|received) )?(?:(?<amount>\\d+)x? )?(?<item>.+?)!?$",
				"(?i)^\\s*(?<rarity>rare|epic|legendary|mythic|godly)!?\\s+(?:find|drop)[!:»>-]+\\s*(?:(?<amount>\\d+)x? )?(?<item>.+?)!?$"
		));
		/** Item ids that count as rare when they appear in your inventory. */
		public List<String> rareItemIds = new ArrayList<>();
		/** Case-insensitive words in an item's display name that count as rare. */
		public List<String> rareNameKeywords = new ArrayList<>(List.of("relic", "legendary", "mythic", "key"));
		public boolean alertSound = true;
	}

	// ---------------------------------------------------------------- io
	public static LSConfig get() {
		return instance;
	}

	public static void load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				LSConfig loaded = GSON.fromJson(reader, LSConfig.class);
				if (loaded != null) instance = loaded;
			} catch (Exception e) {
				LightSkiesUtils.LOGGER.error("Could not read {}, using defaults", PATH, e);
			}
		}
		save(); // writes defaults for any new fields
		Patterns.clearCache();
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			LightSkiesUtils.LOGGER.error("Could not save {}", PATH, e);
		}
	}
}
