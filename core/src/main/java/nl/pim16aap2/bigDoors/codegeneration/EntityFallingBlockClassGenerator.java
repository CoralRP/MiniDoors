package nl.pim16aap2.bigDoors.codegeneration;

import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatchers;
import nl.pim16aap2.bigDoors.NMS.CustomEntityFallingBlock;
import org.bukkit.World;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;

import static net.bytebuddy.implementation.FixedValue.value;
import static net.bytebuddy.implementation.MethodCall.construct;
import static net.bytebuddy.implementation.MethodCall.invoke;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static nl.pim16aap2.bigDoors.codegeneration.ReflectionRepository.*;

/**
 * Represents an implementation of a {@link ClassGenerator} to generate a subclass of {@link CustomEntityFallingBlock}.
 *
 * @author Pim
 */
final class EntityFallingBlockClassGenerator extends ClassGenerator
{
    private static final @NotNull Class<?>[] CONSTRUCTOR_PARAMETER_TYPES =
        new Class<?>[]{World.class, double.class, double.class, double.class,
                       classIBlockData, asArrayType(classEnumMoveType)};

    private final Field fieldNoClip;
    private final Field fieldHurtEntities;

    public EntityFallingBlockClassGenerator(@NotNull String mappingsVersion)
        throws Exception
    {
        super(mappingsVersion);

        final EntityFallingBlockClassAnalyzer analyzer = new EntityFallingBlockClassAnalyzer();
        fieldHurtEntities = analyzer.getHurtEntitiesField();
        fieldNoClip = analyzer.getNoClipField();

        generate();
    }

    @Override
    protected @NotNull Class<?>[] getConstructorArgumentTypes()
    {
        return CONSTRUCTOR_PARAMETER_TYPES;
    }

    @Override
    protected @NotNull String getBaseName()
    {
        return "EntityFallingBlock";
    }

    @Override
    protected void generateImpl()
    {
        DynamicType.Builder<?> builder = createBuilder(classEntityFallingBlock)
            .implement(CustomEntityFallingBlock.class, IGeneratedFallingBlockEntity.class);

        builder = addFields(builder);
        builder = addCTor(builder);
        builder = addSpawnMethod(builder);
        builder = addHurtEntitiesMethod(builder);
        builder = addGetBlockMethod(builder);
        builder = addLoadDataMethod(builder);
        builder = addSaveDataMethod(builder);
        builder = addAuxiliaryMethods(builder);
        builder = addTickMethod(builder);
        builder = addCrashReportMethod(builder);

        finishBuilder(builder);
    }

    private DynamicType.Builder<?> addFields(DynamicType.Builder<?> builder)
    {
        return builder
            .defineField(fieldTicksLived.getName(), fieldTicksLived.getType(), Visibility.PROTECTED)
            .defineField(fieldHurtEntities.getName(), fieldHurtEntities.getType(), Visibility.PROTECTED)
            .defineField(fieldNoClip.getName(), fieldNoClip.getType(), Visibility.PROTECTED)
            .defineField(fieldTileEntityData.getName(), fieldTileEntityData.getType(), Visibility.PROTECTED)
            .defineField("block", classIBlockData, Visibility.PRIVATE)
            .defineField("bukkitWorld", org.bukkit.World.class, Visibility.PRIVATE)
            .defineField("enumMoveTypeValues", asArrayType(classEnumMoveType), Visibility.PRIVATE);
    }

    private DynamicType.Builder<?> addCTor(DynamicType.Builder<?> builder)
    {
        final MethodCall worldCast = (MethodCall) invoke(methodGetNMSWorld)
            .onArgument(0).withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC);

