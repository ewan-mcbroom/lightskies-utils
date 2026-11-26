package gg.lightskies.utils.util;

import java.util.Locale;

public final class Fmt {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private Fmt() {
	}

	/** 1234 -> 1.2k, 5_300_000 -> 5.3m */
	public static String compact(double v) {
		double a = Math.abs(v);
		if (a >= 1e12) return trim(v / 1e12) + "t";
		if (a >= 1e9) return trim(v / 1e9) + "b";
		if (a >= 1e6) return trim(v / 1e6) + "m";
		if (a >= 1e3) return trim(v / 1e3) + "k";
		return trim(v);
	}

	private static String trim(double v) {
		String s = String.format(Locale.ROOT, "%.1f", v);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	/** Milliseconds -> "1h 4m", "3m 12s", "42s". */
	public static String duration(long ms) {
		long s = Math.max(0, ms / 1000);
		long h = s / 3600;
		long m = (s % 3600) / 60;
		long sec = s % 60;
		if (h > 0) return h + "h " + m + "m";
		if (m > 0) return m + "m " + sec + "s";
		return sec + "s";
	}

	public static String ago(long epochMs) {
		return duration(System.currentTimeMillis() - epochMs) + " ago";
	}

	/**
	 * Arrow pointing from the player's view direction towards a target.
	 * Minecraft yaw: 0 = south (+z), 90 = west (-x); yaw grows when turning right.
	 */
	public static String arrow(double dx, double dz, float playerYaw) {
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = wrap(targetYaw - playerYaw);
		int idx = (int) Math.round(rel / 45.0);
		return ARROWS[Math.floorMod(idx, 8)];
	}

	private static double wrap(double deg) {
		deg %= 360;
		if (deg >= 180) deg -= 360;
		if (deg < -180) deg += 360;
		return deg;
	}

	public static String titleCase(String s) {
		StringBuilder out = new StringBuilder(s.length());
		boolean up = true;
		for (char c : s.toCharArray()) {
			if (c == '_' || c == ' ') {
				out.append(' ');
				up = true;
			} else {
				out.append(up ? Character.toUpperCase(c) : c);
				up = false;
			}
		}
		return out.toString();
	}
}
