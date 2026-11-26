package gg.lightskies.utils.tracker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import gg.lightskies.utils.config.DataStore;
import gg.lightskies.utils.config.LSConfig;
import gg.lightskies.utils.config.Patterns;
import gg.lightskies.utils.util.Fmt;
import gg.lightskies.utils.util.Game;

/** Per-dimension boss spawns, deaths, spawn progress and live bosses near you. */
public final class BossTracker {
	public static final class Progress {
		public String boss;
		public double current;
		public double max;
		public long updated;
	}

	public record LiveBoss(String name, double distance, String arrow) {
	}

	/** dimension name -> progress */
	private final Map<String, Progress> progress = new LinkedHashMap<>();
	private final List<LiveBoss> live = new ArrayList<>();

	public void onMessage(String text) {
		LSConfig.Bosses cfg = LSConfig.get().bosses;

		Matcher m = Patterns.firstMatch(cfg.spawnPatterns, text);
		if (m != null && Patterns.group(m, "boss") != null) {
			String key = key(m);
			DataStore.get().lastBossSpawn.put(key, System.currentTimeMillis());
			Game.ping(1.6f);
			return;
		}

		m = Patterns.firstMatch(cfg.deathPatterns, text);
		if (m != null && Patterns.group(m, "boss") != null) {
			DataStore.get().lastBossDeath.put(deathKey(m), System.currentTimeMillis());
			return;
		}

		m = Patterns.firstMatch(cfg.progressPatterns, text);
		if (m != null) {
			String dim = dimensionOf(m);
			Progress p = progress.computeIfAbsent(dim, d -> new Progress());
			p.current = Patterns.number(Patterns.group(m, "current"), p.current);
			p.max = Patterns.number(Patterns.group(m, "max"), p.max);
			String boss = Patterns.group(m, "boss");
			if (boss != null) p.boss = boss;
			p.updated = System.currentTimeMillis();
		}
	}

	/** Called every 10 ticks: finds named boss entities around the player. */
	public void scanEntities() {
		live.clear();
		Minecraft mc = Game.mc();
		List<String> patterns = LSConfig.get().bosses.bossEntityNamePatterns;
		if (patterns.isEmpty() || mc.level == null || mc.player == null) return;

		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || !e.hasCustomName() || e.getCustomName() == null) continue;
			String name = Patterns.strip(e.getCustomName().getString());
			if (!Patterns.anyMatch(patterns, name)) continue;
			double dx = e.getX() - mc.player.getX();
			double dz = e.getZ() - mc.player.getZ();
			live.add(new LiveBoss(name, Math.sqrt(dx * dx + dz * dz), Fmt.arrow(dx, dz, mc.player.getYRot())));
		}
		live.sort((a, b) -> Double.compare(a.distance(), b.distance()));
	}

	/** One line per boss we know about, grouped by dimension name. */
	public Map<String, List<String>> summary() {
		Map<String, List<String>> out = new TreeMap<>();
		DataStore data = DataStore.get();
		Map<String, Integer> respawn = LSConfig.get().bosses.respawnMinutes;

		for (Map.Entry<String, Long> e : data.lastBossSpawn.entrySet()) {
			String[] parts = e.getKey().split("\\|", 2);
			if (parts.length < 2) continue;
			String dim = parts[0];
			String boss = parts[1];
			long spawned = e.getValue();
			Long died = data.lastBossDeath.get(e.getKey());
			boolean alive = died == null || died < spawned;

			StringBuilder line = new StringBuilder(Fmt.titleCase(boss)).append(": ");
			if (alive) {
				line.append("§aALIVE§r (spawned ").append(Fmt.ago(spawned)).append(")");
			} else {
				line.append("killed ").append(Fmt.ago(died));
				Integer mins = respawn.get(boss.toLowerCase());
				if (mins != null) {
					long next = died + mins * 60_000L - System.currentTimeMillis();
					line.append(next > 0 ? ", next ~" + Fmt.duration(next) : ", §edue now§r");
				}
			}
			out.computeIfAbsent(dim, d -> new ArrayList<>()).add(line.toString());
		}

		for (Map.Entry<String, Progress> e : progress.entrySet()) {
			Progress p = e.getValue();
			if (p.max <= 0) continue;
			String who = p.boss == null ? "Boss" : Fmt.titleCase(p.boss);
			out.computeIfAbsent(e.getKey(), d -> new ArrayList<>())
					.add(who + " spawn: " + Fmt.compact(p.current) + "/" + Fmt.compact(p.max)
							+ " (" + Math.round(100 * p.current / p.max) + "%)");
		}
		return out;
	}

	public List<LiveBoss> live() {
		return live;
	}

	public void reset() {
		progress.clear();
		DataStore.get().lastBossSpawn.clear();
		DataStore.get().lastBossDeath.clear();
	}

	/**
	 * Death messages often don't say where the boss was. In that case, attribute the death to the
	 * most recent spawn of a boss with the same name, in any dimension.
	 */
	private static String deathKey(Matcher m) {
		if (Patterns.group(m, "dimension") != null) return key(m);
		String suffix = "|" + Patterns.group(m, "boss").toLowerCase();
		String best = null;
		long bestTime = Long.MIN_VALUE;
		for (Map.Entry<String, Long> e : DataStore.get().lastBossSpawn.entrySet()) {
			if (e.getKey().endsWith(suffix) && e.getValue() > bestTime) {
				best = e.getKey();
				bestTime = e.getValue();
			}
		}
		return best != null ? best : key(m);
	}

	private static String key(Matcher m) {
		return dimensionOf(m) + "|" + Patterns.group(m, "boss").toLowerCase();
	}

	private static String dimensionOf(Matcher m) {
		String dim = Patterns.group(m, "dimension");
		return dim != null ? Fmt.titleCase(dim.toLowerCase()) : Game.dimensionName();
	}
}
