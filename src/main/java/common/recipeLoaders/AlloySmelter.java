package common.recipeLoaders;

import common.items.MetaItem_CraftingComponent;
import cpw.mods.fml.common.Loader;
import gregtech.api.enums.GT_Values;
import gregtech.api.enums.ItemList;
import kekztech.Items;

public class AlloySmelter implements Runnable{
    @Override
    public void run() {
        final MetaItem_CraftingComponent craftingItem = MetaItem_CraftingComponent.getInstance();


        // YSZ Cermic Plate
        GT_Values.RA.addAlloySmelterRecipe(
            craftingItem.getStackOfAmountFromDamage(
                Items.YSZCeramicDust.getMetaID(),
                Loader.isModLoaded("bartworks") ? 3 : 10),
            ItemList.Shape_Mold_Plate.get(0),
            craftingItem.getStackOfAmountFromDamage(Items.YSZCeramicPlate.getMetaID(), 1),
            400,
            480);
    }
}
