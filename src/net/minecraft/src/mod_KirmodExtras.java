package net.minecraft.src;

/**
 * Kirmod Extras: the first Kirmod plugin (see KirPlugin). Gameplay features that used to be part of
 * Kirmod itself, in their own "Kirmod Extras" section of the settings screen's sidebar:
 *
 *   - Gameplay: Item Magnet, Item Magnet Range, Mining Delay, Fullbright; World: Tripwire Transmutation,
 *     Beta Mob Wandering, Crop Catch-Up; Mob Spawning: Passive Spawning, Passive Mob Cap, Passive Spawn Ground
 *   - Minecarts: Minecart Speed, Minecart Speed Limit
 *   - Camera: Freecam, Freecam Speed
 *
 * Needs Kirmod 1.33 or later (1.33 moved Kirmod's own Gameplay tab here). The settings keep their Kirmod.cfg keys, commands and keys (Freecam Y,
 * Fullbright U, Item Magnet L), so moving from Kirmod 1.25 keeps every value. Without this mod those
 * features are simply missing, and their values stay in Kirmod.cfg for when it comes back.
 */
public class mod_KirmodExtras extends BaseMod
{
    /** Extras' own tabs; Gameplay (and World under it) and Camera are the names Kirmod uses too. */
    public static final String CATEGORY_MINECARTS = "Minecarts";

    /** Under Gameplay, like World: how animals spawn. */
    public static final String CATEGORY_MOB_SPAWNING = "Mob Spawning";

    public static final KirPlugin PLUGIN = new KirPlugin("Kirmod Extras", "1.4", "Gameplay extras for Kirmod: Item Magnet, Mining Delay, Fullbright, Tripwire Transmutation, Beta Mob Wandering, Crop Catch-Up, Passive Spawning, Minecart Speed and Freecam.", 266)
    {
        public String getCategoryDescription(String category)
        {
            return KirModule.CATEGORY_GAMEPLAY.equals(category) ? "Item pickup, mining, lighting and the tripwire bug." : (CATEGORY_MINECARTS.equals(category) ? "Faster minecarts on rails." : (KirModuleIsometric.CATEGORY_CAMERA.equals(category) ? "Freecam: fly the camera away from the player." : (KirModule.CATEGORY_WORLD.equals(category) ? "World rules: the tripwire transmutation bug, Beta's far mob wandering and crops growing while you are away." : (CATEGORY_MOB_SPAWNING.equals(category) ? "How animals spawn: like hostile mobs, at any light level, with a higher cap." : null))));
        }

        public String getParentCategory(String category)
        {
            return CATEGORY_MOB_SPAWNING.equals(category) ? KirModule.CATEGORY_GAMEPLAY : super.getParentCategory(category);
        }
    };

    public static final KirModuleJumpDelay JUMP_DELAY = new KirModuleJumpDelay();
    public static final KirModuleVerticalLogs VERTICAL_LOGS = new KirModuleVerticalLogs();
    public static final KirModuleSneakPlace SNEAK_PLACE = new KirModuleSneakPlace();
    public static final KirModuleBridgeAssist BRIDGE_ASSIST = new KirModuleBridgeAssist();
    public static final KirModuleXpPickup XP_PICKUP = new KirModuleXpPickup();
    public static final KirModuleMobGriefing MOB_GRIEFING = new KirModuleMobGriefing();
    public static final KirModuleStackSizes STACK_SIZES = new KirModuleStackSizes();
    public static final KirModuleExtraRecipes EXTRA_RECIPES = new KirModuleExtraRecipes();
    public static final KirModuleFishing FISHING = new KirModuleFishing();
    public static final KirModuleAutoTrader AUTO_TRADER = new KirModuleAutoTrader();
    public static final KirModulePaintingPicker PAINTING_PICKER = new KirModulePaintingPicker();
    public static final KirModuleCraftingGridStorage CRAFTING_GRID_STORAGE = new KirModuleCraftingGridStorage();
    public static final KirModuleLavaDecay LAVA_DECAY = new KirModuleLavaDecay();
    public static final KirModuleInfiniteWater INFINITE_WATER = new KirModuleInfiniteWater();
    public static final KirModuleItemMagnet ITEM_MAGNET = new KirModuleItemMagnet();
    public static final KirModuleItemMagnetRange ITEM_MAGNET_RANGE = new KirModuleItemMagnetRange();
    public static final KirModuleMiningDelay MINING_DELAY = new KirModuleMiningDelay();
    public static final KirModuleFullbright FULLBRIGHT = new KirModuleFullbright();
    public static final KirModuleTripwireTransmutation TRIPWIRE_TRANSMUTATION = new KirModuleTripwireTransmutation();
    public static final KirModuleBetaMobWandering BETA_MOB_WANDERING = new KirModuleBetaMobWandering();
    public static final KirModuleCropCatchUp CROP_CATCH_UP = new KirModuleCropCatchUp();
    public static final KirModulePassiveSpawning PASSIVE_SPAWNING = new KirModulePassiveSpawning();
    public static final KirModulePassiveMobCap PASSIVE_MOB_CAP = new KirModulePassiveMobCap();
    public static final KirModulePassiveSpawnGround PASSIVE_SPAWN_GROUND = new KirModulePassiveSpawnGround();
    public static final KirModuleMinecartSpeed MINECART_SPEED = new KirModuleMinecartSpeed();
    public static final KirModuleMinecartSpeedLimit MINECART_SPEED_LIMIT = new KirModuleMinecartSpeedLimit();
    public static final KirModuleFreecam FREECAM = new KirModuleFreecam();
    public static final KirModuleFreecamSpeed FREECAM_SPEED = new KirModuleFreecamSpeed();

