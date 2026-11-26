package gg.lightskies.utils.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;

import gg.lightskies.utils.LightSkiesUtils;

/**
 * Feeds title and subtitle text (boss spawns, quest completions...) into the trackers.
 *
 * Fabric API only added title events in 26.3, so this hooks vanilla directly to work on 26.2 too.
 * TAIL is used on purpose: these handlers first bounce from the network thread to the main thread,
 * and the network-thread call exits early, so TAIL runs exactly once, on the main thread.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "setTitleText", at = @At("TAIL"), require = 0)
	private void lightskiesUtils$onTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
		LightSkiesUtils.handleServerText(packet.text(), "title");
	}

	@Inject(method = "setSubtitleText", at = @At("TAIL"), require = 0)
	private void lightskiesUtils$onSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
		LightSkiesUtils.handleServerText(packet.text(), "subtitle");
	}
}
