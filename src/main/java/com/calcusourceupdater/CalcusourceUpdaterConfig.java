package com.calcusourceupdater;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("calcusourceupdater")
public interface CalcusourceUpdaterConfig extends Config
{
    @Range(min = 0)
    @ConfigItem(
            keyName = "minimumUpdatableXP",
            name = "Minimum Updatable XP",
            description = "Minimum xp required to be gained in order to send an update to calcusource"
    )
    default int minimumUpdatableXP()
    {
        return 10000;
    }
}
