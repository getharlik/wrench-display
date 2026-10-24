package dev.harlik.wrenchdisplay.fabric.test;

import dev.harlik.wrenchdisplay.common.DisplayMeta;
import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintParser;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintValidator;
import dev.harlik.wrenchdisplay.common.blueprint.PastePackets;
import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import dev.harlik.wrenchdisplay.common.text.Message;
import dev.harlik.wrenchdisplay.fabric.DisplayStore;
import dev.harlik.wrenchdisplay.fabric.ModItems;
import dev.harlik.wrenchdisplay.fabric.WrenchDisplay;
import dev.harlik.wrenchdisplay.fabric.blueprint.BlueprintHandler;
import dev.harlik.wrenchdisplay.fabric.blueprint.FabricBlockInfo;
import dev.harlik.wrenchdisplay.fabric.blueprint.PreparedBlueprint;
import dev.harlik.wrenchdisplay.fabric.net.BlueprintPayloads;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.entity.FakePlayer;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class WrenchDisplayGameTests {
	private static final List<String> FIXTURES = List.of("model2.txt", "model3_part2.txt");

	/** Assertions with plain-text messages; before 1.21.11 GameTestHelper only takes text components. */
	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	private static <N> void same(GameTestHelper helper, N actual, N expected, String message) {
		helper.assertValueEqual(actual, expected, Component.literal(message));
	}

	private static String fixture(String name) {
		try (InputStream in = WrenchDisplayGameTests.class.getResourceAsStream("/models/" + name)) {
			if (in == null) throw new IllegalStateException("missing fixture " + name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static List<String> english(List<Message> messages) {
		return messages.stream().map(Message::toFallbackString).toList();
	}

	/** Every fixture becomes real block states, items and text, and fits the survival rules (with text allowed). */
	@GameTest
	public void fixturesBuildRealEntities(GameTestHelper helper) {
		PermissionView textOnly = node -> Permissions.grantedByDefault(node) || node.equals(Permissions.TEXT);
		for (String name : FIXTURES) {
			BlueprintParser.Result result = BlueprintParser.parse(fixture(name), BlueprintParser.Limits.UNLIMITED);
			check(helper, result.isOk(), name + " does not parse: " + (result.isOk() ? "" : result.error().toFallbackString()));
			PreparedBlueprint prepared = PreparedBlueprint.prepare(result.model(), helper.getLevel().registryAccess());
			same(helper, english(prepared.problems()), List.of(), name + " build problems");
			same(helper, prepared.parts().size(), result.model().parts().size(), name + " part count");

			List<String> rules = english(BlueprintValidator.validate(result.model(), WrenchDisplay.settings(), textOnly, FabricBlockInfo.INSTANCE));
			same(helper, rules, List.of(), name + " survival rule problems");

			List<Display> displays = prepared.create(helper.getLevel(), 0, 0, 0, 0);
			same(helper, displays.size(), prepared.parts().size(), name + " entities");
			for (PreparedBlueprint.Part part : prepared.parts()) {
				if (part.kind() == DisplayKind.ITEM && part.item().is(Items.PLAYER_HEAD)) {
					check(helper, part.item().has(DataComponents.PROFILE), name + ": head lost its skin");
				}
			}
		}
		helper.succeed();
	}

	/** The tools are a stick and a paper underneath; no recipe or trade may treat them as such. */
	@GameTest
	public void toolsAreNotSticksOrPaper(GameTestHelper helper) {
		check(helper, Ingredient.of(Items.STICK).test(new ItemStack(Items.STICK)), "plain sticks still craft");
		check(helper, !Ingredient.of(Items.STICK).test(ModItems.wrench()), "wrench must not count as a stick");
		check(helper, !Ingredient.of(Items.PAPER).test(ModItems.blueprint()), "blueprint must not count as paper");
		check(helper, new ItemCost(Items.PAPER).test(new ItemStack(Items.PAPER)), "villagers still buy paper");
		check(helper, !new ItemCost(Items.PAPER).test(ModItems.blueprint()), "villagers must not take blueprints");
		//? if >=26.3 {
		check(helper, !ModItems.wrench().has(DataComponents.COOKING_FUEL), "wrench must not burn");
		//?} else {
		/*check(helper, !helper.getLevel().fuelValues().isFuel(ModItems.wrench()), "wrench must not burn");
		check(helper, helper.getLevel().fuelValues().isFuel(new ItemStack(Items.STICK)), "plain sticks still burn");
		*///?}
		same(helper, ModItems.wrench().getMaxStackSize(), 1, "wrench stack size");
		helper.succeed();
	}

	/** Paste → place in survival: custom heads are paid for by skin, and all parts share one group. */
	@GameTest
	public void survivalPasteAndPlace(GameTestHelper helper) {
		FakePlayer player = player(helper, "survival-tester");
		player.getInventory().clearContent();
		ItemStack blueprint = ModItems.blueprint();
		player.setItemInHand(InteractionHand.MAIN_HAND, blueprint);

		String text = fixture("model3_part2.txt");
		BlueprintHandler.submitPaste(player, text, text.length() + 1);
		UUID id = ModItems.blueprintId(player.getMainHandItem());
		check(helper, id != null, "blueprint was not filled");

		// Pay with the exact heads the model uses.
		PreparedBlueprint prepared = PreparedBlueprint.prepare(
				BlueprintParser.parse(text, BlueprintParser.Limits.UNLIMITED).model(), helper.getLevel().registryAccess());
		for (PreparedBlueprint.Part part : prepared.parts()) {
			player.getInventory().add(part.item().copy());
		}
		same(helper, player.getInventory().countItem(Items.PLAYER_HEAD), 2, "heads given");

		BlockPos floor = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
		BlueprintHandler.place(player, player.getMainHandItem(), id, hit, PermissionView.DEFAULTS);

		List<Display> placed = helper.getLevel().getEntitiesOfClass(Display.class, new AABB(floor).inflate(3),
				d -> DisplayStore.get(d) != null);
		same(helper, placed.size(), 2, "placed parts");
		DisplayMeta first = DisplayStore.get(placed.get(0));
		check(helper, first.groupId() != null && first.groupId().equals(DisplayStore.get(placed.get(1)).groupId()), "parts share a group");
		check(helper, first.refund() != null, "refund recorded");
		same(helper, player.getInventory().countItem(Items.PLAYER_HEAD), 0, "heads consumed");
		check(helper, ModItems.isBlueprint(player.getMainHandItem()), "blueprint is kept");
		placed.forEach(Display::discard);
		helper.succeed();
	}

	/** A fresh fake player per test, so per-player state (like the right-click debounce) doesn't leak between tests. */
	private static FakePlayer player(GameTestHelper helper, String name) {
		return FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
	}

	private static BlockHitResult clickTopOf(GameTestHelper helper, BlockPos relative) {
		BlockPos floor = helper.absolutePos(relative);
		helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		return new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
	}

	/**
	 * Right-click a block with the wrench, an item in the off hand: one display is placed and one item taken,
	 * even though the client sends block use, item use and the off-hand use for the same click.
	 */
	@GameTest
	public void wrenchRightClickPlacesOffhandItem(GameTestHelper helper) {
		FakePlayer player = player(helper, "wrench-tester");
		player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.wrench());
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_STAIRS, 2));
		BlockHitResult hit = clickTopOf(helper, new BlockPos(1, 1, 1));

		InteractionResult main = UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
		UseItemCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND);
		InteractionResult off = UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.OFF_HAND, hit);

		check(helper, main.consumesAction(), "main-hand click is handled");
		same(helper, off, InteractionResult.FAIL, "off hand must not place its block");
		List<Display.BlockDisplay> placed = helper.getLevel().getEntitiesOfClass(Display.BlockDisplay.class,
				new AABB(hit.getBlockPos().above()).inflate(0.5), d -> DisplayStore.get(d) != null);
		same(helper, placed.size(), 1, "displays placed by one click");
		check(helper, placed.get(0).getBlockState().is(Blocks.OAK_STAIRS), "placed the off-hand block");
		same(helper, player.getOffhandItem().getCount(), 1, "one item taken in survival");
		placed.forEach(Display::discard);
		helper.succeed();
	}

	/** A non-block off-hand item becomes an item display. */
	@GameTest
	public void wrenchRightClickPlacesItemDisplay(GameTestHelper helper) {
		FakePlayer player = player(helper, "item-tester");
		player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.wrench());
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND));
		BlockHitResult hit = clickTopOf(helper, new BlockPos(1, 1, 1));
		UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
		List<Display.ItemDisplay> placed = helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class,
				new AABB(hit.getBlockPos().above()).inflate(0.5), d -> DisplayStore.get(d) != null);
		same(helper, placed.size(), 1, "item displays placed");
		check(helper, placed.get(0).getItemStack().is(Items.DIAMOND), "placed the off-hand item");
		check(helper, player.getOffhandItem().isEmpty(), "item taken");
		placed.forEach(Display::discard);
		helper.succeed();
	}

	/** Right-click a block with a filled blueprint (creative: no cost) places the model through the real input path. */
	@GameTest
	public void blueprintRightClickPlaces(GameTestHelper helper) {
		FakePlayer player = player(helper, "blueprint-tester");
		player.setGameMode(GameType.CREATIVE);
		player.setItemInHand(InteractionHand.MAIN_HAND, ModItems.blueprint());
		String text = fixture("model3_part2.txt");
		BlueprintHandler.submitPaste(player, text, text.length() + 1);
		check(helper, ModItems.blueprintId(player.getMainHandItem()) != null, "blueprint was filled");

		BlockHitResult hit = clickTopOf(helper, new BlockPos(1, 1, 1));
		UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
		List<Display> placed = helper.getLevel().getEntitiesOfClass(Display.class, new AABB(hit.getBlockPos()).inflate(3),
				d -> DisplayStore.get(d) != null);
		same(helper, placed.size(), 2, "parts placed by right-click");
		placed.forEach(Display::discard);
		helper.succeed();
	}

	/**
	 * The Paper plugin reads and writes the client-mod packets by hand ({@link PastePackets}); its bytes must match
	 * the Fabric codecs exactly, or the client mod wouldn't work on Paper servers.
	 */
	@GameTest
	public void pastePacketsMatchFabricCodecs(GameTestHelper helper) {
		String text = "summon block_display ~ ~ ~ {text:'ünïcödé ●'}".repeat(50);
		BlueprintPayloads.PasteChunk chunk = new BlueprintPayloads.PasteChunk(-123456, 2, 5, text);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		BlueprintPayloads.PasteChunk.CODEC.encode(buf, chunk);
		byte[] fabric = new byte[buf.readableBytes()];
		buf.readBytes(fabric);
		byte[] common = PastePackets.encodeChunk(new PastePackets.Chunk(-123456, 2, 5, text));
		check(helper, Arrays.equals(fabric, common), "chunk bytes differ");
		same(helper, PastePackets.decodeChunk(fabric), new PastePackets.Chunk(-123456, 2, 5, text), "decoded chunk");

		RegistryFriendlyByteBuf open = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(PastePackets.encodeOpenPaste(131072)),
				helper.getLevel().registryAccess());
		same(helper, BlueprintPayloads.OpenPaste.CODEC.decode(open).maxLength(), 131072, "open_paste decoded by Fabric");
		same(helper, BlueprintPayloads.OpenPaste.TYPE.id().toString(), PastePackets.OPEN_PASTE, "open_paste channel");
		same(helper, BlueprintPayloads.PasteChunk.TYPE.id().toString(), PastePackets.PASTE_CHUNK, "paste_chunk channel");
		helper.succeed();
	}

	private static ItemStack craft(GameTestHelper helper, ItemStack... grid) {
		CraftingInput input = CraftingInput.of(3, 3, Arrays.asList(grid));
		return helper.getLevel().getServer().getRecipeManager()
				.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				//? if >=26.1 {
				.map(holder -> holder.value().assemble(input))
				//?} else {
				/*.map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess()))
				*///?}
				.orElse(ItemStack.EMPTY);
	}

	/** Both recipes load and craft working tools; a wrench can't stand in for the stick. */
	@GameTest
	public void recipesCraftTheTools(GameTestHelper helper) {
		ItemStack e = ItemStack.EMPTY;
		ItemStack wrench = craft(helper,
				e, e, new ItemStack(Items.NETHERITE_INGOT),
				e, new ItemStack(Items.IRON_INGOT), e,
				new ItemStack(Items.STICK), e, e);
		check(helper, ModItems.isWrench(wrench), "wrench recipe gives a wrench: " + wrench);

		ItemStack blueprint = craft(helper,
				e, new ItemStack(Items.LAPIS_LAZULI), e,
				new ItemStack(Items.FEATHER), new ItemStack(Items.PAPER), new ItemStack(Items.INK_SAC),
				e, new ItemStack(Items.DIAMOND_BLOCK), e);
		check(helper, ModItems.isBlueprint(blueprint), "blueprint recipe gives a blueprint: " + blueprint);
		check(helper, ModItems.blueprintId(blueprint) == null, "crafted blueprints are blank");

		ItemStack noStick = craft(helper,
				e, e, new ItemStack(Items.NETHERITE_INGOT),
				e, new ItemStack(Items.IRON_INGOT), e,
				ModItems.wrench(), e, e);
		check(helper, noStick.isEmpty(), "a wrench must not count as the stick");
		helper.succeed();
	}
}
