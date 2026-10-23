package dev.harlik.wrenchdisplay.fabric.test;

import dev.harlik.wrenchdisplay.common.DisplayMeta;
import dev.harlik.wrenchdisplay.common.PermissionView;
import dev.harlik.wrenchdisplay.common.Permissions;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintParser;
import dev.harlik.wrenchdisplay.common.blueprint.BlueprintValidator;
import dev.harlik.wrenchdisplay.common.edit.DisplayKind;
import dev.harlik.wrenchdisplay.fabric.DisplayStore;
import dev.harlik.wrenchdisplay.fabric.ModItems;
import dev.harlik.wrenchdisplay.fabric.WrenchDisplay;
import dev.harlik.wrenchdisplay.fabric.blueprint.BlueprintHandler;
import dev.harlik.wrenchdisplay.fabric.blueprint.FabricBlockInfo;
import dev.harlik.wrenchdisplay.fabric.blueprint.PreparedBlueprint;
import net.fabricmc.fabric.api.entity.FakePlayer;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
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
import java.util.List;
import java.util.UUID;

public class WrenchDisplayGameTests {
	private static final List<String> FIXTURES = List.of("model2.txt", "model3_part2.txt");

	private static String fixture(String name) {
		try (InputStream in = WrenchDisplayGameTests.class.getResourceAsStream("/bdengine/" + name)) {
			if (in == null) throw new IllegalStateException("missing fixture " + name);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Every fixture becomes real block states, items and text, and fits the survival rules (with text allowed). */
	@GameTest
	public void fixturesBuildRealEntities(GameTestHelper helper) {
		PermissionView textOnly = node -> Permissions.grantedByDefault(node) || node.equals(Permissions.TEXT);
		for (String name : FIXTURES) {
			BlueprintParser.Result result = BlueprintParser.parse(fixture(name), BlueprintParser.Limits.UNLIMITED);
			helper.assertTrue(result.isOk(), name + " does not parse: " + result.error());
			PreparedBlueprint prepared = PreparedBlueprint.prepare(result.model(), helper.getLevel().registryAccess());
			helper.assertValueEqual(prepared.problems(), List.of(), name + " build problems");
			helper.assertValueEqual(prepared.parts().size(), result.model().parts().size(), name + " part count");

			List<String> rules = BlueprintValidator.validate(result.model(), WrenchDisplay.settings(), textOnly, FabricBlockInfo.INSTANCE);
			helper.assertValueEqual(rules, List.of(), name + " survival rule problems");

			List<Display> displays = prepared.create(helper.getLevel(), 0, 0, 0, 0);
			helper.assertValueEqual(displays.size(), prepared.parts().size(), name + " entities");
			for (PreparedBlueprint.Part part : prepared.parts()) {
				if (part.kind() == DisplayKind.ITEM && part.item().is(Items.PLAYER_HEAD)) {
					helper.assertTrue(part.item().has(DataComponents.PROFILE), name + ": head lost its skin");
				}
			}
		}
		helper.succeed();
	}

	/** The tools are a stick and a paper underneath; no recipe or trade may treat them as such. */
	@GameTest
	public void toolsAreNotSticksOrPaper(GameTestHelper helper) {
		helper.assertTrue(Ingredient.of(Items.STICK).test(new ItemStack(Items.STICK)), "plain sticks still craft");
		helper.assertTrue(!Ingredient.of(Items.STICK).test(ModItems.wrench()), "wrench must not count as a stick");
		helper.assertTrue(!Ingredient.of(Items.PAPER).test(ModItems.blueprint()), "blueprint must not count as paper");
		helper.assertTrue(new ItemCost(Items.PAPER).test(new ItemStack(Items.PAPER)), "villagers still buy paper");
		helper.assertTrue(!new ItemCost(Items.PAPER).test(ModItems.blueprint()), "villagers must not take blueprints");
		helper.assertTrue(!ModItems.wrench().has(DataComponents.COOKING_FUEL), "wrench must not burn");
		helper.assertValueEqual(ModItems.wrench().getMaxStackSize(), 1, "wrench stack size");
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
		helper.assertTrue(id != null, "blueprint was not filled");

		// Pay with the exact heads the model uses.
		PreparedBlueprint prepared = PreparedBlueprint.prepare(
				BlueprintParser.parse(text, BlueprintParser.Limits.UNLIMITED).model(), helper.getLevel().registryAccess());
		for (PreparedBlueprint.Part part : prepared.parts()) {
			player.getInventory().add(part.item().copy());
		}
		helper.assertValueEqual(player.getInventory().countItem(Items.PLAYER_HEAD), 2, "heads given");

		BlockPos floor = helper.absolutePos(new BlockPos(1, 1, 1));
		helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
		BlueprintHandler.place(player, player.getMainHandItem(), id, hit, PermissionView.DEFAULTS);

		List<Display> placed = helper.getLevel().getEntitiesOfClass(Display.class, new AABB(floor).inflate(3),
				d -> DisplayStore.get(d) != null);
		helper.assertValueEqual(placed.size(), 2, "placed parts");
		DisplayMeta first = DisplayStore.get(placed.get(0));
		helper.assertTrue(first.groupId() != null && first.groupId().equals(DisplayStore.get(placed.get(1)).groupId()), "parts share a group");
		helper.assertTrue(first.refund() != null, "refund recorded");
		helper.assertValueEqual(player.getInventory().countItem(Items.PLAYER_HEAD), 0, "heads consumed");
		helper.assertTrue(ModItems.isBlueprint(player.getMainHandItem()), "blueprint is kept");
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

		helper.assertTrue(main.consumesAction(), "main-hand click is handled");
		helper.assertValueEqual(off, InteractionResult.FAIL, "off hand must not place its block");
		List<Display.BlockDisplay> placed = helper.getLevel().getEntitiesOfClass(Display.BlockDisplay.class,
				new AABB(hit.getBlockPos().above()).inflate(0.5), d -> DisplayStore.get(d) != null);
		helper.assertValueEqual(placed.size(), 1, "displays placed by one click");
		helper.assertTrue(placed.get(0).getBlockState().is(Blocks.OAK_STAIRS), "placed the off-hand block");
		helper.assertValueEqual(player.getOffhandItem().getCount(), 1, "one item taken in survival");
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
		helper.assertValueEqual(placed.size(), 1, "item displays placed");
		helper.assertTrue(placed.get(0).getItemStack().is(Items.DIAMOND), "placed the off-hand item");
		helper.assertTrue(player.getOffhandItem().isEmpty(), "item taken");
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
		helper.assertTrue(ModItems.blueprintId(player.getMainHandItem()) != null, "blueprint was filled");

		BlockHitResult hit = clickTopOf(helper, new BlockPos(1, 1, 1));
		UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
		List<Display> placed = helper.getLevel().getEntitiesOfClass(Display.class, new AABB(hit.getBlockPos()).inflate(3),
				d -> DisplayStore.get(d) != null);
		helper.assertValueEqual(placed.size(), 2, "parts placed by right-click");
		placed.forEach(Display::discard);
		helper.succeed();
	}
}
