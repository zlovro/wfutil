package io.github.zlovro.wfutil.registry;

import io.github.zlovro.wfutil.WFMod;
import io.github.zlovro.wfutil.entities.SoldierEntity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.w3c.dom.Attr;

public class WFEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITIES, WFMod.MODID);

    public static final RegistryObject<EntityType<SoldierEntity>> SOLDIER_ENTITY_TYPE = ENTITY_TYPES.register("soldier", () -> EntityType.Builder.create(SoldierEntity::factory, EntityClassification.MISC).size(0.6F, 1.8F).trackingRange(1024).updateInterval(2).build("soldier"));

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }

    public static void entityAttributeCreateEvent(EntityAttributeCreationEvent event) {
        event.put(SOLDIER_ENTITY_TYPE.get(), SoldierEntity.getAttributes());
    }
}
