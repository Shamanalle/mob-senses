package com.kasper.mobsenses;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The Mixin config plugin of every band's Mixin config in the universal Fabric jar. Each band's config
 * lists no mixins itself (MergeJars moved them into the band table): this plugin gives Mixin the band's
 * mixins only when that band is the running Minecraft's, so the mixins of the other bands, built for
 * other Minecraft versions and maybe for a newer Java, are never read.
 */
public final class BandMixins implements IMixinConfigPlugin {

    private static final String PACKAGE = "com.kasper.mobsenses.b.";

    /** The band of the config this instance serves, from its package: com.kasper.mobsenses.b.<band>.mixin */
    private String band;

    @Override
    public void onLoad(String mixinPackage) {
        if (mixinPackage.startsWith(PACKAGE)) {
            int dot = mixinPackage.indexOf('.', PACKAGE.length());
            band = dot < 0 ? mixinPackage.substring(PACKAGE.length()) : mixinPackage.substring(PACKAGE.length(), dot);
        }
    }

    @Override
    public List<String> getMixins() {
        try {
            if (band == null || !band.equals(BandAdapter.band())) {
                return Collections.emptyList();
            }
            List<String> out = new ArrayList<>();
            for (String name : BandAdapter.load().getProperty(band + ".mixins", "").split(",")) {
                if (!name.trim().isEmpty()) {
                    out.add(name.trim());
                }
            }
            return out;
        } catch (Exception e) {
            // No band fits this Minecraft: BandAdapter says why when it creates the entrypoints
            return Collections.emptyList();
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
