package dev.harlik.wrenchdisplay.paper;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * The vanilla paste window. Paper's dialog API arrived in 1.21.7, so this class is only touched when
 * {@link #AVAILABLE} is true; on Paper 1.21.6 it is never loaded.
 */
final class PasteDialog {
	static final boolean AVAILABLE = classExists("io.papermc.paper.dialog.Dialog");
	private static final String INPUT_KEY = "paste";

	private PasteDialog() {
	}

	private static boolean classExists(String name) {
		try {
			Class.forName(name, false, PasteDialog.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	/** Opens the dialog; {@code onSubmit} runs on the player's thread with the pasted text. */
	static void open(WrenchDisplayPlugin plugin, Player player, int maxLength, Consumer<String> onSubmit) {
		Component title = Component.text(" ").font(Key.key(WrenchDisplayPlugin.NAMESPACE, "gui"))
				.append(PaperLang.tr("blueprint.dialog.title", "Blueprint"));
		DialogBase base = DialogBase.builder(title)
				.body(List.of(DialogBody.plainMessage(PaperLang.tr("blueprint.dialog.body",
						"Paste a /summon command for block, item or text displays. Several commands for one model can be pasted "
								+ "together.\nUp to %s characters here; bigger models need the Wrench Display client mod.", maxLength), 300)))
				.inputs(List.of(DialogInput.text(INPUT_KEY, PaperLang.tr("blueprint.dialog.input", "Command"))
						.width(300)
						.labelVisible(false)
						.maxLength(maxLength)
						.multiline(TextDialogInput.MultilineOptions.create(null, 160))
						.build()))
				.build();
		ActionButton save = ActionButton.builder(PaperLang.tr("blueprint.dialog.save", "Save"))
				.width(150)
				.action(DialogAction.customClick((response, audience) -> {
					if (audience instanceof Player p) {
						String text = response.getText(INPUT_KEY);
						p.getScheduler().run(plugin, t -> onSubmit.accept(text == null ? "" : text), null);
					}
				}, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(30)).build()))
				.build();
		ActionButton cancel = ActionButton.builder(Component.translatable("gui.cancel")).width(150).build();
		player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(DialogType.multiAction(List.of(save, cancel)).columns(2).build())));
	}
}
