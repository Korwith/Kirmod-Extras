package net.minecraft.src;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Extra Recipes: the recipes of PaperMaster, MoreCraftables and PotionMaster, plus wool dyeing in any
 * color, switched on and off at runtime.
 *
 * The IRecipe objects are made once and added to or removed from CraftingManager.getRecipeList(); the
 * furnace recipes are put into or taken out of FurnaceRecipes' map. Both are shared with the integrated
 * server, so this works in singleplayer and on a LAN world you host; a remote server only crafts its own
 * recipes.
 */
public class KirModuleExtraRecipes extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};

    private final List recipes = new ArrayList();

    /** Furnace input block/item ID to output, for the smelting recipes. */
    private final Map smelting = new HashMap();

    private boolean added = false;

    public KirModuleExtraRecipes()
    {
        super("extraRecipes", "Extra Recipes", "Extra Recipes", 0, "extrarecipes");
    }

    public String getCategory()
    {
        return CATEGORY_ITEMS;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Adds the crafting recipes of PaperMaster, MoreCraftables and PotionMaster.",
            describeValue("Off", "vanilla recipes only."),
            describeValue("On", "9 sugar canes make 9 paper; seeds on dirt make grass; planks on a sticky piston unsticks it; mossy stone bricks from vines; nether bricks from netherrack; two slabs make their full block (and wooden slabs give planks back); stone bricks smelt to cracked ones and double stone slabs to smooth stone; potions can be brewed in the crafting grid."),
            describeValue("On", "wool: any color + a dye re-dyes it (bone meal turns it white again), and any color of wool gives 4 string."),
            note("Works in singleplayer and on a LAN world you host.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public void install()
    {
        this.makeRecipes();
        this.apply();
    }

    protected void onValueChanged()
    {
        this.apply();
    }

    private void apply()
    {
        boolean var1 = this.getValue() == 1;

        if (var1 == this.added)
        {
            return;
        }

        List var2 = CraftingManager.getInstance().getRecipeList();
        Map var3 = FurnaceRecipes.smelting().getSmeltingList();

        if (var1)
        {
            var2.addAll(this.recipes);
            Iterator var4 = this.smelting.entrySet().iterator();

            while (var4.hasNext())
            {
                Map.Entry var5 = (Map.Entry)var4.next();

                // Never replace a furnace recipe another mod (or a later vanilla) already has.
                if (!var3.containsKey(var5.getKey()))
                {
                    var3.put(var5.getKey(), var5.getValue());
                }
            }
        }
        else
        {
            var2.removeAll(this.recipes);
            Iterator var6 = this.smelting.entrySet().iterator();

            while (var6.hasNext())
            {
                Map.Entry var7 = (Map.Entry)var6.next();

                if (var3.get(var7.getKey()) == var7.getValue())
                {
                    var3.remove(var7.getKey());
                }
            }
        }

        this.added = var1;
    }

    private void makeRecipes()
    {
        // PaperMaster: a full grid of sugar canes gives 9 paper (vanilla's row of 3 gives 3).
        this.shaped(new ItemStack(Item.paper, 9), "###", "###", "###", '#', Item.reed);

        // MoreCraftables.
        this.shaped(new ItemStack(Block.grass, 1), "S", "D", 'S', Item.seeds, 'D', Block.dirt);
        this.shaped(new ItemStack(Block.pistonBase, 1), "W", "P", 'W', Block.planks, 'P', Block.pistonStickyBase);
        this.shapeless(new ItemStack(Block.stoneBrick, 1, 1), new ItemStack(Block.stoneBrick, 1, 0), Block.vine);
        this.shaped(new ItemStack(Block.netherBrick, 1), "NN", "NN", 'N', Block.netherrack);
        this.smelting.put(Integer.valueOf(Block.stoneBrick.blockID), new ItemStack(Block.stoneBrick, 1, 2));
        this.smelting.put(Integer.valueOf(Block.stoneDoubleSlab.blockID), new ItemStack(Block.stoneDoubleSlab, 1, 6));
        int[] var1 = new int[] {0, 1, 3, 4, 5, 6};

        for (int var2 = 0; var2 < var1.length; ++var2)
        {
            this.shaped(new ItemStack(Block.stoneSingleSlab, 2, 2), "W", "S", 'W', new ItemStack(Block.woodSingleSlab, 1, 0), 'S', new ItemStack(Block.stoneSingleSlab, 1, var1[var2]));
        }

        this.shaped(new ItemStack(Block.stoneDoubleSlab, 1, 2), "S", "S", 'S', new ItemStack(Block.stoneSingleSlab, 1, 2));
        this.shaped(new ItemStack(Block.stoneDoubleSlab, 1, 6), "S", "S", 'S', new ItemStack(Block.stoneSingleSlab, 1, 6));
        this.shaped(new ItemStack(Block.stoneDoubleSlab, 1, 0), "S", "S", 'S', new ItemStack(Block.stoneSingleSlab, 1, 0));
        this.shaped(new ItemStack(Block.sandStone, 1, 0), "S", "S", 'S', new ItemStack(Block.stoneSingleSlab, 1, 1));
        this.shaped(new ItemStack(Block.cobblestone, 1), "S", "S", 'S', new ItemStack(Block.stoneSingleSlab, 1, 3));
        this.shaped(new ItemStack(Block.brick, 1), "S", "S", 'S', new ItemStack(Block.stoneSingleSlab, 1, 4));
        this.shaped(new ItemStack(Block.stoneBrick, 1, 0), "S", "S", 'S', new ItemStack(Block.stoneSingleSlab, 1, 5));
        this.shapeless(new ItemStack(Block.stoneSingleSlab, 2, 6), new ItemStack(Block.stoneDoubleSlab, 1, 6));

        for (int var3 = 0; var3 < 4; ++var3)
        {
            this.shaped(new ItemStack(Block.planks, 1, var3), "W", "W", 'W', new ItemStack(Block.woodSingleSlab, 1, var3));
        }

        // Wool, in every color: MoreCraftables only did white wool to string and wool + bone meal.
        ItemStack var4 = new ItemStack(Block.cloth, 1, -1);
        this.shapeless(new ItemStack(Item.silk, 4), var4);

        for (int var5 = 0; var5 < 16; ++var5)
        {
            // Dye damage d gives wool damage 15 - d; bone meal (15) gives white (0), like vanilla's dyeing
            // of white wool, which this extends to wool of any color.
            this.shapeless(new ItemStack(Block.cloth, 1, BlockCloth.getDyeFromBlock(var5)), var4, new ItemStack(Item.dyePowder, 1, var5));
        }

        // PotionMaster: brewing in the crafting grid.
        ItemStack var6 = new ItemStack(Item.potion, 1, 0);
        ItemStack var7 = new ItemStack(Item.potion, 1, 16);
        ItemStack var8 = new ItemStack(Item.potion, 1, 32);
        ItemStack var9 = new ItemStack(Item.potion, 1, 64);
        ItemStack var10 = new ItemStack(Item.potion, 1, 8195);
        ItemStack var11 = new ItemStack(Item.potion, 1, 8193);
        ItemStack var12 = new ItemStack(Item.potion, 1, 8194);
        ItemStack var13 = new ItemStack(Item.potion, 1, 8201);
        ItemStack var14 = new ItemStack(Item.potion, 1, 8199);
        ItemStack var15 = new ItemStack(Item.potion, 1, 8196);
        this.shapeless(var7, var6, Item.netherStalkSeeds);
        this.shapeless(var8, var6, Item.lightStoneDust);
        this.shapeless(var9, var6, Item.spiderEye);
        this.shapeless(var9, var6, Item.speckledMelon);
        this.shapeless(var9, var6, Item.blazePowder);
        this.shapeless(var9, var6, Item.redstone);
        this.shapeless(var9, var6, Item.sugar);
        this.shapeless(var10, var7, Item.magmaCream);
        this.shapeless(var11, var7, Item.speckledMelon);
        this.shapeless(var12, var7, Item.ghastTear);
        this.shapeless(var13, var7, Item.blazePowder);
        this.shapeless(var14, var7, Item.sugar);
        this.shapeless(var15, var7, Item.spiderEye);
        this.shapeless(new ItemStack(Item.potion, 1, 16387), var10, Item.gunpowder);
        this.shapeless(new ItemStack(Item.potion, 1, 16385), var11, Item.gunpowder);
        this.shapeless(new ItemStack(Item.potion, 1, 16386), var12, Item.gunpowder);
        this.shapeless(new ItemStack(Item.potion, 1, 16393), var13, Item.gunpowder);
        this.shapeless(new ItemStack(Item.potion, 1, 16391), var14, Item.gunpowder);
        this.shapeless(new ItemStack(Item.potion, 1, 16388), var15, Item.gunpowder);
    }

    /** An ingredient the way CraftingManager reads one: a Block means any damage value. */
    private static ItemStack ingredient(Object o, boolean shaped)
    {
        if (o instanceof ItemStack)
        {
            return ((ItemStack)o).copy();
        }
        else if (o instanceof Item)
        {
            return new ItemStack((Item)o);
        }
        else if (o instanceof Block)
        {
            return shaped ? new ItemStack((Block)o, 1, -1) : new ItemStack((Block)o);
        }

        throw new IllegalArgumentException("Not an ingredient: " + o);
    }

    /** Like CraftingManager.addRecipe: pattern rows, then pairs of character and ingredient. */
    private void shaped(ItemStack output, Object ... args)
    {
        StringBuilder var3 = new StringBuilder();
        int var4 = 0;
        int var5 = 0;
        int var6;

        for (var6 = 0; args[var6] instanceof String; ++var6)
        {
            String var7 = (String)args[var6];
            var4 = var7.length();
            ++var5;
            var3.append(var7);
        }

        HashMap var8 = new HashMap();

        for (; var6 < args.length; var6 += 2)
        {
            var8.put(args[var6], ingredient(args[var6 + 1], true));
        }

        ItemStack[] var9 = new ItemStack[var4 * var5];

        for (int var10 = 0; var10 < var9.length; ++var10)
        {
            ItemStack var11 = (ItemStack)var8.get(Character.valueOf(var3.charAt(var10)));
            var9[var10] = var11 != null ? var11.copy() : null;
        }

        this.recipes.add(new ShapedRecipes(var4, var5, var9, output));
    }

    /** Like CraftingManager.addShapelessRecipe. */
    private void shapeless(ItemStack output, Object ... args)
    {
        ArrayList var3 = new ArrayList();

        for (int var4 = 0; var4 < args.length; ++var4)
        {
            var3.add(ingredient(args[var4], false));
        }

        this.recipes.add(new ShapelessRecipes(output, var3));
    }
}
