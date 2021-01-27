package com.alc.moreminecarts.items;

import com.alc.moreminecarts.entities.CampfireCartEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.registries.ObjectHolder;

@ObjectHolder("moreminecarts")
public class CampfireCartItem extends AbstractMinecartItem {

    public static final EntityType<CampfireCartEntity> campfire_cart = null;

    public CampfireCartItem(Properties builder) {
        super(builder);
    }

    @Override
    void createMinecart(ItemStack stack, World world, double posX, double posY, double posZ) {

        CampfireCartEntity minecart = new CampfireCartEntity(campfire_cart, world, posX, posY, posZ);
        if (stack.hasDisplayName()) {
            minecart.setCustomName(stack.getDisplayName());
        }
        world.addEntity(minecart);
    }
}