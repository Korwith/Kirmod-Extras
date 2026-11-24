package net.minecraft.src;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;

/**
 * Mining Delay (from ClickMiningFix): after a block breaks, vanilla's player controller waits 5 ticks
 * (blockHitDelay) before it starts on the next one. Every frame, a delay above the chosen value is
 * lowered to it, so the next block starts after 2, 1 or 0 ticks. ClickMiningFix did the same by
 * replacing PlayerControllerMP, which took Forge's copy of that class out of the game.
 */
public class KirModuleMiningDelay extends KirModule
{
    private static final String[] VALUES = new String[] {"Default", "2 ticks", "1 tick", "0 ticks"};
    private static final int[] DELAYS = new int[] {5, 2, 1, 0};

    private static final Field fieldBlockHitDelay = KirUtil.findField(PlayerControllerMP.class, "blockHitDelay", "i");

    public KirModuleMiningDelay()
    {
        super("miningDelay", "Mining Delay", "Mining Delay", 0, "miningdelay");
    }

    public String getCategory()
    {
        return CATEGORY_GAMEPLAY;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "After a block breaks, vanilla waits 5 ticks (a quarter of a second) before it starts mining the next one while you hold the button.",
            describeValue("Default", "vanilla, 5 ticks."),
            describeValue("2 ticks", "the next block starts after 2 ticks."),
            describeValue("1 tick", "after 1 tick."),
            describeValue("0 ticks", "straight away, like ClickMiningFix (0 Ticks)."),
            note("Works on servers too, as the delay is the client's.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isAvailable()
    {
        return fieldBlockHitDelay != null;
    }

    public void update(Minecraft mc)
    {
        if (this.getValue() > 0 && mc.playerController != null && fieldBlockHitDelay != null)
        {
            int var2 = DELAYS[this.getValue()];

            if (KirUtil.getInt(fieldBlockHitDelay, mc.playerController, 0) > var2)
            {
                KirUtil.setInt(fieldBlockHitDelay, mc.playerController, var2);
            }
        }
    }
}
