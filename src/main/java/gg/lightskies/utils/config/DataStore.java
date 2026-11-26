package gg.lightskies.utils.config;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import gg.lightskies.utils.LightSkiesUtils;

/** Data that should survive restarts: all-time rare drops and last-known boss spawns. */
public final class DataStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("lightskies_utils_data.json");

	private static DataStore instance = new DataStore();

	/** item name -> total count, across all sessions. */
	public Map<String, Long> allTimeDrops = new LinkedHashMap<>();
	/** "dimension|boss" -> epoch ms of last spawn seen. */
	public Map<String, Long> lastBossSpawn = new LinkedHashMap<>();
	/** "dimension|boss" -> epoch ms of last death seen. */
	public Map<String, Long> lastBossDeath = new LinkedHashMap<>();
	public long totalBlocksMined;

	public static DataStore get() {
		return instance;
	}

	public static void load() {
		if (!Files.exists(PATH)) return;
		try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
			DataStore loaded = GSON.fromJson(reader, DataStore.class);
			if (loaded != null) instance = loaded;
		} catch (Exception e) {
			LightSkiesUtils.LOGGER.error("Could not read {}", PATH, e);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (Exception e) {
			LightSkiesUtils.LOGGER.error("Could not save {}", PATH, e);
		}
	}
}
