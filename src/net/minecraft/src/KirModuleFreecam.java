package net.minecraft.src;

import net.minecraft.client.Minecraft;

/**
 * Freecam: "On" detaches the camera from the player. The player stays exactly where it is (and is drawn
 * there), while the camera flies with the movement keys through blocks, the ground and the sky, at
 * Freecam Speed. See KirCamera.
 *
 * The Freecam key (Y by default, in Controls) switches it too. It always starts off, and turns itself
 * off when you change worlds or respawn.
 */
public class KirModuleFreecam extends KirModule
{
    private static final String[] VALUES = new String[] {"Off", "On"};

    public KirModuleFreecam()
    {
        super("freecam", "Freecam", "Freecam", 0, "freecam", "fc");
    }

    public String getCategory()
    {
        return KirModuleIsometric.CATEGORY_CAMERA;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Lets the camera fly away from you: through blocks, under the ground, up into the sky. You stay where you are.",
            describeValue("Off", "the camera is your eyes."),
            describeValue("On", "the movement keys fly the camera; jump goes up and sneak goes down. You can't break or use blocks while it is on."),
            note("The Freecam key (Y, see Controls) switches it on and off. Only chunks near you are loaded.")
        };
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    /** Freecam is never on after a restart. */
    public void install()
    {
        this.loadValue(0);
    }

    public void update(Minecraft mc)
    {
        if (KirCamera.updateFreecam(mc, this.getValue() == 1))
        {
            // A new world or a respawn ended it.
            this.setValue(0);
        }
    }
}