        return builder
            .defineConstructor(Visibility.PUBLIC)
            .withParameters(getConstructorArgumentTypes())
            .intercept(invoke(cTorNMSFallingBlockEntity).withMethodCall(worldCast).withArgument(1, 2, 3, 4).andThen(
                FieldAccessor.ofField("block").setsArgumentAt(4)).andThen(
                FieldAccessor.ofField("bukkitWorld").setsArgumentAt(0)).andThen(
                FieldAccessor.ofField("enumMoveTypeValues").setsArgumentAt(5)).andThen(
                FieldAccessor.of(fieldTicksLived).setsValue(0)).andThen(
                FieldAccessor.of(fieldHurtEntities).setsValue(false)).andThen(
                FieldAccessor.of(fieldNoClip).setsValue(true)).andThen(
                invoke(methodSetNoGravity).with(true)).andThen(
                invoke(methodSetMot).with(0, 0, 0)).andThen(
                invoke(methodSetStartPos)
                    .withMethodCall(construct(cTorBlockPosition)
                                        .withMethodCall(invoke(methodLocX))
                                        .withMethodCall(invoke(methodLocY))
                                        .withMethodCall(invoke(methodLocZ)))).andThen(
                invoke(named("spawn")))
            );
    }

    private DynamicType.Builder<?> addSpawnMethod(DynamicType.Builder<?> builder)
    {
        return builder
            .defineMethod("spawn", boolean.class, Visibility.PUBLIC)
            .intercept(invoke(methodNMSAddEntity)
                           .onField(fieldNMSWorld)
                           .with(MethodCall.ArgumentLoader.ForThisReference.Factory.INSTANCE)
                           .with(CreatureSpawnEvent.SpawnReason.CUSTOM)
                           .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC)
            );
    }

    private DynamicType.Builder<?> addHurtEntitiesMethod(DynamicType.Builder<?> builder)
    {
        return builder
            .defineMethod(methodHurtEntities.getName(), boolean.class, Visibility.PUBLIC)
            .withParameters(methodHurtEntities.getParameterTypes())
            .intercept(value(false));
    }

    private DynamicType.Builder<?> addCrashReportMethod(DynamicType.Builder<?> builder)
    {
        return builder
            .defineMethod(methodAppendEntityCrashReport.getName(), void.class, Visibility.PUBLIC)
            .withParameters(methodAppendEntityCrashReport.getParameterTypes())
            .intercept(invoke(methodAppendEntityCrashReport).onSuper().withArgument(0).andThen(
                invoke(methodCrashReportAppender)
                    .onArgument(0).with("Animated BigDoors block with state: ").withField("block")));
    }

    private DynamicType.Builder<?> addGetBlockMethod(DynamicType.Builder<?> builder)
    {
        return builder
            .defineMethod(methodGetBlock.getName(), classIBlockData, Visibility.PUBLIC)
            .intercept(FieldAccessor.ofField("block"));
    }

    public interface ILoadDataDelegation
    {
        @RuntimeType
        void intercept(@This IGeneratedFallingBlockEntity baseObject, @RuntimeType Object compound, boolean hasKey);
    }

    private DynamicType.Builder<?> addLoadDataMethod(DynamicType.Builder<?> builder)
    {
        final String tileEntityConditionalName = methodLoadData.getName() + "$conditional";

        builder = builder
            .method(named("generated$loadTileEntityData"))
            .intercept(invoke(methodNBTTagCompoundGetCompound)
                           .onArgument(0).with("TileEntityData").setsField(fieldTileEntityData)
                           .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC));

        builder = builder
            .defineMethod(tileEntityConditionalName, void.class, Visibility.PRIVATE)
            .withParameters(IGeneratedFallingBlockEntity.class, classNBTTagCompound, boolean.class)
            .intercept(MethodDelegation.to((ILoadDataDelegation) (baseObject, compound, hasKey) ->
            {
                if (!hasKey)
                    baseObject.generated$loadTileEntityData(compound);
            }, ILoadDataDelegation.class));

        builder = builder
            .define(methodLoadData)
            .intercept(invoke(methodIBlockDataDeserializer)
                           .withMethodCall(invoke(methodNBTTagCompoundGetCompound)
                                               .onArgument(0).with("BlockState")).setsField(named("block")).andThen(
                    invoke(methodNBTTagCompoundGetInt)
                        .onArgument(0).with("Time").setsField(fieldTicksLived)).andThen(
                    invoke(named(tileEntityConditionalName))
                        .withThis().withArgument(0)
                        .withMethodCall(invoke(methodNBTTagCompoundHasKeyOfType)
                                            .onArgument(0).with("TileEntityData", 10))));

        return builder;
    }

    public interface ISaveDataDelegation
    {
        @RuntimeType
        void intercept(@This IGeneratedFallingBlockEntity baseObject, @RuntimeType Object compound,
                       @RuntimeType Object block);
    }

    private DynamicType.Builder<?> addSaveDataMethod(DynamicType.Builder<?> builder)
    {
        final String tileEntityConditionalName = methodSaveData.getName() + "$conditional";

        builder = builder
            .method(named("generated$saveTileEntityData"))
            .intercept(invoke(methodNBTTagCompoundSet)
                           .onArgument(0).with("TileEntityData").withField(fieldTileEntityData.getName())
                           .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC));

        builder = builder
            .defineMethod(tileEntityConditionalName, void.class, Visibility.PRIVATE)
            .withParameters(IGeneratedFallingBlockEntity.class, classNBTTagCompound, classNBTTagCompound)
            .intercept(MethodDelegation.to((ISaveDataDelegation) (baseObject, compound, block) ->
            {
                if (block != null)
                    baseObject.generated$saveTileEntityData(compound);
            }, ISaveDataDelegation.class));

        return builder
            .define(methodSaveData)
            .intercept(invoke(methodNBTTagCompoundSet)
                           .onArgument(0).with("BlockState")
                           .withMethodCall(invoke(methodIBlockDataSerializer).withField("block")).andThen(
                    invoke(methodNBTTagCompoundSetInt)
                        .onArgument(0).with("Time").withField(fieldTicksLived.getName())).andThen(
                    invoke(methodNBTTagCompoundSetBoolean).onArgument(0).with("DropItem", false)).andThen(
                    invoke(methodNBTTagCompoundSetBoolean)
                        .onArgument(0).with("HurtEntities").withField(fieldHurtEntities.getName())).andThen(
                    invoke(methodNBTTagCompoundSetFloat).onArgument(0).with("FallHurtAmount", 0.0f)).andThen(
                    invoke(methodNBTTagCompoundSetInt).onArgument(0).with("FallHurtMax", 0)).andThen(
                    invoke(named(tileEntityConditionalName)).withThis().withArgument(0)
                                                            .withField(fieldTileEntityData.getName())).andThen(
                    FixedValue.argument(0)));
    }

    public interface IGeneratedFallingBlockEntity
    {
        void generated$die();

        boolean generated$isAir();

        void generated$move();

        int generated$getTicksLived();

        void generated$setTicksLived(int val);

        void generated$updateMot();

        double locY();

        void generated$saveTileEntityData(Object target);

        void generated$loadTileEntityData(Object target);
    }

    public interface IMultiplyVec3D
    {
        @RuntimeType
        Object intercept(@RuntimeType Object vec3d, double x, double y, double z);
    }

    private DynamicType.Builder<?> addAuxiliaryMethods(DynamicType.Builder<?> builder)
    {
        builder = builder
            .defineMethod("generated$multiplyVec", classVec3D, Visibility.PRIVATE)
            .withParameters(classVec3D, double.class, double.class, double.class)
            .intercept(MethodDelegation.to((IMultiplyVec3D) (vec, x, y, z) ->
            {
                try
                {
                    return cTorVec3D.newInstance((double) fieldsVec3D.get(0).get(vec) * x,
                                                 (double) fieldsVec3D.get(1).get(vec) * y,
                                                 (double) fieldsVec3D.get(2).get(vec) * z);
                }
                catch (InstantiationException | IllegalAccessException | InvocationTargetException e)
                {
                    e.printStackTrace();
                    return vec;
                }
            }, IMultiplyVec3D.class));


        builder = builder.method(named("generated$die")).intercept(invoke(named("die")).onSuper());
        builder = builder.method(named("generated$isAir")).intercept(invoke(methodIsAir).onField("block"));
        builder = builder
            .method(named("generated$move").and(ElementMatchers.takesArguments(Collections.emptyList())))
            .intercept(invoke(methodMove)
                           .withMethodCall(invoke(methodArrayGetIdx).withField("enumMoveTypeValues").with(0))
                           .withMethodCall(invoke(methodGetMot))
                           .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC));
        builder = builder.method(named("generated$getTicksLived"))
                         .intercept(FieldAccessor.ofField(fieldTicksLived.getName()));
        builder = builder.method(named("generated$setTicksLived"))
                         .intercept(FieldAccessor.of(fieldTicksLived).setsArgumentAt(0));
        builder = builder
            .method(named("generated$updateMot"))
            .intercept(invoke(methodSetMotVec)
                           .withMethodCall(invoke(named("generated$multiplyVec"))
                                               .withMethodCall(invoke(methodGetMot))
                                               .with(0.9800000190734863D, 1.0D, 0.9800000190734863D)));
        return builder;
    }

    public interface ITickMethodDelegate
    {
        void intercept(IGeneratedFallingBlockEntity entity);
    }

    private DynamicType.Builder<?> addTickMethod(DynamicType.Builder<?> builder)
    {
        builder = builder
            .defineMethod("generated$tick", void.class, Visibility.PRIVATE)
            .withParameters(IGeneratedFallingBlockEntity.class)
            .intercept(MethodDelegation.to((ITickMethodDelegate) entity ->
            {
                if (entity.generated$isAir())
                {
                    entity.generated$die();
                    return;
                }

                entity.generated$move();

                double locY = entity.locY();
                int ticks = entity.generated$getTicksLived() + 1;
                entity.generated$setTicksLived(ticks);

                if (++ticks > 100 && (locY < 1 || locY > 256) || ticks > 12000)
                    entity.generated$die();

                entity.generated$updateMot();
            }, ITickMethodDelegate.class));

        builder = builder
            .define(methodTick).intercept(invoke(named("generated$tick"))
                                              .withThis().withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC));
        return builder;
    }
}