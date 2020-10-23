/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.api;

import com.google.common.base.MoreObjects;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

//TODO do not extend BlockPos, can cause strange bugs when used in world access
public class DirectionalBlockPos extends BlockPos
{
	public Direction direction;

	public DirectionalBlockPos(BlockPos pos)
	{
		this(pos, Direction.DOWN);
	}

	public DirectionalBlockPos(BlockPos pos, Direction direction)
	{
		this(pos.getX(), pos.getY(), pos.getZ(), direction);
	}

	public DirectionalBlockPos(int x, int y, int z, Direction direction)
	{
		super(x, y, z);
		this.direction = direction;
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this).add("x", this.getX()).add("y", this.getY()).add("z", this.getZ()).add("direction", this.direction.toString()).toString();
	}

	public TileEntity getTile(World world)
	{
		return world.getTileEntity(this);
	}

	@Nonnull
	@Override
	public BlockPos toImmutable()
	{
		return new BlockPos(this);
	}
}
