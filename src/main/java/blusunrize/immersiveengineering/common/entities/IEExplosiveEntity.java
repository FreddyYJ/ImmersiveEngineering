/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.entities;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.common.util.IEExplosion;
import blusunrize.immersiveengineering.mixin.accessors.TNTEntityAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Explosion.BlockInteraction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.Nonnull;
import java.util.Optional;

public class IEExplosiveEntity extends PrimedTnt
{
	public static final EntityType<IEExplosiveEntity> TYPE = Builder
			.<IEExplosiveEntity>of(IEExplosiveEntity::new, MobCategory.MISC)
			.fireImmune()
			.sized(0.98F, 0.98F)
			.build(ImmersiveEngineering.MODID+":explosive");

	static
	{
		TYPE.setRegistryName(ImmersiveEngineering.MODID, "explosive");
	}

	private float size;
	private Explosion.BlockInteraction mode = BlockInteraction.BREAK;
	private boolean isFlaming = false;
	private float explosionDropChance;
	public BlockState block;
	private Component name;

	private static final EntityDataAccessor<Optional<BlockState>> dataMarker_block = SynchedEntityData.defineId(IEExplosiveEntity.class, EntityDataSerializers.BLOCK_STATE);
	private static final EntityDataAccessor<Integer> dataMarker_fuse = SynchedEntityData.defineId(IEExplosiveEntity.class, EntityDataSerializers.INT);

	public IEExplosiveEntity(EntityType<IEExplosiveEntity> type, Level world)
	{
		super(type, world);
	}

	public IEExplosiveEntity(Level world, double x, double y, double z, LivingEntity igniter, BlockState blockstate, float size)
	{
		super(TYPE, world);
		this.setPos(x, y, z);
		double jumpingDirection = world.random.nextDouble()*2*Math.PI;
		this.setDeltaMovement(-Math.sin(jumpingDirection)*0.02D, 0.2, -Math.cos(jumpingDirection)*0.02D);
		this.setFuse(80);
		this.xo = x;
		this.yo = y;
		this.zo = z;
		((TNTEntityAccess)this).setOwner(igniter);
		this.size = size;
		this.block = blockstate;
		this.explosionDropChance = 1/size;
		this.setBlockSynced();
	}

	public IEExplosiveEntity(Level world, BlockPos pos, LivingEntity igniter, BlockState blockstate, float size)
	{
		this(world, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, igniter, blockstate, size);
	}

	public IEExplosiveEntity setMode(BlockInteraction smoke)
	{
		this.mode = smoke;
		return this;
	}

	public IEExplosiveEntity setFlaming(boolean fire)
	{
		this.isFlaming = fire;
		return this;
	}

	public IEExplosiveEntity setDropChance(float chance)
	{
		this.explosionDropChance = chance;
		return this;
	}

	@Override
	protected void defineSynchedData()
	{
		super.defineSynchedData();
		this.entityData.define(dataMarker_block, Optional.empty());
		this.entityData.define(dataMarker_fuse, 0);
	}

	private void setBlockSynced()
	{
		if(this.block!=null)
		{
			this.entityData.set(dataMarker_block, Optional.of(this.block));
			this.entityData.set(dataMarker_fuse, this.getLife());
		}
	}

	private void getBlockSynced()
	{
		this.block = this.entityData.get(dataMarker_block).orElse(null);
		this.setFuse(this.entityData.get(dataMarker_fuse));
	}

	@Nonnull
	@Override
	public Component getName()
	{
		if(this.block!=null&&name==null)
		{
			ItemStack s = new ItemStack(this.block.getBlock(), 1);
			if(!s.isEmpty()&&s.getItem()!=Items.AIR)
				name = s.getHoverName();
		}
		if(name!=null)
			return name;
		return super.getName();
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tagCompound)
	{
		super.addAdditionalSaveData(tagCompound);
		tagCompound.putFloat("explosionPower", size);
		tagCompound.putInt("explosionSmoke", mode.ordinal());
		tagCompound.putBoolean("explosionFire", isFlaming);
		if(this.block!=null)
			tagCompound.putInt("block", Block.getId(this.block));
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tagCompound)
	{
		super.readAdditionalSaveData(tagCompound);
		size = tagCompound.getFloat("explosionPower");
		mode = BlockInteraction.values()[tagCompound.getInt("explosionSmoke")];
		isFlaming = tagCompound.getBoolean("explosionFire");
		if(tagCompound.contains("block", NBT.TAG_INT))
			this.block = Block.stateById(tagCompound.getInt("block"));
	}


	@Override
	public void tick()
	{
		if(level.isClientSide&&this.block==null)
			this.getBlockSynced();

		this.xo = this.getX();
		this.yo = this.getY();
		this.zo = this.getZ();
		if(!this.isNoGravity())
		{
			this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
		}

		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
		if(this.onGround)
		{
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
		}
		int newFuse = this.getLife()-1;
		this.setFuse(newFuse);
		if(newFuse <= 0)
		{
			this.remove();

			Explosion explosion = new IEExplosion(level, this, getX(), getY()+(getBbHeight()/16f), getZ(), size, isFlaming, mode)
					.setDropChance(explosionDropChance);
			if(!ForgeEventFactory.onExplosionStart(level, explosion))
			{
				explosion.explode();
				explosion.finalizeExplosion(true);
			}
		}
		else
		{
			this.updateInWaterStateAndDoFluidPushing();
			this.level.addParticle(ParticleTypes.SMOKE, this.getX(), this.getY()+0.5D, this.getZ(), 0.0D, 0.0D, 0.0D);
		}
	}

	@Nonnull
	@Override
	public EntityType<?> getType()
	{
		return TYPE;
	}

	@Override
	public Packet<?> getAddEntityPacket()
	{
		return NetworkHooks.getEntitySpawningPacket(this);
	}

}