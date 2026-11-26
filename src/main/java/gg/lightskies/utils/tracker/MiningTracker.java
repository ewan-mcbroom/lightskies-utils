package gg.lightskies.utils.tracker;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import gg.lightskies.utils.config.DataStore;
import gg.lightskies.utils.config.LSConfig;
import gg.lightskies.utils.config.Patterns;

/** Session mining stats: blocks, blocks/min, money and tokens per hour. */
public final class MiningTracker {
	private long sessionStart = System.currentTimeMillis();
	private long lastBreak;
	private long blocks;
	private double money;
	private double tokens;
	private final Map<String, Long> perBlock = new HashMap<>();
	/** Rolling window of break timestamps for an accurate "right now" rate. */
	private final long[] recent = new long[600];
	private int recentHead;
	private int recentSize;

	public void onBlockBroken(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		var counted = LSConfig.get().mining.countedBlockIds;
		if (!counted.isEmpty() && !counted.contains(id)) return;

		long now = System.currentTimeMillis();
		int idle = LSConfig.get().mining.autoResetAfterIdleMinutes;
		if (idle > 0 && lastBreak > 0 && now - lastBreak > idle * 60_000L) reset();
		if (blocks == 0) sessionStart = now;

		lastBreak = now;
		blocks++;
		DataStore.get().totalBlocksMined++;
		perBlock.merge(id, 1L, Long::sum);
		recent[recentHead] = now;
		recentHead = (recentHead + 1) % recent.length;
		recentSize = Math.min(recentSize + 1, recent.length);
	}

	public void onMessage(String text) {
		LSConfig.Mining cfg = LSConfig.get().mining;
		Matcher m = Patterns.firstMatch(cfg.moneyPatterns, text);
		if (m != null) {
			money += Patterns.number(Patterns.group(m, "amount"), 0) * Patterns.multiplier(Patterns.group(m, "multiplier"));
		}
		m = Patterns.firstMatch(cfg.tokenPatterns, text);
		if (m != null) {
			tokens += Patterns.number(Patterns.group(m, "amount"), 0) * Patterns.multiplier(Patterns.group(m, "multiplier"));
		}
	}

	public void reset() {
		sessionStart = System.currentTimeMillis();
		lastBreak = 0;
		blocks = 0;
		money = 0;
		tokens = 0;
		perBlock.clear();
		recentHead = 0;
		recentSize = 0;
	}

	/** Blocks per minute over the last 60 seconds. */
	public double currentBpm() {
		long cutoff = System.currentTimeMillis() - 60_000;
		int count = 0;
		for (int i = 0; i < recentSize; i++) {
			int idx = Math.floorMod(recentHead - 1 - i, recent.length);
			if (recent[idx] < cutoff) break;
			count++;
		}
		return count;
	}

	public double sessionBpm() {
		double minutes = elapsedMs() / 60_000.0;
		return minutes < 0.05 ? 0 : blocks / minutes;
	}

	public double perHour(double amount) {
		double hours = elapsedMs() / 3_600_000.0;
		return hours < 0.001 ? 0 : amount / hours;
	}

	public long elapsedMs() {
		return blocks == 0 && money == 0 ? 0 : System.currentTimeMillis() - sessionStart;
	}

	public long blocks() {
		return blocks;
	}

	public double money() {
		return money;
	}

	public double tokens() {
		return tokens;
	}

	public Map<String, Long> perBlock() {
		return perBlock;
	}
}
