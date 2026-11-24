package net.minecraft.src;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;

/**
 * Jump Delay: "Off" clears the player's private jump cooldown every tick, so holding jump jumps again
 * as soon as the player lands.
 */
public class KirModuleJumpDelay extends KirModule
{
    private static final String[] VALUES = new String[] {"Default", "Off"};

    private static final Field fieldJumpTicks = KirUtil.findField(EntityLiving.class, "jumpTicks", "bE");

    public KirModuleJumpDelay()
    {
        super("jumpDelay", "Jump Delay", "Jump Delay", 0, "jumpdelay");
    }

    public String getCategory()
    {
        return CATEGORY_GAMEPLAY;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "After a jump, vanilla waits 10 ticks (half a second) before you can jump again, which slows down holding jump under low ceilings or up a staircase of blocks.",
            describeValue("Default", "vanilla jump delay."),
            describeValue("Off", "holding jump jumps again the moment you land.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isAvailable()
    {
        return fieldJumpTicks != null;
    }

    public void update(Minecraft mc)
    {
        if (this.getValue() == 1 && mc.thePlayer != null && fieldJumpTicks != null)
        {
            try
            {
                fieldJumpTicks.setInt(mc.thePlayer, 0);
            }
            catch (IllegalAccessException var3)
            {
                KirUtil.log("Could not change the jump delay", var3);
            }
        }
    }
}
