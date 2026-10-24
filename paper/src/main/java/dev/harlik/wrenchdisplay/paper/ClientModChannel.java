package dev.harlik.wrenchdisplay.paper;

import dev.harlik.wrenchdisplay.common.blueprint.PasteAssembler;
import dev.harlik.wrenchdisplay.common.blueprint.PastePackets;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Talks to the Fabric client mod: tells it to open the textured paste screen and joins the pasted text it sends
 * back in pieces. Same channels and bytes as the Fabric mod's payloads, see {@link PastePackets}.
 */
final class ClientModChannel implements PluginMessageListener, Listener {
	static final String OPEN_PASTE = PastePackets.OPEN_PASTE;
	static final String PASTE_CHUNK = PastePackets.PASTE_CHUNK;

	private final WrenchDisplayPlugin plugin;
	private final BlueprintListener blueprints;
	private final Map<UUID, PasteAssembler> assemblers = new ConcurrentHashMap<>();

	ClientModChannel(WrenchDisplayPlugin plugin, BlueprintListener blueprints) {
		this.plugin = plugin;
		this.blueprints = blueprints;
	}

	void register() {
		plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, OPEN_PASTE);
		plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, PASTE_CHUNK, this);
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	/** The client mod announces its channels on join. */
	static boolean hasClientMod(Player player) {
		return player.getListeningPluginChannels().contains(OPEN_PASTE);
	}

	void openPaste(Player player, int maxLength) {
		player.sendPluginMessage(plugin, OPEN_PASTE, PastePackets.encodeOpenPaste(maxLength));
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!PASTE_CHUNK.equals(channel)) return;
		PastePackets.Chunk chunk = PastePackets.decodeChunk(message);
		if (chunk == null) return;
		player.getScheduler().run(plugin, task -> accept(player, chunk.session(), chunk.index(), chunk.count(), chunk.text()), null);
	}

	private void accept(Player player, int session, int index, int count, String text) {
		int maxLength = plugin.settings().blueprint.maxLength;
		PasteAssembler assembler = assemblers.computeIfAbsent(player.getUniqueId(), id -> new PasteAssembler());
		switch (assembler.accept(session, index, count, text, maxLength)) {
			case COMPLETE -> blueprints.submitPaste(player, assembler.result(), maxLength);
			case REJECTED -> player.sendMessage(PaperLang.tr("blueprint.paste_rejected",
					"The paste didn't arrive correctly (limit %s characters). Please try again.", maxLength).color(NamedTextColor.RED));
			case PENDING -> {
			}
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		assemblers.remove(event.getPlayer().getUniqueId());
	}
}