    public String getVersion()
    {
        return PLUGIN.getVersion() + " for 1.3.2";
    }

    public String getPriorities()
    {
        return "after:mod_Kirmod";
    }

    public void load()
    {
        // Registration order is the order of the tabs and rows.
        mod_Kirmod.registerPlugin(PLUGIN);
        mod_Kirmod.registerModule(PLUGIN, JUMP_DELAY);
        mod_Kirmod.registerModule(PLUGIN, VERTICAL_LOGS);
        mod_Kirmod.registerModule(PLUGIN, SNEAK_PLACE);
        mod_Kirmod.registerModule(PLUGIN, BRIDGE_ASSIST);
        mod_Kirmod.registerModule(PLUGIN, XP_PICKUP);
        mod_Kirmod.registerModule(PLUGIN, MOB_GRIEFING);
        mod_Kirmod.registerModule(PLUGIN, ITEM_MAGNET);
        mod_Kirmod.registerModule(PLUGIN, ITEM_MAGNET_RANGE);
        mod_Kirmod.registerModule(PLUGIN, MINING_DELAY);
        mod_Kirmod.registerModule(PLUGIN, FULLBRIGHT);
        mod_Kirmod.registerModule(PLUGIN, STACK_SIZES);
        mod_Kirmod.registerModule(PLUGIN, EXTRA_RECIPES);
        mod_Kirmod.registerModule(PLUGIN, FISHING);
        mod_Kirmod.registerModule(PLUGIN, AUTO_TRADER);
        mod_Kirmod.registerModule(PLUGIN, PAINTING_PICKER);
        mod_Kirmod.registerModule(PLUGIN, CRAFTING_GRID_STORAGE);
        mod_Kirmod.registerModule(PLUGIN, LAVA_DECAY);
        mod_Kirmod.registerModule(PLUGIN, INFINITE_WATER);
        mod_Kirmod.registerModule(PLUGIN, TRIPWIRE_TRANSMUTATION);
        mod_Kirmod.registerModule(PLUGIN, BETA_MOB_WANDERING);
        mod_Kirmod.registerModule(PLUGIN, CROP_CATCH_UP);
        mod_Kirmod.registerModule(PLUGIN, PASSIVE_SPAWNING);
        mod_Kirmod.registerModule(PLUGIN, PASSIVE_MOB_CAP);
        mod_Kirmod.registerModule(PLUGIN, PASSIVE_SPAWN_GROUND);
        mod_Kirmod.registerModule(PLUGIN, MINECART_SPEED);
        mod_Kirmod.registerModule(PLUGIN, MINECART_SPEED_LIMIT);
        mod_Kirmod.registerModule(PLUGIN, FREECAM);
        mod_Kirmod.registerModule(PLUGIN, FREECAM_SPEED);
        mod_Kirmod.registerModuleKey(new KeyBinding("Freecam", 21), FREECAM);
        mod_Kirmod.registerModuleKey(new KeyBinding("Fullbright", 22), FULLBRIGHT);
        mod_Kirmod.registerModuleKey(new KeyBinding("Item Magnet", 38), ITEM_MAGNET);
        mod_Kirmod.registerModuleKey(new KeyBinding("Instant XP Pickup", 37), XP_PICKUP);

        if (AUTO_TRADER.isEnabled())
        {
            // Read by Auto Trader in the trading screen, not through keyboardEvent.
            ModLoader.registerKey(this, KirModuleAutoTrader.KEY, false);
            mod_Kirmod.registerPluginKey(PLUGIN, (String)null, KirModuleAutoTrader.KEY, "In a villager's trading screen, makes the trade shown as many times as your items allow.");
        }
    }
}
