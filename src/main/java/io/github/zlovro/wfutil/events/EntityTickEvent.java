package io.github.zlovro.wfutil.events;

import net.minecraft.entity.Entity;
import net.minecraftforge.event.entity.EntityEvent;

public class EntityTickEvent extends EntityEvent {
    public EntityTickEvent(Entity entity) {
        super(entity);
    }
}
