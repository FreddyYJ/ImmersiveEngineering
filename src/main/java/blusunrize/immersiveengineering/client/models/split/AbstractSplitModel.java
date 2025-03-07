/*
 * BluSunrize
 * Copyright (c) 2020
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 *
 */

package blusunrize.immersiveengineering.client.models.split;

import blusunrize.immersiveengineering.api.IEProperties.Model;
import blusunrize.immersiveengineering.api.client.IModelOffsetProvider;
import blusunrize.immersiveengineering.api.utils.client.CombinedModelData;
import blusunrize.immersiveengineering.api.utils.client.SinglePropertyModelData;
import blusunrize.immersiveengineering.client.models.CompositeBakedModel;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import malte0811.modelsplitter.ClumpedModel;
import malte0811.modelsplitter.SplitModel;
import malte0811.modelsplitter.math.ModelSplitterVec3i;
import malte0811.modelsplitter.model.OBJModel;
import malte0811.modelsplitter.model.Polygon;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.IModelData;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public abstract class AbstractSplitModel<T extends BakedModel> extends CompositeBakedModel<T>
{
	private final Vec3i size;

	public AbstractSplitModel(T base, Vec3i size)
	{
		super(base);
		this.size = size;
	}

	@Nonnull
	@Override
	public IModelData getModelData(
			@Nonnull BlockAndTintGetter world,
			@Nonnull BlockPos pos,
			@Nonnull BlockState state,
			@Nonnull IModelData tileData
	)
	{
		IModelData baseData = super.getModelData(world, pos, state, tileData);
		BlockEntity te = world.getBlockEntity(pos);
		BlockPos offset = null;
		if(te instanceof IModelOffsetProvider)
			offset = ((IModelOffsetProvider)te).getModelOffset(state, size);
		else if(state.getBlock() instanceof IModelOffsetProvider)
			offset = ((IModelOffsetProvider)state.getBlock()).getModelOffset(state, size);
		if(offset!=null)
			return CombinedModelData.combine(new SinglePropertyModelData<>(offset, Model.SUBMODEL_OFFSET), baseData);
		else
			return baseData;
	}

	protected Map<Vec3i, List<BakedQuad>> split(List<BakedQuad> in, Set<Vec3i> parts, ModelState transform)
	{
		List<Polygon<TextureAtlasSprite>> polys = in.stream()
				.map(PolygonUtils::toPolygon)
				.collect(Collectors.toList());
		SplitModel<TextureAtlasSprite> splitData = new SplitModel<>(new OBJModel<>(polys));
		Set<ModelSplitterVec3i> partsBMS = parts.stream()
				.map(v -> new ModelSplitterVec3i(v.getX(), v.getY(), v.getZ()))
				.collect(Collectors.toSet());
		ClumpedModel<TextureAtlasSprite> clumpedModel = new ClumpedModel<>(splitData, partsBMS);

		Map<Vec3i, List<BakedQuad>> map = new HashMap<>();
		for(Entry<ModelSplitterVec3i, OBJModel<TextureAtlasSprite>> e : clumpedModel.getClumpedParts().entrySet())
		{
			List<BakedQuad> subModelFaces = new ArrayList<>(e.getValue().getFaces().size());
			for(Polygon<TextureAtlasSprite> p : e.getValue().getFaces())
				subModelFaces.add(PolygonUtils.toBakedQuad(p, transform, DefaultVertexFormat.BLOCK));
			Vec3i mcKey = new Vec3i(e.getKey().getX(), e.getKey().getY(), e.getKey().getZ());
			map.put(mcKey, subModelFaces);
		}
		return map;
	}
}
