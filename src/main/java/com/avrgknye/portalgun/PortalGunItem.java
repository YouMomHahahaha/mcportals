package com.avrgknye.portalgun;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class PortalGunItem extends Item {
	public PortalGunItem(Properties properties) {
		super(properties);
	}

	/** Left click must never break blocks (it fires the blue portal instead). */
	@Override
	public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
		return false;
	}
}
