package net.minecraft.src;

/**
 * Freecam Speed: a slider from 1 to 30 blocks per second (10 by default). The camera always moves at
 * exactly that speed, with no speeding up or slowing down. See KirCamera.
 */
public class KirModuleFreecamSpeed extends KirModule
{
    public static final int MIN_SPEED = 1;
    public static final int MAX_SPEED = 30;
    public static final int DEFAULT_SPEED = 10;

    /** "1" to "30": value i is i + 1 blocks per second. */
    private static final String[] VALUES = new String[MAX_SPEED - MIN_SPEED + 1];

    static
    {
        for (int var0 = 0; var0 < VALUES.length; ++var0)
        {
            VALUES[var0] = String.valueOf(MIN_SPEED + var0);
        }
    }

    public KirModuleFreecamSpeed()
    {
        super("freecamSpeed", "Freecam Speed", "Freecam Speed", DEFAULT_SPEED - MIN_SPEED, "freecamspeed", "fcspeed");
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public boolean isSlider()
    {
        return true;
    }

    public String getCategory()
    {
        return KirModuleIsometric.CATEGORY_CAMERA;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "How fast the Freecam camera flies. It always moves at exactly this speed, in every direction.",
            describeValue(MIN_SPEED + " to " + MAX_SPEED, "blocks per second. The default is " + DEFAULT_SPEED + ".")
        };
    }

    public String getButtonString()
    {
        return super.getButtonString() + " blocks/s";
    }

    public void install()
    {
        KirCamera.setFreecamSpeed(this.getSpeed());
    }

    protected void onValueChanged()
    {
        KirCamera.setFreecamSpeed(this.getSpeed());
    }

    /** The speed in blocks per second. */
    public int getSpeed()
    {
        return MIN_SPEED + this.getValue();
    }
}
