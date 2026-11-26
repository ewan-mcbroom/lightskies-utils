package gg.lightskies.utils.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import gg.lightskies.utils.config.LSConfig;

/** Small wrappers around vanilla client state, so the rest of the mod doesn't repeat null checks. */
public final class Game {
	private static long lastPing;

	private Game() {
	}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}

	/** True when in a world and (if configured) connected to LightSkies. */
	public static boolean active() {
		Minecraft mc = mc();
		if (mc.player == null || mc.level == null) return false;
		LSConfig cfg = LSConfig.get();
		if (!cfg.onlyOnLightSkies) return true;
		ServerData server = mc.getCurrentServer();
		if (server == null) return false; // singleplayer
		String ip = server.ip.toLowerCase();
		for (String part : cfg.serverAddressContains) {
			if (ip.contains(part.toLowerCase())) return true;
		}
		return false;
	}

	public static String dimensionId() {
		Minecraft mc = mc();
		return mc.level == null ? "unknown" : mc.level.dimension().identifier().toString();
	}

	public static String dimensionName() {
		return dimensionName(dimensionId());
	}

	public static String dimensionName(String id) {
		String friendly = LSConfig.get().bosses.dimensionNames.get(id);
		if (friendly != null) return friendly;
		int colon = id.indexOf(':');
		return Fmt.titleCase(colon >= 0 ? id.substring(colon + 1) : id);
	}

	public static void say(String message) {
		Minecraft mc = mc();
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("§b[LSU]§r " + message));
		}
	}

	/** Plays a short UI ping, at most once a second. */
	public static void ping(float pitch) {
		long now = System.currentTimeMillis();
		if (now - lastPing < 1000) return;
		lastPing = now;
		mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch, 0.6F));
	}
}
