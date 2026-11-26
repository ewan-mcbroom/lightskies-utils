package gg.lightskies.utils.scan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import gg.lightskies.utils.config.LSConfig;
import gg.lightskies.utils.config.Patterns;
import gg.lightskies.utils.util.Fmt;
import gg.lightskies.utils.util.Game;

/**
 * Finds relic and treasure blocks/entities around the player. Blocks are scanned incrementally
 * (a slice of the cube each tick) so it never causes a lag spike; entities are re-checked every
 * 10 ticks. Only reads what the client already has loaded.
 */
public final class Locator {
	public enum Kind { RELIC, TREASURE }

	public record Target(Kind kind, String label, BlockPos pos, int entityId) {
		public boolean isEntity() {
			return entityId >= 0;
		}
	}

	/** Shown in the HUD: label, distance, arrow. */
	public record Sighting(Kind kind, String label, double distance, String arrow, int dy) {
	}

	// scan cycle state
	private BlockPos center;
	private int ix, iy, iz;
	private Map<BlockPos, Target> building = new HashMap<>();
	private Map<BlockPos, Target> blocks = new HashMap<>();
	private final List<Target> entities = new ArrayList<>();
	private final Set<BlockPos> seenBefore = new HashSet<>();
	private Map<Block, Kind> wanted = Map.of();
	private String wantedForDimension = "";

	// relic progress from chat
	private int relicsFound;
	private int relicsFoundCurrent = -1;
	private int relicsFoundMax = -1;
	private String lastRelicName;

	public void tick() {
		Minecraft mc = Game.mc();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) return;

		String dim = Game.dimensionId();
		if (!dim.equals(wantedForDimension)) {
			rebuildWanted(dim);
			clear();
		}
		if (wanted.isEmpty()) {
			blocks.clear();
			return;
		}

		LSConfig.Scan cfg = LSConfig.get().scan;
		int rh = Math.max(4, cfg.horizontalRadius);
		int rv = Math.max(4, cfg.verticalRadius);
		if (center == null) startCycle(mc.player.blockPosition(), rh, rv);

		int minY = level.getMinY();
		int maxY = minY + level.getHeight() - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int budget = Math.max(1000, cfg.blocksPerTick);

