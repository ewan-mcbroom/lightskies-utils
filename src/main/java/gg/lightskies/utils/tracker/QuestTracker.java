package gg.lightskies.utils.tracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import gg.lightskies.utils.config.LSConfig;
import gg.lightskies.utils.config.Patterns;

/** Tracks quest progress from chat, the action bar and (optionally) the sidebar. */
public final class QuestTracker {
	public static final class Quest {
		public final String name;
		public double current;
		public double max;
		public long updated = System.currentTimeMillis();
		public long completedAt;

		Quest(String name) {
			this.name = name;
		}

		public double fraction() {
			return max <= 0 ? 0 : Math.min(1, current / max);
		}
	}

	private static final long SHOW_COMPLETED_MS = 8_000;
	private final Map<String, Quest> quests = new LinkedHashMap<>();

	public void onMessage(String text) {
		LSConfig.Quests cfg = LSConfig.get().quests;

		Matcher m = Patterns.firstMatch(cfg.progressPatterns, text);
		if (m != null) {
			String name = Patterns.group(m, "name");
			if (name != null) {
				Quest q = quest(name);
				q.current = Patterns.number(Patterns.group(m, "current"), q.current);
				q.max = Patterns.number(Patterns.group(m, "max"), q.max);
				q.updated = System.currentTimeMillis();
				if (q.max > 0 && q.current >= q.max && q.completedAt == 0) q.completedAt = q.updated;
			}
			return;
		}

		m = Patterns.firstMatch(cfg.completePatterns, text);
		if (m != null) {
			String name = Patterns.group(m, "name");
			if (name != null) {
				Quest q = quest(name);
				if (q.max > 0) q.current = q.max;
				q.completedAt = System.currentTimeMillis();
			}
			return;
		}

		m = Patterns.firstMatch(cfg.startPatterns, text);
		if (m != null) {
			String name = Patterns.group(m, "name");
			if (name != null) {
				Quest q = quest(name);
				q.current = 0;
				q.max = Patterns.number(Patterns.group(m, "max"), q.max);
				q.completedAt = 0;
				q.updated = System.currentTimeMillis();
			}
		}
	}

	/** Manually add a quest (from /lsu quest add). */
	public void add(String name, double max) {
		Quest q = quest(name);
		q.max = max;
		q.completedAt = 0;
	}

	public boolean remove(String name) {
		return quests.remove(key(name)) != null;
	}

	public void clear() {
		quests.clear();
	}

	/** Active quests first (closest to done first), then recently completed ones. */
	public List<Quest> visible() {
		long now = System.currentTimeMillis();
		quests.values().removeIf(q -> q.completedAt > 0 && now - q.completedAt > SHOW_COMPLETED_MS);
		List<Quest> list = new ArrayList<>(quests.values());
		list.sort(Comparator.<Quest>comparingInt(q -> q.completedAt > 0 ? 1 : 0)
				.thenComparing(Comparator.comparingDouble(Quest::fraction).reversed()));
		return list;
	}

	private Quest quest(String name) {
		return quests.computeIfAbsent(key(name), k -> new Quest(name.trim()));
	}

	private static String key(String name) {
		return name.trim().toLowerCase();
	}
}
