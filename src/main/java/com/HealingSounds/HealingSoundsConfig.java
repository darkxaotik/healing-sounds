package com.HealingSounds;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("healingsounds")
public interface HealingSoundsConfig extends Config
{
	@ConfigItem(
			keyName = "sanguinestStaff",
			name = "Sanguinesti Staff",
			description = "Play sound for Sanguinesti Staff heals",
			position = 1
	)
	default boolean sanguinestStaff()
	{
		return true;
	}

	@ConfigItem(
			keyName = "bloodFury",
			name = "Blood Fury",
			description = "Play sound for Blood Fury heals",
			position = 2
	)
	default boolean bloodFury()
	{
		return true;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "soundVolume",
		name = "Sound Volume",
		description = "Volume of the healing sound (0-100)",
		position = 3
	)
	default int soundVolume()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "useInGameSound",
		name = "Use In-Game Sound",
		description = "Check this to use an in-game sound effect (Disables custom sound if checked)",
		position = 4
	)
	default boolean useInGameSound()
	{
		return false;
	}

	@ConfigItem(
		keyName = "inGameSoundId",
		name = "In-game Sound ID",
		description = "If 'In-Game Sound' is selected, plays this sound effect ID (e.g. 166)",
		position = 5
	)
	default int inGameSoundId()
	{
		return 166;
	}

	@Range(min = 2, max = 99)
	@ConfigItem(
		keyName = "minHealAmount",
		name = "Minimum Heal Amount",
		description = "Only play sound for heals of this amount or greater",
		position = 6
	)
	default int minHealAmount()
	{
		return 2;
	}

}
