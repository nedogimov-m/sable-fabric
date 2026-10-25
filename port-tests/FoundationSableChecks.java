import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;

/** Real native bodies across positive/negative chunk boundaries and separate-JVM persistence. */
public final class FoundationSableChecks {
    private record Rig(ServerSubLevel ship, BlockPos source, Map<BlockPos,BlockPos> positions, double initialY) {}
    public static void run(ClientGameTestContext test, TestSingleplayerContext world,
            Map<String,Object> report, List<String> checks) throws Exception {
        String selection = System.getProperty("foundation.case", "physics");
        var result = new LinkedHashMap<String,Object>(); report.put("sable", result);
        if (selection.equals("dimensions")) { dimensions(test, world, result, checks); return; }
        if (selection.equals("restore")) { restore(test, world, result, checks); return; }
        result.put("scope", "Real SubLevelAssemblyHelper bodies, native Rapier gravity/contact, negative/cross-chunk source positions, actual chest/finite torch interactions, renderer resource reload and cold saved-world input. Assembly prepares fixtures; no pose, velocity, force or constraint writes.");
        world.getServer().runCommand("gamemode spectator @a"); world.getServer().runCommand("time set noon"); world.getServer().runCommand("weather clear");
        var rigs = world.getServer().computeOnServer(server -> build(server));
        test.waitFor(client -> rigs.stream().allMatch(rig -> SubLevelContainer.getContainer(client.level).getSubLevel(rig.ship().getUniqueId())
                instanceof ClientSubLevel ship && ship.isFinalized()), 600);
        test.waitTicks(120);
        var samples = world.getServer().computeOnServer(server -> {
            var rows = new ArrayList<Object>();
            for (var rig : rigs) {
                var body = RigidBodyHandle.of(rig.ship()); check(body != null && rig.ship().getMassTracker().getMass() > 0, "Real native body/mass missing");
                var p = rig.ship().logicalPose().position(); var velocity = body.getLinearVelocity(new Vector3d());
                check(Double.isFinite(p.x()+p.y()+p.z()+velocity.length()), "Native physics produced nonfinite pose/velocity");
                check(p.y() < rig.initialY()-1, "Real gravity did not move the assembled body");
                double minY = rig.positions().values().stream().mapToDouble(local -> rig.ship().logicalPose().transformPosition(Vec3.atLowerCornerOf(local)).y).min().orElseThrow();
                check(minY > 117.7 && minY < 118.3, "Native body did not settle on its actual stone support: " + minY);
                check(Math.abs(velocity.y) < .15, "Native resting body retained excessive vertical velocity: " + velocity.y);
                rows.add(Map.of("ship",rig.ship().getUniqueId().toString(),"source",rig.source().toShortString(),"blocks",rig.positions().size(),
                        "drop",rig.initialY()-p.y(),"bottomY",minY,"velocity",List.of(velocity.x,velocity.y,velocity.z),"mass",rig.ship().getMassTracker().getMass()));
            }
            return rows;
        });
        result.put("nativeGravityContacts", samples);
        checks.add("Four real bodies cross positive/negative Minecraft chunk boundaries, fall under native gravity and settle on actual terrain with finite pose/velocity and positive mass");
        var rig = rigs.getFirst(); interact(test, world, rig, result, checks);
        world.getServer().runCommand("gamemode spectator @a"); world.getServer().runCommand("tp @a 22 124 24 140 30"); test.waitTicks(12);
        result.put("worldScreenshot", test.takeScreenshot("foundation-sable-native-gravity-bodies").toString());
        var reload = new AtomicReference<CompletableFuture<Void>>();
        test.runOnClient(client -> reload.set(client.reloadResourcePacks()));
        test.waitFor(client -> reload.get() != null && reload.get().isDone(), 600); test.runOnClient(client -> reload.get().join());
        test.waitFor(client -> client.gui.overlay() == null, 300); test.waitTicks(12);
        result.put("reloadScreenshot", test.takeScreenshot("foundation-sable-bodies-after-native-resource-reload").toString());
        world.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 120 0");
        test.waitFor(client -> client.level != null && client.level.dimension().identifier().toString().equals("minecraft:the_nether"), 600); test.waitTicks(8);
        world.getServer().runCommand("execute in minecraft:overworld run tp @a 22 124 24 140 30");
        test.waitFor(client -> client.level != null && client.level.dimension().identifier().toString().equals("minecraft:overworld")
                && rigs.stream().allMatch(r -> SubLevelContainer.getContainer(client.level).getSubLevel(r.ship().getUniqueId()) instanceof ClientSubLevel s && s.isFinalized()), 600);
        test.waitTicks(12); result.put("dimensionReturnScreenshot", test.takeScreenshot("foundation-sable-after-native-dimension-return").toString());
        checks.add("Actual Minecraft resource reload and Overworld/Nether/Overworld player travel retire/rebuild native world render owners and recover all four original ship UUIDs");
        var expected = world.getServer().computeOnServer(server -> snapshot(server, rigs));
        var path = Path.of(System.getProperty("port.smoke.report")).resolveSibling("world-fixture.json");
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(expected)+"\n"); result.put("coldRestartExpectation",path.toString());
        result.put("status","passed");
    }

    private static List<Rig> build(MinecraftServer server) {
        var level=server.overworld();var rigs=new ArrayList<Rig>();
        for (var source:List.of(new BlockPos(15,122,15),new BlockPos(-16,122,15),new BlockPos(31,122,-16),new BlockPos(-31,122,-16))) {
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)level.setBlockAndUpdate(new BlockPos(source.getX()+x,117,source.getZ()+z),Blocks.STONE.defaultBlockState());
            var blocks=new LinkedHashSet<BlockPos>();
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var p=source.offset(x,0,z);level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());blocks.add(p);}
            var chest=source.above();level.setBlockAndUpdate(chest,Blocks.CHEST.defaultBlockState());blocks.add(chest);
            var be=(ChestBlockEntity)level.getBlockEntity(chest);var named=new ItemStack(Items.CHEST);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Foundation chest \u0416 "+source.getX()));be.applyComponentsFromItemStack(named);be.setItem(0,new ItemStack(Items.DIAMOND,7));be.setItem(1,new ItemStack(Items.COAL,11));
            for(var entry:Map.of(source.above().north(),Blocks.OAK_STAIRS.defaultBlockState(),source.above().south(),Blocks.OAK_SLAB.defaultBlockState(),source.above().east(),Blocks.GLASS.defaultBlockState()).entrySet()){
                level.setBlockAndUpdate(entry.getKey(),entry.getValue());blocks.add(entry.getKey());
            }
            var ship=SubLevelAssemblyHelper.assembleBlocks(level,source,blocks,new BoundingBox3i(source.offset(-2,0,-2),source.offset(2,1,2)));
            check(ship!=null,"Native body assembly failed");var positions=new LinkedHashMap<BlockPos,BlockPos>();
            for(var original:blocks){positions.put(original,BlockPos.containing(ship.logicalPose().transformPositionInverse(Vec3.atCenterOf(original))));check(level.getBlockState(original).isAir(),"Assembly duplicated a source block: "+original);}
            var tag=new CompoundTag();tag.putString("FoundationAudit","native \u03bb\u0416");tag.putInt("SourceX",source.getX());ship.setUserDataTag(tag);
            rigs.add(new Rig(ship,source,positions,ship.logicalPose().position().y()));
        }
        return rigs;
    }

    private record DimensionRig(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,UUID id,long slot) {}
    private static void dimensions(ClientGameTestContext test,TestSingleplayerContext world,Map<String,Object> result,List<String> checks){
        result.put("scope","Two actual dimensions contain different native bodies at the same relative plot slot. Genuine vanilla dimension transfers must not apply old-world removals to a new-world body; no packet is synthesized by the driver.");
        world.getServer().runCommand("gamemode spectator @a");world.getServer().runCommand("tp @a 12 124 12");
        var rigs=world.getServer().computeOnServer(server->{
            var rows=new ArrayList<DimensionRig>();
            for(var key:List.of(net.minecraft.world.level.Level.OVERWORLD,net.minecraft.world.level.Level.NETHER)){
                var level=Objects.requireNonNull(server.getLevel(key));var source=new BlockPos(8,122,8);
                for(int x=3;x<=13;x++)for(int z=3;z<=13;z++)level.setBlockAndUpdate(new BlockPos(x,117,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(source,(key.equals(net.minecraft.world.level.Level.OVERWORLD)?Blocks.GOLD_BLOCK:Blocks.DIAMOND_BLOCK).defaultBlockState());
                var ship=SubLevelAssemblyHelper.assembleBlocks(level,source,List.of(source),new BoundingBox3i(source,source));check(ship!=null,"Dimension fixture assembly failed");
                var container=SubLevelContainer.getContainer(level);var origin=container.getOrigin();var plot=ship.getPlot().plotPos;
                rows.add(new DimensionRig(key,ship.getUniqueId(),net.minecraft.world.level.ChunkPos.pack(plot.x()-origin.x,plot.z()-origin.y)));
            }return rows;
        });
        check(rigs.get(0).slot()==rigs.get(1).slot(),"Regression needs overlapping relative plot slots");
        result.put("relativePlotSlot",rigs.getFirst().slot());result.put("bodies",rigs.stream().map(r->Map.of("dimension",r.dimension().identifier().toString(),"uuid",r.id().toString())).toList());
        var samples=new ArrayList<Object>();result.put("transfers",samples);
        test.waitFor(c->SubLevelContainer.getContainer(c.level).getSubLevel(rigs.getFirst().id()) instanceof ClientSubLevel ship&&ship.isFinalized(),400);
        for(int cycle=0;cycle<2;cycle++)for(var rig:List.of(rigs.get(1),rigs.getFirst())){
            world.getServer().runCommand("execute in "+rig.dimension().identifier()+" run tp @a 12 124 12");
            test.waitFor(c->c.level!=null&&c.level.dimension().equals(rig.dimension()),400);
            test.waitTicks(40);
            var row=test.computeOnClient(c->{var container=SubLevelContainer.getContainer(c.level);var ship=container.getSubLevel(rig.id());return Map.of("dimension",c.level.dimension().identifier().toString(),"bodyUuid",rig.id().toString(),"present",ship instanceof ClientSubLevel s&&s.isFinalized(),"clientBodies",container.getAllSubLevels().stream().map(s->s.getUniqueId().toString()).toList());});
            samples.add(row);
            check(Boolean.TRUE.equals(row.get("present")),"Old-world tracking removal erased/missed the current dimension's native body: "+row);
        }
        try(var lines=Files.lines(Path.of(System.getProperty("port.smoke.report")).resolveSibling("runtime.log"))){
            long stale=lines.filter(line->line.contains("Received a sub-level tracking removal packet for unknown sub-level")).count();
            result.put("staleWorldRemovalErrors",stale);check(stale==0,"Old-world removals reached a new native client container: "+stale);
        }catch(java.io.IOException failure){throw new RuntimeException(failure);}
        result.put("screenshot",test.takeScreenshot("foundation-sable-dimension-slot-ownership").toString());result.put("status","passed");
        checks.add("Four actual Nether/Overworld transfers preserve both distinct bodies in matching relative plot slots, with no old-world tracking-removal errors");
    }

    private static void interact(ClientGameTestContext test,TestSingleplayerContext world,Rig rig,Map<String,Object> result,List<String> checks){
        var chest=rig.positions().get(rig.source().above());
        result.put("interactionPhase","teleport-before-chest");result.put("plotChest",chest.toShortString());
        world.getServer().runCommand("gamemode survival @a");world.getServer().runCommand("clear @a");
        world.getServer().runCommand("tp @a 15.5 118 19.0 180 0");
        test.waitFor(c->c.player.distanceToSqr(15.5,118,19)<.1,200);test.waitTicks(3);result.put("interactionPhase","ray-before-chest");lookAtPlot(test,rig.ship().getUniqueId(),Vec3.atCenterOf(chest));
        result.put("clientChest",test.computeOnClient(c->Map.of("state",c.level.getBlockState(chest).toString(),"entity",String.valueOf(c.level.getBlockEntity(chest)),"renderPose",SubLevelContainer.getContainer(c.level).getSubLevel(rig.ship().getUniqueId()).logicalPose().toString())));
        test.waitFor(c->c.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(chest),200);result.put("interactionPhase","native-chest-open");test.getInput().pressKey(o->o.keyUse);
        test.waitFor(c->c.player.containerMenu instanceof ChestMenu,200);test.waitTicks(5);
        result.put("chestScreenshot",test.takeScreenshot("foundation-sable-native-moving-chest-menu").toString());
        clickSlot(test,true,0);test.waitFor(c->c.player.containerMenu.getCarried().is(Items.DIAMOND)&&c.player.containerMenu.getCarried().getCount()==7,200);
        clickSlot(test,false,0);test.waitFor(c->c.player.containerMenu.getCarried().isEmpty(),200);
        await(test,world,s->{var player=s.getPlayerList().getPlayers().getFirst();return player.getInventory().countItem(Items.DIAMOND)==7&&((ChestBlockEntity)s.overworld().getBlockEntity(chest)).getItem(0).isEmpty();},"Native plot chest transfer must conserve exactly seven diamonds");
        test.getInput().pressKey(InputConstants.KEY_ESCAPE);test.waitFor(c->c.gui.screen()==null,200);
        world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.getInventory().setItem(2,new ItemStack(Items.TORCH,2));p.containerMenu.broadcastChanges();});
        test.getInput().pressKey(InputConstants.KEY_3);test.waitFor(c->c.player.getMainHandItem().is(Items.TORCH)&&c.player.getMainHandItem().getCount()==2,200);
        world.getServer().runCommand("tp @a 19.5 118 15.5 90 20");test.waitFor(c->c.player.distanceToSqr(19.5,118,15.5)<.1,200);
        var floor=rig.positions().get(rig.source().east(2));lookAtPlot(test,rig.ship().getUniqueId(),Vec3.atCenterOf(floor).add(0,.499,0));
        test.waitFor(c->c.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(floor)&&hit.getDirection()==Direction.UP,200);test.getInput().pressKey(o->o.keyUse);
        await(test,world,s->s.overworld().getBlockState(floor.above()).is(Blocks.TORCH)&&s.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.TORCH)==1,"Native finite-stock torch placement must reach the translated plot and consume exactly one");
        result.put("nativeInventoryDiamonds",7);result.put("nativePlacedTorch",floor.above().toShortString());
        checks.add("Actual mouse chest open and native container clicks extract seven original diamonds; finite survival torch placement on the naturally translated body consumes exactly one of two items");
    }

    private static List<Object> snapshot(MinecraftServer server,List<Rig> rigs){
        var data=new ArrayList<Object>();
        for(var rig:rigs){var row=new LinkedHashMap<String,Object>();row.put("ship",rig.ship().getUniqueId().toString());row.put("source",rig.source().toShortString());
            var cells=new TreeMap<String,String>();for(var p:rig.positions().values()){cells.put(p.toShortString(),server.overworld().getBlockState(p).toString());var up=p.above();if(server.overworld().getBlockState(up).is(Blocks.TORCH))cells.put(up.toShortString(),server.overworld().getBlockState(up).toString());}
            row.put("states",cells);var chest=(ChestBlockEntity)server.overworld().getBlockEntity(rig.positions().get(rig.source().above()));row.put("diamondCount",chest.getItem(0).getCount());row.put("coalCount",chest.getItem(1).getCount());row.put("chestName",chest.getCustomName().getString());row.put("userData",rig.ship().getUserDataTag().toString());data.add(row);
        }return data;
    }
    private static void restore(ClientGameTestContext test,TestSingleplayerContext world,Map<String,Object> result,List<String> checks)throws Exception{
        var file=Path.of(System.getProperty("foundation.expected"));var expected=JsonParser.parseString(Files.readString(file)).getAsJsonArray();
        test.waitFor(c->SubLevelContainer.getContainer(c.level).getAllSubLevels().size()==expected.size(),600);test.waitTicks(20);
        var accepted=world.getServer().computeOnServer(server->{
            var container=SubLevelContainer.getContainer(server.overworld());check(container.getAllSubLevels().size()==expected.size(),"Cold world reopened a different number of native bodies");int cells=0;
            for(var value:expected){var row=value.getAsJsonObject();var ship=(ServerSubLevel)container.getSubLevel(UUID.fromString(row.get("ship").getAsString()));check(ship!=null,"Saved native body UUID missing");
                var body=RigidBodyHandle.of(ship);check(body!=null&&ship.getMassTracker().getMass()>0,"Cold native body did not restore its live physics handle/mass");
                var velocity=body.getLinearVelocity(new Vector3d());check(Double.isFinite(velocity.length()+ship.logicalPose().position().y()),"Cold native physics produced nonfinite state");
                check(Math.abs(velocity.y)<.2,"Cold restored body did not retain stable contact");
                check(ship.getUserDataTag()!=null&&ship.getUserDataTag().toString().equals(row.get("userData").getAsString()),"Cold world lost custom sublevel data");ChestBlockEntity chest=null;
                for(var entry:row.getAsJsonObject("states").entrySet()){var p=parse(entry.getKey());check(server.overworld().getBlockState(p).toString().equals(entry.getValue().getAsString()),"Cold plot palette changed at "+p);if(server.overworld().getBlockEntity(p)instanceof ChestBlockEntity found)chest=found;cells++;}
                check(chest!=null&&chest.getItem(0).getCount()==row.get("diamondCount").getAsInt()&&chest.getItem(1).getCount()==row.get("coalCount").getAsInt(),"Cold plot chest inventory changed");check(chest.getCustomName().getString().equals(row.get("chestName").getAsString()),"Cold plot chest Unicode name changed");
            }return cells;
        });
        result.put("restoredBodies",expected.size());result.put("restoredBlockStates",accepted);result.put("expectation",file.toString());
        result.put("screenshot",test.takeScreenshot("foundation-sable-real-cold-world-restore").toString());result.put("status","passed");
        checks.add("Separate native Minecraft JVM opens copied saved-world bytes: same four body UUIDs, exact plot block palettes including player-placed torch, native chest contents/Unicode names and custom user data");
    }
    private static void clickSlot(ClientGameTestContext test,boolean chest,int index){
        var point=test.computeOnClient(c->{var screen=(AbstractContainerScreen<?>)c.gui.screen();var menu=c.player.containerMenu;
            var slot=menu.slots.stream().filter(s->(s.container==c.player.getInventory())!=chest&&s.getContainerSlot()==index).findFirst().orElseThrow();
            return new Vec2((integer(screen,"leftPos")+slot.x+8f)*c.getWindow().getScreenWidth()/c.getWindow().getGuiScaledWidth(),(integer(screen,"topPos")+slot.y+8f)*c.getWindow().getScreenHeight()/c.getWindow().getGuiScaledHeight());});
        test.getInput().setCursorPos(point.x,point.y);test.waitTicks(2);test.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }
    private static int integer(Object owner,String name){try{Field f=AbstractContainerScreen.class.getDeclaredField(name);f.setAccessible(true);return f.getInt(owner);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void lookAtPlot(ClientGameTestContext test,UUID id,Vec3 local){
        var angles=test.computeOnClient(c->{var ship=SubLevelContainer.getContainer(c.level).getSubLevel(id);var target=ship.logicalPose().transformPosition(local);var d=target.subtract(c.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));});test.getInput().lookAt(angles.x,angles.y);
    }
    private static BlockPos parse(String text){var p=text.split(", ");return new BlockPos(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));}
    private static void await(ClientGameTestContext test,TestSingleplayerContext world,Predicate<MinecraftServer> condition,String error){for(int i=0;i<200;i++){if(world.getServer().computeOnServer(condition::test))return;test.waitTicks(1);}throw new AssertionError(error);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private FoundationSableChecks(){}
}
