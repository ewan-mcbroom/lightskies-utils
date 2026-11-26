package gg.lightskies.utils.tracker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import gg.lightskies.utils.config.DataStore;
import gg.lightskies.utils.config.LSConfig;
import gg.lightskies.utils.config.Patterns;
import gg.lightskies.utils.util.Game;

/**
 * Rare drops from two sources: server drop messages, and rare items appearing in your inventory
 * (by item id or name keyword). Inventory changes while a screen is open are ignored, so moving
 * items out of chests doesn't count as a drop.
 */
public final class DropTracker {
	public record Drop(String item, long amount, String source, long time) {
	}

	private final Map<String, Long> session = new LinkedHashMap<>();
	private final Deque<Drop> recent = new ArrayDeque<>();
	private Map<String, Long> lastInventory;
	/** Suppresses inventory-based drops right after a chat drop, so one drop isn't counted twice. */
	private long lastChatDrop;

	public void onMessage(String text) {
		Matcher m = Patterns.firstMatch(LSConfig.get().drops.chatPatterns, text);
		if (m == null) return;
		String item = Patterns.group(m, "item");
		if (item == null) return;
		long amount = (long) Patterns.number(Patterns.group(m, "amount"), 1);
		String rarity = Patterns.group(m, "rarity");
		record(item, amount, rarity == null ? "drop" : rarity.toLowerCase());
		lastChatDrop = System.currentTimeMillis();
	}

	/** Called every few ticks. */
	public void tickInventory() {
		Minecraft mc = Game.mc();
		if (mc.player == null) {
			lastInventory = null;
			return;
		}
		Map<String, Long> now = rareCounts(mc.player.getInventory());
		if (mc.gui.screen() != null || lastInventory == null) {
			lastInventory = now;
			return;
		}
		boolean recentChat = System.currentTimeMillis() - lastChatDrop < 1500;
		for (Map.Entry<String, Long> e : now.entrySet()) {
			long gained = e.getValue() - lastInventory.getOrDefault(e.getKey(), 0L);
			if (gained > 0 && !recentChat) record(e.getKey(), gained, "inventory");
		}
		lastInventory = now;
	}

	public void resetInventoryBaseline() {
		lastInventory = null;
	}

	private Map<String, Long> rareCounts(Inventory inv) {
		LSConfig.Drops cfg = LSConfig.get().drops;
		Map<String, Long> counts = new HashMap<>();
		if (cfg.rareItemIds.isEmpty() && cfg.rareNameKeywords.isEmpty()) return counts;

		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) continue;
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			String name = Patterns.strip(stack.getHoverName().getString());
			if (isRare(id, name, cfg)) counts.merge(name, (long) stack.getCount(), Long::sum);
		}
		return counts;
	}

	private static boolean isRare(String id, String name, LSConfig.Drops cfg) {
		if (cfg.rareItemIds.contains(id)) return true;
		String lower = name.toLowerCase();
		for (String kw : cfg.rareNameKeywords) {
			if (!kw.isBlank() && lower.contains(kw.toLowerCase())) return true;
		}
		return false;
	}

	private void record(String item, long amount, String source) {
		session.merge(item, amount, Long::sum);
		DataStore.get().allTimeDrops.merge(item, amount, Long::sum);
		recent.addFirst(new Drop(item, amount, source, System.currentTimeMillis()));
		while (recent.size() > 50) recent.removeLast();
		if (LSConfig.get().drops.alertSound) Game.ping(2.0f);
	}

	public Map<String, Long> session() {
		return session;
	}

	public List<Drop> recent() {
		return List.copyOf(recent);
	}

	public long sessionTotal() {
		long t = 0;
		for (long v : session.values()) t += v;
		return t;
	}

	public void reset() {
		session.clear();
		recent.clear();
	}
}
