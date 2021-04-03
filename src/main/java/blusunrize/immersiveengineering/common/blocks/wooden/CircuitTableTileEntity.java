/*
 * BluSunrize
 * Copyright (c) 2021
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.blocks.wooden;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.client.IModelOffsetProvider;
import blusunrize.immersiveengineering.api.tool.LogicCircuitHandler.LogicCircuitInstruction;
import blusunrize.immersiveengineering.common.IETileTypes;
import blusunrize.immersiveengineering.common.blocks.IEBaseTileEntity;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IHasDummyBlocks;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IInteractionObjectIE;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IStateBasedDirectional;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.common.util.inventory.IIEInventory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.EnumProperty;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CircuitTableTileEntity extends IEBaseTileEntity implements IIEInventory, IStateBasedDirectional,
		IHasDummyBlocks, IInteractionObjectIE, IModelOffsetProvider
{
	public static final BlockPos MASTER_POS = BlockPos.ZERO;
	public static final BlockPos DUMMY_POS = new BlockPos(1, 0, 0);
	public static final String[] SLOT_TYPES = new String[]{"backplane", "logic", "traces", "solder"};

	NonNullList<ItemStack> inventory = NonNullList.withSize(5, ItemStack.EMPTY);

	public CircuitTableTileEntity()
	{
		super(IETileTypes.CIRCUIT_TABLE.get());
	}

	@Override
	public void readCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		if(!descPacket)
		{
			ItemStackHelper.loadAllItems(nbt, inventory);
		}
	}

	@Override
	public void writeCustomNBT(CompoundNBT nbt, boolean descPacket)
	{
		if(!descPacket)
		{
			ItemStackHelper.saveAllItems(nbt, inventory);
		}
	}

	public static int getIngredientAmount(LogicCircuitInstruction instruction, int slot)
	{
		switch(slot)
		{
			case 0: // backplane
				return 1;
			case 1: // logic
				return instruction.getOperator().getComplexity();
			case 2: // traces
				return instruction.getInputs().length+1;
			case 3: // solder
				return (int)Math.ceil((instruction.getOperator().getComplexity()+instruction.getInputs().length+1)/2f);
		}
		return -1;
	}

	@OnlyIn(Dist.CLIENT)
	private AxisAlignedBB renderAABB;

	@OnlyIn(Dist.CLIENT)
	@Override
	public AxisAlignedBB getRenderBoundingBox()
	{
		if(renderAABB==null)
			renderAABB = new AxisAlignedBB(getPos().getX()-1, getPos().getY(), getPos().getZ()-1, getPos().getX()+2, getPos().getY()+2, getPos().getZ()+2);
		return renderAABB;
	}

	@Override
	public boolean canUseGui(PlayerEntity player)
	{
		return true;
	}

	@Override
	public IInteractionObjectIE getGuiMaster()
	{
		if(!isDummy())
			return this;
		Direction dummyDir = getFacing().rotateYCCW();
		TileEntity tileEntity = world.getTileEntity(pos.offset(dummyDir));
		if(tileEntity instanceof CircuitTableTileEntity)
			return (CircuitTableTileEntity)tileEntity;
		return null;
	}

	@Nonnull
	@Override
	public Container createMenu(int id, PlayerInventory playerInventory, PlayerEntity player)
	{
		return IInteractionObjectIE.super.createMenu(id, playerInventory, player);
	}

	@Override
	public NonNullList<ItemStack> getInventory()
	{
		return inventory;
	}

	@Override
	public boolean isStackValid(int slot, ItemStack stack)
	{
		return true;
	}

	@Override
	public int getSlotLimit(int slot)
	{
		return 64;
	}

	@Override
	public void doGraphicalUpdates(int slot)
	{
		this.markDirty();
	}

	@Override
	public PlacementLimitation getFacingLimitation()
	{
		return PlacementLimitation.HORIZONTAL;
	}

	@Override
	public boolean mirrorFacingOnPlacement(LivingEntity placer)
	{
		return false;
	}

	@Override
	public boolean canHammerRotate(Direction side, Vector3d hit, LivingEntity entity)
	{
		return true;
	}

	@Override
	public boolean canRotate(Direction axis)
	{
		return false;
	}

	@Override
	public EnumProperty<Direction> getFacingProperty()
	{
		return IEProperties.FACING_HORIZONTAL;
	}

	@Override
	public boolean isDummy()
	{
		return getState().get(IEProperties.MULTIBLOCKSLAVE);
	}

	@Nullable
	@Override
	public CircuitTableTileEntity master()
	{
		if(!isDummy())
			return this;
		// Used to provide tile-dependant drops after breaking
		if(tempMasterTE!=null)
			return (CircuitTableTileEntity)tempMasterTE;
		Direction dummyDir = isDummy()?getFacing().rotateYCCW(): getFacing().rotateY();
		BlockPos masterPos = getPos().offset(dummyDir);
		TileEntity te = Utils.getExistingTileEntity(world, masterPos);
		return (te instanceof CircuitTableTileEntity)?(CircuitTableTileEntity)te: null;
	}

	private void setDummy(boolean dummy)
	{
		setState(getState().with(IEProperties.MULTIBLOCKSLAVE, dummy));
	}

	@Override
	public void placeDummies(BlockItemUseContext ctx, BlockState state)
	{
		final Direction facing = getFacing();
		Direction dummyDir;
		if(facing.getAxis()==Axis.X)
			dummyDir = ctx.getHitVec().z < .5?Direction.NORTH: Direction.SOUTH;
		else
			dummyDir = ctx.getHitVec().x < .5?Direction.WEST: Direction.EAST;
		BlockPos dummyPos = pos.offset(dummyDir);
		if(!world.getBlockState(dummyPos).isReplaceable(BlockItemUseContext.func_221536_a(ctx, dummyPos, dummyDir)))
		{
			dummyDir = dummyDir.getOpposite();
			dummyPos = pos.offset(dummyDir);
		}
		boolean mirror = dummyDir!=facing.rotateY();
		if(mirror)
			setDummy(true);
		world.setBlockState(dummyPos, state);
		CircuitTableTileEntity tileEntityDummy = ((CircuitTableTileEntity)world.getTileEntity(dummyPos));
		tileEntityDummy.setDummy(!mirror);
		tileEntityDummy.setFacing(facing);
	}

	@Override
	public void breakDummies(BlockPos pos, BlockState state)
	{
		tempMasterTE = master();
		Direction dummyDir = isDummy()?getFacing().rotateYCCW(): getFacing().rotateY();
		world.removeBlock(pos.offset(dummyDir), false);
	}

	@Override
	public BlockPos getModelOffset(BlockState state, @Nullable Vector3i size)
	{
		if(isDummy())
			return DUMMY_POS;
		else
			return MASTER_POS;
	}
}