package com.panda.teeming.client.mob;

import com.panda.teeming.client.ClientSinks;
import com.panda.teeming.registry.ModMobs;
import java.util.Set;

/** One renderer per mob. */
public final class MobRenderers {
    private MobRenderers() {}

    public static void register(ClientSinks.Renderers out) {
        out.register(ModMobs.THORNHORN.get(), ctx -> new TeemingRenderer<>(ctx, new ThornhornModel(ctx.bakeLayer(CreatureLayers.THORNHORN)), "thornhorn",
                Set.of(), 0.8F, 1.0F, 1, true));
        out.register(ModMobs.SNAGWOOD.get(), ctx -> new TeemingRenderer<>(ctx, new SnagwoodModel(ctx.bakeLayer(CreatureLayers.SNAGWOOD)), "snagwood",
                Set.of(), 0.6F, 1.0F, 1, false));
        out.register(ModMobs.PUFFCAP_SHEPHERD.get(), ctx -> new TeemingRenderer<>(ctx, new PuffcapShepherdModel(ctx.bakeLayer(CreatureLayers.PUFFCAP_SHEPHERD)), "puffcap_shepherd",
                Set.of("glowcap"), 0.45F, 1.0F, 1, false));
        out.register(ModMobs.MIRE_GULPER.get(), ctx -> new TeemingRenderer<>(ctx, new MireGulperModel(ctx.bakeLayer(CreatureLayers.MIRE_GULPER)), "mire_gulper",
                Set.of(), 0.75F, 1.0F, 1, true));
        out.register(ModMobs.DUNEFIN.get(), ctx -> new TeemingRenderer<>(ctx, new DunefinModel(ctx.bakeLayer(CreatureLayers.DUNEFIN)), "dunefin",
                Set.of(), 0.6F, 1.0F, 1, true));
        out.register(ModMobs.RATTLECOIL.get(), ctx -> new TeemingRenderer<>(ctx, new RattlecoilModel(ctx.bakeLayer(CreatureLayers.RATTLECOIL)), "rattlecoil",
                Set.of(), 0.5F, 1.0F, 1, false));
        out.register(ModMobs.SNOWROLLER.get(), ctx -> new TeemingRenderer<>(ctx, new SnowrollerModel(ctx.bakeLayer(CreatureLayers.SNOWROLLER)), "snowroller",
                Set.of(), 0.55F, 1.0F, 1, true));
        out.register(ModMobs.SHIVERWRAITH.get(), ctx -> new TeemingRenderer<>(ctx, new ShiverwraithModel(ctx.bakeLayer(CreatureLayers.SHIVERWRAITH)), "shiverwraith",
                Set.of("base", "hoarfrost"), 0.4F, 1.0F, 1, false));
        out.register(ModMobs.GLOAMHOUND.get(), ctx -> new TeemingRenderer<>(ctx, new GloamhoundModel(ctx.bakeLayer(CreatureLayers.GLOAMHOUND)), "gloamhound",
                Set.of("base", "snowpelt"), 0.5F, 1.0F, 1, false));
        out.register(ModMobs.VINEDROP_STRANGLER.get(), ctx -> new TeemingRenderer<>(ctx, new VinedropStranglerModel(ctx.bakeLayer(CreatureLayers.VINEDROP_STRANGLER)), "vinedrop_strangler",
                Set.of(), 0.45F, 1.0F, 1, true));
        out.register(ModMobs.SNAPMAW_BLOOM.get(), ctx -> new TeemingRenderer<>(ctx, new SnapmawBloomModel(ctx.bakeLayer(CreatureLayers.SNAPMAW_BLOOM)), "snapmaw_bloom",
                Set.of("azalea_bloom", "base"), 0.55F, 1.0F, 1, true));
        out.register(ModMobs.STORMEEL.get(), ctx -> new TeemingRenderer<>(ctx, new StormeelModel(ctx.bakeLayer(CreatureLayers.STORMEEL)), "stormeel",
                Set.of("abyssal_eel", "base"), 0.35F, 1.0F, 3, false));
        out.register(ModMobs.SHELLGUARD_CRAB.get(), ctx -> new TeemingRenderer<>(ctx, new ShellguardCrabModel(ctx.bakeLayer(CreatureLayers.SHELLGUARD_CRAB)), "shellguard_crab",
                Set.of(), 0.6F, 1.0F, 1, true));
        out.register(ModMobs.DRIPFANG.get(), ctx -> new TeemingRenderer<>(ctx, new DripfangModel(ctx.bakeLayer(CreatureLayers.DRIPFANG)), "dripfang",
                Set.of(), 0.3F, 1.0F, 1, true));
        out.register(ModMobs.HUSHWING.get(), ctx -> new TeemingRenderer<>(ctx, new HushwingModel(ctx.bakeLayer(CreatureLayers.HUSHWING)), "hushwing",
                Set.of(), 0.3F, 1.0F, 1, false));
        out.register(ModMobs.MYCELITE_SHAMBLER.get(), ctx -> new TeemingRenderer<>(ctx, new MyceliteShamblerModel(ctx.bakeLayer(CreatureLayers.MYCELITE_SHAMBLER)), "mycelite_shambler",
                Set.of(), 0.6F, 1.0F, 1, false));
        out.register(ModMobs.PETALKIN.get(), ctx -> new TeemingRenderer<>(ctx, new PetalkinModel(ctx.bakeLayer(CreatureLayers.PETALKIN)), "petalkin",
                Set.of(), 0.35F, 1.0F, 1, false));
        out.register(ModMobs.HOLLOW_MOURNER.get(), ctx -> new TeemingRenderer<>(ctx, new HollowMournerModel(ctx.bakeLayer(CreatureLayers.HOLLOW_MOURNER)), "hollow_mourner",
                Set.of(), 0.45F, 1.0F, 1, false));
        out.register(ModMobs.DUSTWHIRL.get(), ctx -> new TeemingRenderer<>(ctx, new DustwhirlModel(ctx.bakeLayer(CreatureLayers.DUSTWHIRL)), "dustwhirl",
                Set.of("base", "red_dust"), 0.45F, 1.0F, 2, true));
        out.register(ModMobs.SKREEL.get(), ctx -> new TeemingRenderer<>(ctx, new SkreelModel(ctx.bakeLayer(CreatureLayers.SKREEL)), "skreel",
                Set.of(), 0.5F, 1.0F, 1, true));
        out.register(ModMobs.SLAGSPITTER.get(), ctx -> new TeemingRenderer<>(ctx, new SlagspitterModel(ctx.bakeLayer(CreatureLayers.SLAGSPITTER)), "slagspitter",
                Set.of("basalt_spitter", "base"), 0.5F, 1.0F, 2, false));
        out.register(ModMobs.SLAGHEART_GOLEM.get(), ctx -> new TeemingRenderer<>(ctx, new SlagheartGolemModel(ctx.bakeLayer(CreatureLayers.SLAGHEART_GOLEM)), "slagheart_golem",
                Set.of("base", "cooled_golem"), 0.9F, 1.15F, 4, true));
        out.register(ModMobs.SOOT_MANTA.get(), ctx -> new TeemingRenderer<>(ctx, new SootMantaModel(ctx.bakeLayer(CreatureLayers.SOOT_MANTA)), "soot_manta",
                Set.of("ember_manta"), 0.9F, 1.0F, 1, false));
        out.register(ModMobs.MAGMA_TORTOISE.get(), ctx -> new TeemingRenderer<>(ctx, new MagmaTortoiseModel(ctx.bakeLayer(CreatureLayers.MAGMA_TORTOISE)), "magma_tortoise",
                Set.of("base", "old_tortoise"), 1.2F, 1.0F, 1, false));
        out.register(ModMobs.GLEAMTRAP.get(), ctx -> new TeemingRenderer<>(ctx, new GleamtrapModel(ctx.bakeLayer(CreatureLayers.GLEAMTRAP)), "gleamtrap",
                Set.of("base", "warped_gleamtrap"), 0.0F, 1.0F, 3, false));
        out.register(ModMobs.LAVALEAPER.get(), ctx -> new TeemingRenderer<>(ctx, new LavaleaperModel(ctx.bakeLayer(CreatureLayers.LAVALEAPER)), "lavaleaper",
                Set.of("base", "ember_leaper"), 0.45F, 1.0F, 3, true));
        out.register(ModMobs.PYRE_HERALD.get(), ctx -> new TeemingRenderer<>(ctx, new PyreHeraldModel(ctx.bakeLayer(CreatureLayers.PYRE_HERALD)), "pyre_herald",
                Set.of("base", "soul_herald"), 0.4F, 1.0F, 1, false));
        out.register(ModMobs.FORTRESS_JAILER.get(), ctx -> new TeemingRenderer<>(ctx, new FortressJailerModel(ctx.bakeLayer(CreatureLayers.FORTRESS_JAILER)), "fortress_jailer",
                Set.of("base", "rusted_jailer"), 0.5F, 1.0F, 1, false));
        out.register(ModMobs.FLAMEWRIGHT.get(), ctx -> new TeemingRenderer<>(ctx, new FlamewrightModel(ctx.bakeLayer(CreatureLayers.FLAMEWRIGHT)), "flamewright",
                Set.of("base", "soul_wright"), 0.45F, 1.0F, 2, true));
        out.register(ModMobs.LEVIMOTH.get(), ctx -> new TeemingRenderer<>(ctx, new LevimothModel(ctx.bakeLayer(CreatureLayers.LEVIMOTH)), "levimoth",
                Set.of("base", "pale_levimoth"), 0.35F, 1.0F, 1, false));
    }
}
