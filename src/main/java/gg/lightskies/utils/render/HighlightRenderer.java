package gg.lightskies.utils.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import gg.lightskies.utils.LightSkiesUtils;
import gg.lightskies.utils.config.LSConfig;
import gg.lightskies.utils.scan.Locator;
import gg.lightskies.utils.util.Game;

/** Draws translucent boxes over relic and treasure targets. Same approach as Fabric API's own render tests. */
public final class HighlightRenderer {
	private HighlightRenderer() {
	}

	public static void render(LevelRenderContext context) {
		if (!LightSkiesUtils.highlightsOn() || !Game.active()) return;
		Minecraft mc = Game.mc();
		if (mc.player == null) return;

		LSConfig cfg = LSConfig.get();
		Locator locator = LightSkiesUtils.locator();
		List<Locator.Target> targets = locator.targets();
		if (targets.isEmpty()) return;

		Vec3 player = mc.player.position();
		targets.sort((a, b) -> Double.compare(a.pos().distToCenterSqr(player), b.pos().distToCenterSqr(player)));
		int limit = Math.min(targets.size(), Math.max(1, cfg.scan.maxHighlights));

		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);

		for (int i = 0; i < limit; i++) {
			Locator.Target t = targets.get(i);
			int colour = t.kind() == Locator.Kind.RELIC ? cfg.relics.color : cfg.treasure.color;
			AABB box = locator.boxFor(t);
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(),
					(pose, buffer) -> drawFilledBox(pose, buffer, box, colour));
		}

		poseStack.popPose();
	}

	private static void drawFilledBox(PoseStack.Pose pose, VertexConsumer vc, AABB b, int color) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		// front
		vc.addVertex(pose, x0, y0, z0).setColor(color);
		vc.addVertex(pose, x1, y0, z0).setColor(color);
		vc.addVertex(pose, x1, y1, z0).setColor(color);
		vc.addVertex(pose, x0, y1, z0).setColor(color);
		// back
		vc.addVertex(pose, x1, y0, z1).setColor(color);
		vc.addVertex(pose, x0, y0, z1).setColor(color);
		vc.addVertex(pose, x0, y1, z1).setColor(color);
		vc.addVertex(pose, x1, y1, z1).setColor(color);
		// left
		vc.addVertex(pose, x0, y0, z1).setColor(color);
		vc.addVertex(pose, x0, y0, z0).setColor(color);
		vc.addVertex(pose, x0, y1, z0).setColor(color);
		vc.addVertex(pose, x0, y1, z1).setColor(color);
		// right
		vc.addVertex(pose, x1, y0, z0).setColor(color);
		vc.addVertex(pose, x1, y0, z1).setColor(color);
		vc.addVertex(pose, x1, y1, z1).setColor(color);
		vc.addVertex(pose, x1, y1, z0).setColor(color);
		// top
		vc.addVertex(pose, x0, y1, z0).setColor(color);
		vc.addVertex(pose, x1, y1, z0).setColor(color);
		vc.addVertex(pose, x1, y1, z1).setColor(color);
		vc.addVertex(pose, x0, y1, z1).setColor(color);
		// bottom
		vc.addVertex(pose, x0, y0, z1).setColor(color);
		vc.addVertex(pose, x1, y0, z1).setColor(color);
		vc.addVertex(pose, x1, y0, z0).setColor(color);
		vc.addVertex(pose, x0, y0, z0).setColor(color);
	}
}
