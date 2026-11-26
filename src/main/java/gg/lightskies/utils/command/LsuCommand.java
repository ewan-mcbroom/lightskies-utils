package gg.lightskies.utils.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import gg.lightskies.utils.LightSkiesUtils;
import gg.lightskies.utils.config.DataStore;
import gg.lightskies.utils.config.LSConfig;
import gg.lightskies.utils.tracker.SidebarReader;
import gg.lightskies.utils.util.Fmt;

/**
 * /lsu hud | highlights | page | scan | reload | drops [all] | bosses | mining
 * /lsu reset <mining|quests|bosses|drops|relics|all>
 * /lsu quest add <max> <name> | quest remove <name> | quest clear
 * /lsu debug           - log every server message to latest.log (for writing regexes)
 * /lsu test <message>  - run a fake server message through every tracker
 * /lsu sidebar         - print the sidebar lines the mod can see
 */
public final class LsuCommand {
	private LsuCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("lsu")
				.executes(ctx -> help(ctx))
				.then(literal("hud").executes(ctx -> {
					LSConfig.get().hud.enabled = !LSConfig.get().hud.enabled;
					LSConfig.save();
					return ok(ctx, "HUD " + onOff(LSConfig.get().hud.enabled));
				}))
				.then(literal("highlights").executes(ctx -> ok(ctx, "Highlights " + onOff(LightSkiesUtils.toggleHighlights()))))
				.then(literal("page").executes(ctx -> ok(ctx, "HUD page: " + LightSkiesUtils.hud().cyclePage())))
				.then(literal("scan").executes(ctx -> {
					LightSkiesUtils.locator().clear();
					return ok(ctx, "Rescanning nearby blocks");
				}))
				.then(literal("reload").executes(ctx -> {
					LSConfig.load();
					LightSkiesUtils.locator().reloadConfig();
					return ok(ctx, "Config reloaded");
				}))
				.then(literal("mining").executes(LsuCommand::mining))
				.then(literal("bosses").executes(LsuCommand::bosses))
				.then(literal("drops")
						.executes(ctx -> drops(ctx, false))
						.then(literal("all").executes(ctx -> drops(ctx, true))))
				.then(literal("reset")
						.then(literal("mining").executes(ctx -> reset(ctx, "mining")))
						.then(literal("quests").executes(ctx -> reset(ctx, "quests")))
						.then(literal("bosses").executes(ctx -> reset(ctx, "bosses")))
						.then(literal("drops").executes(ctx -> reset(ctx, "drops")))
						.then(literal("relics").executes(ctx -> reset(ctx, "relics")))
						.then(literal("all").executes(ctx -> reset(ctx, "all"))))
				.then(literal("quest")
						.then(literal("add").then(argument("max", IntegerArgumentType.integer(1))
								.then(argument("name", StringArgumentType.greedyString()).executes(ctx -> {
									String name = StringArgumentType.getString(ctx, "name");
									LightSkiesUtils.quests().add(name, IntegerArgumentType.getInteger(ctx, "max"));
									return ok(ctx, "Tracking quest " + name);
								}))))
						.then(literal("remove").then(argument("name", StringArgumentType.greedyString()).executes(ctx -> {
							String name = StringArgumentType.getString(ctx, "name");
							return ok(ctx, LightSkiesUtils.quests().remove(name) ? "Removed " + name : "No quest named " + name);
						})))
						.then(literal("clear").executes(ctx -> reset(ctx, "quests"))))
				.then(literal("debug").executes(ctx -> ok(ctx, "Message logging " + onOff(LightSkiesUtils.toggleDebug())
						+ " §7(lines go to latest.log, prefixed [LSU msg])")))
				.then(literal("test").then(argument("message", StringArgumentType.greedyString()).executes(ctx -> {
					String msg = StringArgumentType.getString(ctx, "message");
					LightSkiesUtils.handleServerText(msg, "test");
					return ok(ctx, "Processed test message: §7" + msg);
				})))
				.then(literal("sidebar").executes(ctx -> {
					List<String> lines = SidebarReader.lines();
					if (lines.isEmpty()) return ok(ctx, "No sidebar visible");
					for (String l : lines) ctx.getSource().sendFeedback(Component.literal("§7| §f" + l));
					return 1;
				}))
		);
	}

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		String[] lines = {
				"§b§lLightSkies Utils",
				"§f/lsu hud|highlights|page §7- toggles",
				"§f/lsu mining|bosses|drops [all] §7- summaries",
				"§f/lsu quest add <max> <name>|remove <name>|clear",
				"§f/lsu reset <mining|quests|bosses|drops|relics|all>",
				"§f/lsu scan|reload §7- rescan / reload config",
				"§f/lsu debug|test <msg>|sidebar §7- dev tools for regexes"
		};
		for (String l : lines) ctx.getSource().sendFeedback(Component.literal(l));
		return 1;
	}

	private static int mining(CommandContext<FabricClientCommandSource> ctx) {
		var m = LightSkiesUtils.mining();
		ok(ctx, "Session: " + m.blocks() + " blocks in " + Fmt.duration(m.elapsedMs())
				+ ", $" + Fmt.compact(m.money()) + ", all-time " + Fmt.compact(DataStore.get().totalBlocksMined) + " blocks");
		m.perBlock().entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.limit(10)
				.forEach(e -> ctx.getSource().sendFeedback(Component.literal("§7 " + e.getKey() + ": §f" + e.getValue())));
		return 1;
	}

	private static int bosses(CommandContext<FabricClientCommandSource> ctx) {
		Map<String, List<String>> summary = LightSkiesUtils.bosses().summary();
		if (summary.isEmpty()) return ok(ctx, "No boss activity seen yet");
		for (Map.Entry<String, List<String>> e : summary.entrySet()) {
			ctx.getSource().sendFeedback(Component.literal("§e" + e.getKey()));
			for (String l : e.getValue()) ctx.getSource().sendFeedback(Component.literal("§7 " + l));
		}
		return 1;
	}

	private static int drops(CommandContext<FabricClientCommandSource> ctx, boolean allTime) {
		Map<String, Long> source = allTime ? DataStore.get().allTimeDrops : LightSkiesUtils.drops().session();
		if (source.isEmpty()) return ok(ctx, "No rare drops " + (allTime ? "recorded" : "this session"));
		long total = source.values().stream().mapToLong(Long::longValue).sum();
		ok(ctx, "Rare drops " + (allTime ? "(all time)" : "(this session)") + ": " + total);
		source.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
				.limit(20)
				.forEach(e -> ctx.getSource().sendFeedback(Component.literal("§d " + e.getValue() + "x §f" + e.getKey())));
		return 1;
	}

	private static int reset(CommandContext<FabricClientCommandSource> ctx, String what) {
		boolean all = what.equals("all");
		if (all || what.equals("mining")) LightSkiesUtils.mining().reset();
		if (all || what.equals("quests")) LightSkiesUtils.quests().clear();
		if (all || what.equals("bosses")) LightSkiesUtils.bosses().reset();
		if (all || what.equals("drops")) LightSkiesUtils.drops().reset();
		if (all || what.equals("relics")) LightSkiesUtils.locator().resetRelicProgress();
		DataStore.save();
		return ok(ctx, "Reset " + what);
	}

	private static int ok(CommandContext<FabricClientCommandSource> ctx, String msg) {
		ctx.getSource().sendFeedback(Component.literal("§b[LSU]§r " + msg));
		return 1;
	}

	private static String onOff(boolean on) {
		return on ? "§aon" : "§coff";
	}
}
