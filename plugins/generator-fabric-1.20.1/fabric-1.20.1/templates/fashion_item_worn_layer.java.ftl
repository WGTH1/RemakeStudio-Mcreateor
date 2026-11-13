<#-- @formatter:off -->
package ${package}.client.renderer.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import ${package}.init.${JavaModName}Items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

@Environment(EnvType.CLIENT)
public class ${name}FashionLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

	public static void clientLoad() {
		ArmorRenderer.register((poseStack, buffer, stack, entity, slot, light, contextModel) -> {}, fashionItems());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityType == EntityType.PLAYER && entityRenderer instanceof PlayerRenderer playerRenderer)
				registrationHelper.register(new ${name}FashionLayer(playerRenderer));
		});
	}

	private static Item[] fashionItems() {
		return new Item[] {
			<#if data.singleItemSet>
			${JavaModName}Items.${REGISTRYNAME}_${data.getPrimaryItemConstantSuffix()}
			<#else>
			<#if data.enableHelmet>${JavaModName}Items.${REGISTRYNAME}_HELMET,</#if>
			<#if data.enableBody>${JavaModName}Items.${REGISTRYNAME}_CHESTPLATE,</#if>
			<#if data.enableLeggings>${JavaModName}Items.${REGISTRYNAME}_LEGGINGS,</#if>
			<#if data.enableBoots>${JavaModName}Items.${REGISTRYNAME}_BOOTS,</#if>
			</#if>
		};
	}

	public ${name}FashionLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
			AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
			float netHeadYaw, float headPitch) {
		<#if data.singleItemSet>
		Item fashionItem = ${JavaModName}Items.${REGISTRYNAME}_${data.getPrimaryItemConstantSuffix()};
		<#if data.enableHelmet>
		renderHead(player, player.getItemBySlot(EquipmentSlot.HEAD), fashionItem, poseStack, buffer, packedLight);
		</#if>
		<#if data.enableBody>
		renderBody(player, player.getItemBySlot(EquipmentSlot.CHEST), fashionItem, poseStack, buffer, packedLight);
		</#if>
		<#if data.enableLeggings>
		renderLegPair(player, player.getItemBySlot(EquipmentSlot.LEGS), fashionItem, poseStack, buffer, packedLight, false);
		</#if>
		<#if data.enableBoots>
		renderLegPair(player, player.getItemBySlot(EquipmentSlot.FEET), fashionItem, poseStack, buffer, packedLight, true);
		</#if>
		<#else>
		<#if data.enableHelmet>
		renderHead(player, player.getItemBySlot(EquipmentSlot.HEAD), ${JavaModName}Items.${REGISTRYNAME}_HELMET,
				poseStack, buffer, packedLight);
		</#if>
		<#if data.enableBody>
		renderBody(player, player.getItemBySlot(EquipmentSlot.CHEST), ${JavaModName}Items.${REGISTRYNAME}_CHESTPLATE,
				poseStack, buffer, packedLight);
		</#if>
		<#if data.enableLeggings>
		renderLegPair(player, player.getItemBySlot(EquipmentSlot.LEGS), ${JavaModName}Items.${REGISTRYNAME}_LEGGINGS,
				poseStack, buffer, packedLight, false);
		</#if>
		<#if data.enableBoots>
		renderLegPair(player, player.getItemBySlot(EquipmentSlot.FEET), ${JavaModName}Items.${REGISTRYNAME}_BOOTS,
				poseStack, buffer, packedLight, true);
		</#if>
		</#if>
	}

	private void renderHead(AbstractClientPlayer player, ItemStack stack, Item expectedItem, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		if (!isExpectedFashionItem(stack, expectedItem))
			return;
		poseStack.pushPose();
		getParentModel().head.translateAndRotate(poseStack);
		CustomHeadLayer.translateToHead(poseStack, false);
		<@wornTransform "helmet"/>
		renderWornItem(player, stack, ItemDisplayContext.HEAD, poseStack, buffer, packedLight);
		poseStack.popPose();
	}

	private void renderBody(AbstractClientPlayer player, ItemStack stack, Item expectedItem, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		if (!isExpectedFashionItem(stack, expectedItem))
			return;
		poseStack.pushPose();
		getParentModel().body.translateAndRotate(poseStack);
		poseStack.translate(0.0F, 0.35F, -0.2F);
		prepareRigidItemPose(poseStack, 0.72F);
		<@wornTransform "body"/>
		renderWornItem(player, stack, ItemDisplayContext.FIXED, poseStack, buffer, packedLight);
		poseStack.popPose();
	}

	private void renderLegPair(AbstractClientPlayer player, ItemStack stack, Item expectedItem, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, boolean boots) {
		if (!isExpectedFashionItem(stack, expectedItem))
			return;
		if (previewSideEnabled(boots ? "boots" : "leggings", true,
				boots ? ${data.getWornDisplay().bootsLeftEnabled?c} : ${data.getWornDisplay().leggingsLeftEnabled?c}))
			renderLeg(player, stack, getParentModel().leftLeg, poseStack, buffer, packedLight, boots, true);
		if (previewSideEnabled(boots ? "boots" : "leggings", false,
				boots ? ${data.getWornDisplay().bootsRightEnabled?c} : ${data.getWornDisplay().leggingsRightEnabled?c}))
			renderLeg(player, stack, getParentModel().rightLeg, poseStack, buffer, packedLight, boots, false);
	}

	private void renderLeg(AbstractClientPlayer player, ItemStack stack, ModelPart leg, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, boolean boots, boolean left) {
		poseStack.pushPose();
		leg.translateAndRotate(poseStack);
		poseStack.translate(0.0F, boots ? 0.7F : 0.3F, -0.08F);
		prepareRigidItemPose(poseStack, boots ? 0.42F : 0.48F);
		if (boots) {
			if (left) { <@wornTransform "boots" true/> } else { <@wornTransform "boots" false/> }
		} else {
			if (left) { <@wornTransform "leggings" true/> } else { <@wornTransform "leggings" false/> }
		}
		renderWornItem(player, stack, ItemDisplayContext.FIXED, poseStack, buffer, packedLight);
		poseStack.popPose();
	}

	private static boolean isExpectedFashionItem(ItemStack stack, Item expectedItem) {
		return !stack.isEmpty() && stack.getItem() == expectedItem;
	}

	private static boolean previewSideEnabled(String slot, boolean left, boolean fallback) {
		var preview = ${package}.client.preview.DisplayLivePreview.wornOverride("${registryname}", slot, left);
		return preview == null ? fallback : preview.enabled();
	}

	private static boolean applyPreviewTransform(PoseStack poseStack, String slot, boolean left) {
		var preview = ${package}.client.preview.DisplayLivePreview.wornOverride("${registryname}", slot, left);
		if (preview == null) return false;
		poseStack.translate(preview.translationX() / 16.0, preview.translationY() / 16.0, preview.translationZ() / 16.0);
		poseStack.mulPose(new org.joml.Quaternionf().rotationXYZ(
				(float) Math.toRadians(preview.rotationX()), (float) Math.toRadians(preview.rotationY()),
				(float) Math.toRadians(preview.rotationZ())));
		poseStack.scale((float) preview.scaleX(), (float) preview.scaleY(), (float) preview.scaleZ());
		return true;
	}

	private static void prepareRigidItemPose(PoseStack poseStack, float scale) {
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		poseStack.scale(scale, -scale, -scale);
	}

	private static void renderWornItem(AbstractClientPlayer player, ItemStack stack, ItemDisplayContext context,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, context, false, poseStack, buffer,
				player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId());
	}
}
<#-- @formatter:on -->

<#macro wornTransform slot left=true>
<#assign worn = data.getWornTransformFor(slot, left)>
if (!applyPreviewTransform(poseStack, "${slot}", ${left?c})) {
poseStack.translate(${worn.translationX?c} / 16.0, ${worn.translationY?c} / 16.0, ${worn.translationZ?c} / 16.0);
poseStack.mulPose(new org.joml.Quaternionf().rotationXYZ(
    (float) Math.toRadians(${worn.rotationX?c}), (float) Math.toRadians(${worn.rotationY?c}), (float) Math.toRadians(${worn.rotationZ?c})));
poseStack.scale(${worn.scaleX?c}F, ${worn.scaleY?c}F, ${worn.scaleZ?c}F);
}
</#macro>
