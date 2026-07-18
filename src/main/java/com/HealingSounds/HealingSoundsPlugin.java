package com.HealingSounds;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
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

	/**
	 * Number of ticks after an eating/drinking animation during which
	 * HP increases are assumed to come from food, not passive heals.
	 */
	private static final int CONSUMING_SUPPRESSION_TICKS = 3;

	@Inject
	private Client client;

	@Inject
	private HealingSoundsConfig config;

	@Inject
	private net.runelite.client.audio.AudioPlayer audioPlayer;

	private int lastHp = 0;
	private int lastInteractingTick = -1;

	/**
	 * The tick on which the player last performed the CONSUMING (eating/drinking) animation.
	 * Used to suppress heals that come from food or potions.
	 */
	private int lastConsumingAnimTick = -1;

	/**
	 * The tick on which the player's inventory last changed.
	 * Eating/drinking removes items from the inventory; passive heals do not.
	 */
	private int lastInventoryChangeTick = -1;

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
		lastConsumingAnimTick = -1;
		lastInventoryChangeTick = -1;
		lastInteractingTick = -1;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (actor != client.getLocalPlayer())
		{
			return;
		}

		if (actor.getAnimation() == AnimationID.CONSUMING)
		{
			lastConsumingAnimTick = client.getTickCount();
			log.debug("Consuming animation detected on tick {}", lastConsumingAnimTick);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INVENTORY.getId())
		{
			lastInventoryChangeTick = client.getTickCount();
		}
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
			// Ignore heals if we haven't been in combat recently (within the last 10 ticks).
			// This filters out natural HP regeneration when out of combat.
			if (lastInteractingTick == -1 || client.getTickCount() - lastInteractingTick > 10)
			{
				lastHp = currentHp;
				return;
			}

			// Filter out eating/drinking heals:
			// If the player recently performed the CONSUMING animation, the heal is from food/potions.
			int currentTick = client.getTickCount();
			if (lastConsumingAnimTick != -1 && currentTick - lastConsumingAnimTick <= CONSUMING_SUPPRESSION_TICKS)
			{
				log.debug("HP gain of {} suppressed — player ate/drank recently (anim tick {}, current tick {})",
					hpGain, lastConsumingAnimTick, currentTick);
				lastHp = currentHp;
				return;
			}

			// Secondary filter: if the inventory changed on this tick, the heal likely came
			// from consuming an item (food, potion, etc.), not a passive weapon/amulet effect.
			if (lastInventoryChangeTick == currentTick)
			{
				log.debug("HP gain of {} suppressed — inventory changed on same tick {}", hpGain, currentTick);
				lastHp = currentHp;
				return;
			}

			boolean shouldPlaySound = false;

			if (config.sanguinestStaff() && isSanguinestEquipped())
			{
				shouldPlaySound = true;
				log.debug("Sanguinesti Staff passive heal detected: {} HP", hpGain);
			}

			if (config.bloodFury() && isBloodFuryEquipped())
			{
				shouldPlaySound = true;
				log.debug("Blood Fury passive heal detected: {} HP", hpGain);
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
		return isItemEquipped(SANGUINESTI_STAFF);
	}

	private boolean isBloodFuryEquipped()
	{
		return isItemEquipped(BLOOD_FURY);
	}

	private boolean isItemEquipped(int itemId)
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

