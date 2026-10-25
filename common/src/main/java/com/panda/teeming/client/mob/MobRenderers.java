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
        out.register(ModMobs.PRISMBACK.get(), ctx -> new TeemingRenderer<>(ctx, new PrismbackModel(ctx.bakeLayer(CreatureLayers.PRISMBACK)), "prismback",
                Set.of("base", "smoky_prismback"), 0.6F, 1.0F, 4, false));
        out.register(ModMobs.SLAGSPITTER.get(), ctx -> new TeemingRenderer<>(ctx, new SlagspitterModel(ctx.bakeLayer(CreatureLayers.SLAGSPITTER)), "slagspitter",
                Set.of("basalt_spitter", "base"), 0.5F, 1.0F, 2, false));
        out.register(ModMobs.ASH_JACKAL.get(), ctx -> new TeemingRenderer<>(ctx, new AshJackalModel(ctx.bakeLayer(CreatureLayers.ASH_JACKAL)), "ash_jackal",
                Set.of("base", "soul_jackal"), 0.45F, 1.0F, 1, false));
        out.register(ModMobs.SLAGHEART_GOLEM.get(), ctx -> new TeemingRenderer<>(ctx, new SlagheartGolemModel(ctx.bakeLayer(CreatureLayers.SLAGHEART_GOLEM)), "slagheart_golem",
                Set.of("base", "cooled_golem"), 0.9F, 1.15F, 4, true));
        out.register(ModMobs.SOOT_MANTA.get(), ctx -> new TeemingRenderer<>(ctx, new SootMantaModel(ctx.bakeLayer(CreatureLayers.SOOT_MANTA)), "soot_manta",
                Set.of("ember_manta"), 0.9F, 1.0F, 1, false));
        out.register(ModMobs.MAGMA_TORTOISE.get(), ctx -> new TeemingRenderer<>(ctx, new MagmaTortoiseModel(ctx.bakeLayer(CreatureLayers.MAGMA_TORTOISE)), "magma_tortoise",
                Set.of("base", "old_tortoise"), 1.2F, 1.0F, 1, false));
        out.register(ModMobs.VEINLEECH.get(), ctx -> new TeemingRenderer<>(ctx, new VeinleechModel(ctx.bakeLayer(CreatureLayers.VEINLEECH)), "veinleech",
                Set.of(), 0.4F, 1.0F, 1, false));
        out.register(ModMobs.SHREDJAW_BEETLE.get(), ctx -> new TeemingRenderer<>(ctx, new ShredjawBeetleModel(ctx.bakeLayer(CreatureLayers.SHREDJAW_BEETLE)), "shredjaw_beetle",
                Set.of("base", "warped_shredjaw"), 0.7F, 1.0F, 1, false));
        out.register(ModMobs.WEEPLING_BROODMOTHER.get(), ctx -> new TeemingRenderer<>(ctx, new WeeplingBroodmotherModel(ctx.bakeLayer(CreatureLayers.WEEPLING_BROODMOTHER)), "weepling_broodmother",
                Set.of("base", "bloated_brood"), 0.8F, 1.0F, 1, false));
        out.register(ModMobs.GLEAMTRAP.get(), ctx -> new TeemingRenderer<>(ctx, new GleamtrapModel(ctx.bakeLayer(CreatureLayers.GLEAMTRAP)), "gleamtrap",
                Set.of("base", "warped_gleamtrap"), 0.0F, 1.0F, 3, false));
        out.register(ModMobs.SCARLET_MANTIS.get(), ctx -> new TeemingRenderer<>(ctx, new ScarletMantisModel(ctx.bakeLayer(CreatureLayers.SCARLET_MANTIS)), "scarlet_mantis",
                Set.of("ashen_mantis", "base"), 0.5F, 1.0F, 3, false));
        out.register(ModMobs.PHANTASM_LANTERN.get(), ctx -> new TeemingRenderer<>(ctx, new PhantasmLanternModel(ctx.bakeLayer(CreatureLayers.PHANTASM_LANTERN)), "phantasm_lantern",
                Set.of("base", "crimson_lantern"), 0.35F, 1.0F, 1, false));
        out.register(ModMobs.WOOLCAP_GRAZER.get(), ctx -> new TeemingRenderer<>(ctx, new WoolcapGrazerModel(ctx.bakeLayer(CreatureLayers.WOOLCAP_GRAZER)), "woolcap_grazer",
                Set.of(), 0.6F, 1.0F, 1, false));
        out.register(ModMobs.SIPHONJELLY.get(), ctx -> new TeemingRenderer<>(ctx, new SiphonjellyModel(ctx.bakeLayer(CreatureLayers.SIPHONJELLY)), "siphonjelly",
                Set.of("base", "crimson_jelly"), 0.4F, 1.0F, 13, false));
        out.register(ModMobs.SPORE_CANNON.get(), ctx -> new TeemingRenderer<>(ctx, new SporeCannonModel(ctx.bakeLayer(CreatureLayers.SPORE_CANNON)), "spore_cannon",
                Set.of(), 0.6F, 1.0F, 1, true));
        out.register(ModMobs.GLOOMWEAVER.get(), ctx -> new TeemingRenderer<>(ctx, new GloomweaverModel(ctx.bakeLayer(CreatureLayers.GLOOMWEAVER)), "gloomweaver",
                Set.of("base", "crimson_weaver"), 0.9F, 1.0F, 3, false));
        out.register(ModMobs.OSSUARY_WYRM.get(), ctx -> new TeemingRenderer<>(ctx, new OssuaryWyrmModel(ctx.bakeLayer(CreatureLayers.OSSUARY_WYRM)), "ossuary_wyrm",
                Set.of("base", "bleached_wyrm"), 0.0F, 1.0F, 1, true));
        out.register(ModMobs.KEENING_SHADE.get(), ctx -> new TeemingRenderer<>(ctx, new KeeningShadeModel(ctx.bakeLayer(CreatureLayers.KEENING_SHADE)), "keening_shade",
                Set.of("base", "hollow_shade"), 0.35F, 1.0F, 1, false));
        out.register(ModMobs.SOULFIRE_SEER.get(), ctx -> new TeemingRenderer<>(ctx, new SoulfireSeerModel(ctx.bakeLayer(CreatureLayers.SOULFIRE_SEER)), "soulfire_seer",
                Set.of("base", "bone_seer"), 0.4F, 1.0F, 1, false));
        out.register(ModMobs.CINDERMOTH_SWARM.get(), ctx -> new TeemingRenderer<>(ctx, new CindermothSwarmModel(ctx.bakeLayer(CreatureLayers.CINDERMOTH_SWARM)), "cindermoth_swarm",
                Set.of("base", "soul_moths"), 0.3F, 1.0F, 1, false));
        out.register(ModMobs.SOULWARD_REVENANT.get(), ctx -> new TeemingRenderer<>(ctx, new SoulwardRevenantModel(ctx.bakeLayer(CreatureLayers.SOULWARD_REVENANT)), "soulward_revenant",
                Set.of("base", "gilded_revenant"), 0.55F, 1.0F, 4, true));
        out.register(ModMobs.BASALT_BEHEMOTH.get(), ctx -> new TeemingRenderer<>(ctx, new BasaltBehemothModel(ctx.bakeLayer(CreatureLayers.BASALT_BEHEMOTH)), "basalt_behemoth",
                Set.of("base"), 1.6F, 1.75F, 3, true));
        out.register(ModMobs.QUAKEHOPPER.get(), ctx -> new TeemingRenderer<>(ctx, new QuakehopperModel(ctx.bakeLayer(CreatureLayers.QUAKEHOPPER)), "quakehopper",
                Set.of("base", "magma_hopper"), 0.6F, 1.0F, 3, false));
        out.register(ModMobs.ASHHARRIER.get(), ctx -> new TeemingRenderer<>(ctx, new AshharrierModel(ctx.bakeLayer(CreatureLayers.ASHHARRIER)), "ashharrier",
                Set.of("base", "cinder_harrier"), 0.7F, 1.0F, 3, true));
        out.register(ModMobs.LAVALEAPER.get(), ctx -> new TeemingRenderer<>(ctx, new LavaleaperModel(ctx.bakeLayer(CreatureLayers.LAVALEAPER)), "lavaleaper",
                Set.of("base", "ember_leaper"), 0.45F, 1.0F, 3, true));
        out.register(ModMobs.BLACKSTONE_SCUTTLER.get(), ctx -> new TeemingRenderer<>(ctx, new BlackstoneScuttlerModel(ctx.bakeLayer(CreatureLayers.BLACKSTONE_SCUTTLER)), "blackstone_scuttler",
                Set.of("base", "gilded_scuttler"), 0.75F, 1.0F, 1, false));
        out.register(ModMobs.PYRE_HERALD.get(), ctx -> new TeemingRenderer<>(ctx, new PyreHeraldModel(ctx.bakeLayer(CreatureLayers.PYRE_HERALD)), "pyre_herald",
                Set.of("base", "soul_herald"), 0.4F, 1.0F, 1, false));
        out.register(ModMobs.FORTRESS_JAILER.get(), ctx -> new TeemingRenderer<>(ctx, new FortressJailerModel(ctx.bakeLayer(CreatureLayers.FORTRESS_JAILER)), "fortress_jailer",
                Set.of("base", "rusted_jailer"), 0.5F, 1.0F, 1, false));
        out.register(ModMobs.FLAMEWRIGHT.get(), ctx -> new TeemingRenderer<>(ctx, new FlamewrightModel(ctx.bakeLayer(CreatureLayers.FLAMEWRIGHT)), "flamewright",
                Set.of("base", "soul_wright"), 0.45F, 1.0F, 2, true));
        out.register(ModMobs.AUROVORE.get(), ctx -> new TeemingRenderer<>(ctx, new AurovoreModel(ctx.bakeLayer(CreatureLayers.AUROVORE)), "aurovore",
                Set.of("base", "gilded_aurovore"), 0.5F, 1.0F, 1, false));
        out.register(ModMobs.LEVIMOTH.get(), ctx -> new TeemingRenderer<>(ctx, new LevimothModel(ctx.bakeLayer(CreatureLayers.LEVIMOTH)), "levimoth",
                Set.of("base", "pale_levimoth"), 0.35F, 1.0F, 1, false));
    }
}
