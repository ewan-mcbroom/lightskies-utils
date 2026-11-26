package gg.lightskies.utils.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


import gg.lightskies.utils.LightSkiesUtils;

/** Compiles and caches the regexes from the config. Bad regexes are logged once and skipped. */
public final class Patterns {
	private static final Map<String, Pattern> CACHE = new ConcurrentHashMap<>();
	private static final Pattern COLOUR_CODES = Pattern.compile("(?i)§[0-9A-FK-ORX]");

	private Patterns() {
	}

	public static void clearCache() {
		CACHE.clear();
	}

	public static String strip(String text) {
		return COLOUR_CODES.matcher(text).replaceAll("").trim();
	}

	public static List<Pattern> compile(List<String> regexes) {
		List<Pattern> out = new ArrayList<>(regexes.size());
		for (String regex : regexes) {
			Pattern p = CACHE.computeIfAbsent(regex, Patterns::tryCompile);
			if (p != NEVER) out.add(p);
		}
		return out;
	}

	/** Returns the first matching matcher (after find()), or null. */
	public static Matcher firstMatch(List<String> regexes, String text) {
		for (Pattern p : compile(regexes)) {
			Matcher m = p.matcher(text);
			if (m.find()) return m;
		}
		return null;
	}

	public static boolean anyMatch(List<String> regexes, String text) {
		return firstMatch(regexes, text) != null;
	}

	/** Named group value or null when the group is absent from the pattern or didn't participate. */
	public static String group(Matcher m, String name) {
		try {
			String v = m.group(name);
			return v == null || v.isBlank() ? null : v.trim();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static double number(String raw, double fallback) {
		if (raw == null) return fallback;
		try {
			return Double.parseDouble(raw.replace(",", ""));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public static double multiplier(String suffix) {
		if (suffix == null) return 1;
		return switch (suffix.toLowerCase()) {
			case "k" -> 1e3;
			case "m" -> 1e6;
			case "b" -> 1e9;
			case "t" -> 1e12;
			default -> 1;
		};
	}

	private static final Pattern NEVER = Pattern.compile("(?!)");

	private static Pattern tryCompile(String regex) {
		try {
			return Pattern.compile(regex);
		} catch (PatternSyntaxException e) {
			LightSkiesUtils.LOGGER.warn("Ignoring invalid regex in lightskies_utils.json: {} ({})", regex, e.getDescription());
			return NEVER;
		}
	}
}
