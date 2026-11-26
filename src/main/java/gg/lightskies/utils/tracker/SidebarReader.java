package gg.lightskies.utils.tracker;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import gg.lightskies.utils.config.Patterns;
import gg.lightskies.utils.util.Game;

/** Reads the visible text of the sidebar scoreboard (team prefix + entry + suffix per line). */
public final class SidebarReader {
	private SidebarReader() {
	}

	public static List<String> lines() {
		Minecraft mc = Game.mc();
		List<String> out = new ArrayList<>();
		if (mc.level == null) return out;
		Scoreboard sb = mc.level.getScoreboard();
		Objective objective = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) return out;

		for (PlayerScoreEntry entry : sb.listPlayerScores(objective)) {
			if (entry.isHidden()) continue;
			PlayerTeam team = sb.getPlayersTeam(entry.owner());
			String text = PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString();
			out.add(Patterns.strip(text));
		}
		return out;
	}
}
