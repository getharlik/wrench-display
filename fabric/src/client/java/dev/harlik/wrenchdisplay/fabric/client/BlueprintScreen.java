package dev.harlik.wrenchdisplay.fabric.client;

import dev.harlik.wrenchdisplay.common.blueprint.PasteAssembler;
import dev.harlik.wrenchdisplay.fabric.WrenchDisplay;
import dev.harlik.wrenchdisplay.fabric.net.BlueprintPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;

import java.util.Random;

/**
 * The blueprint paste screen for players with the mod: no vanilla dialog length limit. The pasted text is sent
 * in pieces and joined by the server (works on Fabric and Paper servers). Layout follows the blueprint_screen
 * texture: title strip, text area and a footer with the buttons.
 */
public class BlueprintScreen extends Screen {
	private static final Identifier TEXTURE = WrenchDisplay.id("textures/gui/blueprint_screen.png");
	private static final int TEXTURE_SIZE = 256;
	private static final int PANEL_WIDTH = 240;
	private static final int PANEL_HEIGHT = 180;
	private static final int TEXT_X = 12;
	private static final int TEXT_Y = 24;
	private static final int TEXT_WIDTH = 216;
	private static final int TEXT_HEIGHT = 122;
	private static final int TITLE_Y = 10;
	private static final int BUTTON_Y = 152;
	private static final int BUTTON_WIDTH = 98;
	private static final int TITLE_COLOR = 0xFFEEF6FF;
	private static final int COUNTER_COLOR = 0xFF9CC2EC;
	private static final int COUNTER_FULL_COLOR = 0xFFFF6060;

	private static final Random RANDOM = new Random();

	private final int maxLength;
	private MultiLineEditBox text;
	private String draft = "";
	private int left;
	private int top;

	public BlueprintScreen(int maxLength) {
		super(Component.translatableWithFallback("screen.wrench-display.blueprint", "Blueprint"));
		this.maxLength = maxLength;
	}

	@Override
	protected void init() {
		left = (width - PANEL_WIDTH) / 2;
		top = (height - PANEL_HEIGHT) / 2;
		text = MultiLineEditBox.builder()
				.setX(left + TEXT_X)
				.setY(top + TEXT_Y)
				.setPlaceholder(Component.translatableWithFallback("screen.wrench-display.blueprint.placeholder",
						"Paste a /summon command for block, item or text displays…"))
				.setShowBackground(false)
				// The screen draws its own character count in the header.
				.setShowDecorations(false)
				.build(font, TEXT_WIDTH, TEXT_HEIGHT, title);
		text.setCharacterLimit(maxLength);
		// Keep what was pasted when the window is resized (init runs again).
		text.setValue(draft);
		text.setValueListener(value -> draft = value);
		addRenderableWidget(text);
		setInitialFocus(text);

		addRenderableWidget(Button.builder(Component.translatableWithFallback("screen.wrench-display.blueprint.save", "Save"), b -> save())
				.bounds(left + TEXT_X, top + BUTTON_Y, BUTTON_WIDTH, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
				.bounds(left + TEXT_X + TEXT_WIDTH - BUTTON_WIDTH, top + BUTTON_Y, BUTTON_WIDTH, 20).build());
	}

	private void save() {
		String value = text.getValue().trim();
		if (!value.isEmpty()) {
			// Split into pieces under the packet size limit; the server joins them again.
			String[] pieces = PasteAssembler.split(value);
			int session = RANDOM.nextInt();
			for (int i = 0; i < pieces.length; i++) {
				ClientPlayNetworking.send(new BlueprintPayloads.PasteChunk(session, i, pieces.length, pieces[i]));
			}
		}
		onClose();
	}

	//? if >=26.1 {
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, left, top, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(font, title, left + TEXT_X + 4, top + TITLE_Y, TITLE_COLOR, false);
		graphics.text(font, counter(), left + TEXT_X + TEXT_WIDTH - 4 - font.width(counter()), top + TITLE_Y, counterColor(), false);
	}
	//?} else {
	/*@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, left, top, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawString(font, title, left + TEXT_X + 4, top + TITLE_Y, TITLE_COLOR, false);
		graphics.drawString(font, counter(), left + TEXT_X + TEXT_WIDTH - 4 - font.width(counter()), top + TITLE_Y, counterColor(), false);
	}
	*///?}

	private String counter() {
		return draft.length() + " / " + maxLength;
	}

	private int counterColor() {
		return draft.length() >= maxLength ? COUNTER_FULL_COLOR : COUNTER_COLOR;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
