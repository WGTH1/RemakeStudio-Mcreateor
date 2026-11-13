package ${package}.mixins;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.server.packs.repository.PackRepository.class)
public abstract class DisplayPreviewRepositoryMixin {
	@org.spongepowered.asm.mixin.injection.Inject(method = "discoverAvailable", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), cancellable = true)
	private void remarkDisplayPreview(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.Map<String, net.minecraft.server.packs.repository.Pack>> callback) {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) return;
		var pack = ${package}.client.preview.DisplayLivePreview.previewPack();
		if (pack == null) return;
		var available = new java.util.HashMap<>(callback.getReturnValue());
		available.put(pack.getId(), pack);
		callback.setReturnValue(available);
	}
}
