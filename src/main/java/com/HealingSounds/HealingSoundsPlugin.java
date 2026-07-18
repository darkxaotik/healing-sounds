package com.HealingSounds;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.RuneLite;
import net.runelite.api.events.GameTick;
import net.runelite.api.ItemID;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;




@Slf4j
@PluginDescriptor(
	name = "Healing Sounds",
	description = "Plays a sound when you get healed from Sanguinesti Staff or Blood Fury",
	tags = {"sound", "notification", "healing"}
)
public class HealingSoundsPlugin extends Plugin
{
	// Item IDs for the weapons that trigger healing sounds
	private static final int SANGUINESTI_STAFF = ItemID.SANGUINESTI_STAFF;
	private static final int BLOOD_FURY = ItemID.AMULET_OF_BLOOD_FURY;

	@Inject
	private Client client;

	@Inject
	private HealingSoundsConfig config;

	@Inject
	private net.runelite.client.audio.AudioPlayer audioPlayer;

	private int lastHp = 0;
	private int lastInteractingTick = -1;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Healing Sounds plugin started!");
		if (client != null)
		{
			lastHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Healing Sounds plugin stopped!");
		
	}


	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getLocalPlayer() != null && client.getLocalPlayer().getInteracting() != null)
		{
			lastInteractingTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{

		if (statChanged.getSkill() != Skill.HITPOINTS)
		{
			return;
		}

		int currentHp = statChanged.getBoostedLevel();
		int hpGain = currentHp - lastHp;

		if (hpGain > 0 && hpGain >= config.minHealAmount())
		{
			// Ignore heals if we haven't been in combat recently (e.g., within the last 10 ticks)
			// This filters out natural HP regeneration when out of combat.
			if (lastInteractingTick == -1 || client.getTickCount() - lastInteractingTick > 10)
			{
				lastHp = currentHp;
				return;
			}

			boolean shouldPlaySound = false;

			if (config.sanguinestStaff() && isSanguinestEquipped())
			{
				shouldPlaySound = true;
				log.debug("Sanguinesti Staff heal detected: {} HP", hpGain);
			}

			if (config.bloodFury() && isBloodFuryEquipped())
			{
				shouldPlaySound = true;
				log.debug("Blood Fury heal detected: {} HP", hpGain);
			}

			if (shouldPlaySound)
			{
				playHealingSound();
			}
		}

		lastHp = currentHp;
	}

	private boolean isSanguinestEquipped()
	{
		return isWeaponEquipped(SANGUINESTI_STAFF);
	}

	private boolean isBloodFuryEquipped()
	{
		return isWeaponEquipped(BLOOD_FURY);
	}

	private boolean isWeaponEquipped(int itemId)
	{
		try
		{
			var equipment = client.getItemContainer(InventoryID.EQUIPMENT);
			if (equipment == null)
			{
				return false;
			}

			for (Item item : equipment.getItems())
			{
				if (item != null && item.getId() == itemId)
				{
					return true;
				}
			}
			return false;
		}
		catch (Exception e)
		{
			log.debug("Error checking equipped weapon: {}", e.getMessage());
			return false;
		}
	}


	private void playHealingSound()
	{
		if (config.useInGameSound())
		{
			int soundId = config.inGameSoundId();
			if (soundId > 0)
			{
				client.playSoundEffect(soundId);
				log.debug("In-game healing sound played: {}", soundId);
			}
			return;
		}

		try
		{
			// Play the custom healing sound using RuneLite's AudioPlayer
			audioPlayer.play(this.getClass(), "/healing_sound.wav", 1.0f);
			log.debug("Healing sound played via AudioPlayer");
		}
		catch (Exception e)
		{
			log.debug("Error playing healing sound: {}", e.getMessage());
		}
	}

	@Provides
	HealingSoundsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HealingSoundsConfig.class);
	}
}
