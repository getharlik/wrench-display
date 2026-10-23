package dev.harlik.wrenchdisplay.fabric.mixin;

import com.mojang.math.Transformation;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Display has a public transformation setter but no getter. */
@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("createTransformation")
	static Transformation wrenchDisplay$createTransformation(SynchedEntityData data) {
		throw new AssertionError();
	}
}