		while (budget-- > 0) {
			int y = center.getY() + iy;
			if (y >= minY && y <= maxY) {
				pos.set(center.getX() + ix, y, center.getZ() + iz);
				if (level.isLoaded(pos)) {
					BlockState state = level.getBlockState(pos);
					Kind kind = wanted.get(state.getBlock());
					if (kind != null) {
						BlockPos found = pos.immutable();
						building.put(found, new Target(kind, blockLabel(state), found, -1));
					}
				}
			}
			// advance x -> z -> y
			if (++ix > rh) {
				ix = -rh;
				if (++iz > rh) {
					iz = -rh;
					if (++iy > rv) {
						finishCycle();
						return;
					}
				}
			}
		}
	}

	public void tickEntities() {
		entities.clear();
		Minecraft mc = Game.mc();
		if (mc.level == null) return;
		LSConfig cfg = LSConfig.get();
		boolean relics = cfg.relics.enabled && !cfg.relics.entityNamePatterns.isEmpty();
		boolean treasure = cfg.treasure.enabled && !cfg.treasure.entityNamePatterns.isEmpty();
		if (!relics && !treasure) return;

		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || !e.hasCustomName() || e.getCustomName() == null) continue;
			String name = Patterns.strip(e.getCustomName().getString());
			Kind kind = null;
			if (relics && Patterns.anyMatch(cfg.relics.entityNamePatterns, name)) kind = Kind.RELIC;
			else if (treasure && Patterns.anyMatch(cfg.treasure.entityNamePatterns, name)) kind = Kind.TREASURE;
			if (kind == null) continue;
			BlockPos p = e.blockPosition();
			entities.add(new Target(kind, name, p, e.getId()));
			if (seenBefore.add(p)) ping(kind);
		}
	}

	public void onBlockBroken(BlockPos pos) {
		blocks.remove(pos);
		building.remove(pos);
	}

	public void onMessage(String text) {
		Matcher m = Patterns.firstMatch(LSConfig.get().relics.foundPatterns, text);
		if (m == null) return;
		relicsFound++;
		lastRelicName = Patterns.group(m, "name");
		relicsFoundCurrent = (int) Patterns.number(Patterns.group(m, "current"), relicsFoundCurrent);
		relicsFoundMax = (int) Patterns.number(Patterns.group(m, "max"), relicsFoundMax);
		Game.ping(1.2f);
	}

	/** All current targets (blocks + entities) for rendering. */
	public List<Target> targets() {
		List<Target> out = new ArrayList<>(blocks.size() + entities.size());
		out.addAll(blocks.values());
		out.addAll(entities);
		return out;
	}

	/** Nearest targets of a kind, for the HUD. */
	public List<Sighting> nearest(Kind kind, int limit) {
		Minecraft mc = Game.mc();
		if (mc.player == null) return List.of();
		double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
		float yaw = mc.player.getYRot();

		List<Sighting> out = new ArrayList<>();
		for (Target t : targets()) {
			if (t.kind() != kind) continue;
			double dx = t.pos().getX() + 0.5 - px;
			double dz = t.pos().getZ() + 0.5 - pz;
			int dy = (int) Math.round(t.pos().getY() - py);
			out.add(new Sighting(kind, t.label(), Math.sqrt(dx * dx + dz * dz + dy * dy), Fmt.arrow(dx, dz, yaw), dy));
		}
		out.sort((a, b) -> Double.compare(a.distance(), b.distance()));
		return out.size() > limit ? out.subList(0, limit) : out;
	}

	public int count(Kind kind) {
		int n = 0;
		for (Target t : targets()) if (t.kind() == kind) n++;
		return n;
	}

	public AABB boxFor(Target t) {
		Minecraft mc = Game.mc();
		if (t.isEntity() && mc.level != null) {
			Entity e = mc.level.getEntity(t.entityId());
			if (e != null) return e.getBoundingBox().inflate(0.05);
		}
		return new AABB(t.pos()).inflate(0.002);
	}

	public String relicProgress() {
		if (relicsFoundCurrent >= 0 && relicsFoundMax > 0) return relicsFoundCurrent + "/" + relicsFoundMax;
		return relicsFound == 0 ? null : relicsFound + " this session";
	}

	public String lastRelicName() {
		return lastRelicName;
	}

	/** Forget everything and rescan (dimension change, /lsu scan, config reload). */
	public void clear() {
		center = null;
		building = new HashMap<>();
		blocks = new HashMap<>();
		entities.clear();
		seenBefore.clear();
	}

	public void reloadConfig() {
		wantedForDimension = "";
	}

	public void resetRelicProgress() {
		relicsFound = 0;
		relicsFoundCurrent = -1;
		relicsFoundMax = -1;
		lastRelicName = null;
	}

	// ------------------------------------------------------------------------

	private void startCycle(BlockPos c, int rh, int rv) {
		center = c;
		ix = -rh;
		iz = -rh;
		iy = -rv;
		building = new HashMap<>();
	}

	private void finishCycle() {
		for (Target t : building.values()) {
			if (seenBefore.add(t.pos())) ping(t.kind());
		}
		blocks = building;
		center = null; // next tick restarts around the player's new position
	}

	private void ping(Kind kind) {
		LSConfig cfg = LSConfig.get();
		boolean on = kind == Kind.RELIC ? cfg.relics.pingSound : cfg.treasure.pingSound;
		if (on) Game.ping(kind == Kind.RELIC ? 0.8f : 1.4f);
	}

	private void rebuildWanted(String dim) {
		wantedForDimension = dim;
		LSConfig cfg = LSConfig.get();
		Map<Block, Kind> map = new HashMap<>();
		if (cfg.treasure.enabled) addBlocks(map, cfg.treasure.blockIds, Kind.TREASURE);
		if (cfg.relics.enabled) {
			List<String> any = cfg.relics.blockIdsByDimension.get("*");
			if (any != null) addBlocks(map, any, Kind.RELIC);
			List<String> here = cfg.relics.blockIdsByDimension.get(dim);
			if (here != null) addBlocks(map, here, Kind.RELIC);
		}
		wanted = map;
	}

	private static void addBlocks(Map<Block, Kind> map, List<String> ids, Kind kind) {
		for (String id : ids) {
			net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.tryParse(id);
			if (rl == null) continue;
			BuiltInRegistries.BLOCK.getOptional(rl).ifPresent(b -> map.put(b, kind));
		}
	}

	private static String blockLabel(BlockState state) {
		return state.getBlock().getName().getString();
	}
}
