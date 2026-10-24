package dev.harlik.wrenchdisplay.fabric.net;

import dev.harlik.wrenchdisplay.common.blueprint.PasteAssembler;
import dev.harlik.wrenchdisplay.fabric.Lang;
import dev.harlik.wrenchdisplay.fabric.WrenchDisplay;
import dev.harlik.wrenchdisplay.fabric.blueprint.BlueprintHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packets between the server and clients that have the mod. Vanilla clients never see these; the server only
 * sends them to clients that registered the channel. The wire format is shared with the Paper plugin.
 */
public final class BlueprintPayloads {
	/** Unfinished pastes per player. */
	private static final Map<UUID, PasteAssembler> ASSEMBLERS = new ConcurrentHashMap<>();

	private BlueprintPayloads() {
	}

	/** Server → client: open the textured paste screen instead of the vanilla dialog. */
	public record OpenPaste(int maxLength) implements CustomPacketPayload {
		public static final Type<OpenPaste> TYPE = new Type<>(WrenchDisplay.id("open_paste"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPaste> CODEC =
				ByteBufCodecs.VAR_INT.map(OpenPaste::new, OpenPaste::maxLength).cast();

		@Override
		public Type<OpenPaste> type() {
			return TYPE;
		}
	}

	/**
	 * Client → server: one piece of the pasted text. Long pastes are split by the client and joined with
	 * {@link PasteAssembler}; the Paper plugin reads the same bytes, so the client mod works on both.
	 */
	public record PasteChunk(int session, int index, int count, String text) implements CustomPacketPayload {
		public static final Type<PasteChunk> TYPE = new Type<>(WrenchDisplay.id("paste_chunk"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PasteChunk> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, PasteChunk::session,
				ByteBufCodecs.VAR_INT, PasteChunk::index,
				ByteBufCodecs.VAR_INT, PasteChunk::count,
				ByteBufCodecs.stringUtf8(PasteAssembler.CHUNK_CHARS), PasteChunk::text,
				PasteChunk::new);

		@Override
		public Type<PasteChunk> type() {
			return TYPE;
		}
	}

	public static void register() {
		//? if >=26.1 {
		PayloadTypeRegistry.clientboundPlay().register(OpenPaste.TYPE, OpenPaste.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PasteChunk.TYPE, PasteChunk.CODEC);
		//?} else {
		/*PayloadTypeRegistry.playS2C().register(OpenPaste.TYPE, OpenPaste.CODEC);
		PayloadTypeRegistry.playC2S().register(PasteChunk.TYPE, PasteChunk.CODEC);
		*///?}
		ServerPlayNetworking.registerGlobalReceiver(PasteChunk.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			int maxLength = WrenchDisplay.settings().blueprint.maxLength;
			PasteAssembler assembler = ASSEMBLERS.computeIfAbsent(player.getUUID(), id -> new PasteAssembler());
			switch (assembler.accept(payload.session(), payload.index(), payload.count(), payload.text(), maxLength)) {
				case COMPLETE -> BlueprintHandler.submitPaste(player, assembler.result(), maxLength);
				case REJECTED -> player.sendSystemMessage(Lang.tr("blueprint.paste_rejected",
						"The paste didn't arrive correctly (limit %s characters). Please try again.", maxLength).withStyle(ChatFormatting.RED));
				case PENDING -> {
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ASSEMBLERS.remove(handler.player.getUUID()));
	}

	/** True if this player has the client mod, so the textured screen can be used. */
	public static boolean hasClientMod(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, OpenPaste.TYPE);
	}

	public static void openPaste(ServerPlayer player) {
		ServerPlayNetworking.send(player, new OpenPaste(WrenchDisplay.settings().blueprint.maxLength));
	}
}
