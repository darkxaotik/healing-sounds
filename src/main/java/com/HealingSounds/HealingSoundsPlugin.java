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
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.events.GameTick;
import net.runelite.api.ItemID;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import java.util.Locale;

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
	private static final int INVENTORY_CHANGE_SUPPRESSION_TICKS = 2;
	private static final int PASSIVE_HEAL_WINDOW_TICKS = 2;

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

	/**
	 * The tick on which the player cast an Ancient Magicks spell (like Blood Barrage).
	 */
	private int lastBloodSpellTick = -1;
	private int lastAttackTick = -1;
	private int lastSoundTick = -1;
	private int pendingHealTick = -1;

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
		lastBloodSpellTick = -1;
		lastAttackTick = -1;
		lastSoundTick = -1;
		pendingHealTick = -1;
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
		// 1978 = Ancient single-target (Rush/Blitz), 1979 = Ancient multi-target (Burst/Barrage)
		else if (actor.getAnimation() == 1978 || actor.getAnimation() == 1979)
		{
			markBloodSpell();
		}
		else if (actor.getAnimation() != AnimationID.IDLE && actor.getInteracting() != null)
		{
			lastAttackTick = client.getTickCount();
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

		if (client.getLocalPlayer() != null && isBloodSpellAnimation(client.getLocalPlayer().getAnimation()))
		{
			markBloodSpell();
		}

		processPendingHeal();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption() == null ? "" : event.getMenuOption().toLowerCase(Locale.ROOT);
		String target = event.getMenuTarget() == null ? "" : event.getMenuTarget().toLowerCase(Locale.ROOT);
		String menuText = option + " " + target;

		if (menuText.contains("blood rush")
			|| menuText.contains("blood blitz")
			|| menuText.contains("blood burst")
			|| menuText.contains("blood barrage"))
		{
			markBloodSpell();
			log.debug("Blood spell cast selected");
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
			// A passive weapon heal must follow a recent attack. Interaction alone is
			// not sufficient because raid rooms can restore HP while an NPC is still targeted.
			if (lastInteractingTick == -1 || client.getTickCount() - lastInteractingTick > 10)
			{
				lastHp = currentHp;
				return;
			}

			int currentTick = client.getTickCount();

			// Check the current animation as well as the recorded animation event.
			// This covers drinking a brew on the same tick as an attack, where the
			// attack animation can otherwise make the HP increase look like a weapon heal.
			if (isCurrentlyConsuming())
			{
				log.debug("HP gain of {} suppressed — player is consuming an item", hpGain);
				lastHp = currentHp;
				return;
			}

			if (lastAttackTick == -1 || currentTick - lastAttackTick > PASSIVE_HEAL_WINDOW_TICKS)
			{
				log.debug("HP gain of {} suppressed — no recent player attack", hpGain);
				lastHp = currentHp;
				return;
			}

			// Filter out eating/drinking heals:
			// If the player recently performed the CONSUMING animation, the heal is from food/potions.
			if (lastConsumingAnimTick != -1 && currentTick - lastConsumingAnimTick <= CONSUMING_SUPPRESSION_TICKS)
			{
				log.debug("HP gain of {} suppressed — player ate/drank recently (anim tick {}, current tick {})",
					hpGain, lastConsumingAnimTick, currentTick);
				lastHp = currentHp;
				return;
			}

			// Secondary filter: if the inventory changed recently, the heal likely came
			// from consuming an item (food, potion, etc.), not a passive weapon/amulet effect.
			if (lastInventoryChangeTick != -1 && currentTick - lastInventoryChangeTick <= INVENTORY_CHANGE_SUPPRESSION_TICKS)
			{
				log.debug("HP gain of {} suppressed — inventory changed recently (tick {} vs {})", hpGain, lastInventoryChangeTick, currentTick);
				lastHp = currentHp;
				return;
			}

			boolean shouldPlaySound = false;

			// Filter out Blood Spells (Barrage, etc.) which heal independently of the weapons
			if (lastBloodSpellTick != -1 && currentTick - lastBloodSpellTick <= 5)
			{
				log.debug("HP gain of {} suppressed — player cast Blood Spell recently", hpGain);
				lastHp = currentHp;
				return;
			}

			if (config.sanguinestStaff() && isSanguinestEquipped())
			{
				shouldPlaySound = true;
				log.debug("Sanguinesti Staff passive heal detected: {} HP", hpGain);
			}

			if (config.bloodFury() && isBloodFuryEquipped())
			{
				// Ignore Blowpipe heals for Blood Fury since Blood Fury only works with melee
				if (!isItemEquipped(ItemID.TOXIC_BLOWPIPE) && !isItemEquipped(ItemID.TOXIC_BLOWPIPE_EMPTY))
				{
					shouldPlaySound = true;
					log.debug("Blood Fury passive heal detected: {} HP", hpGain);
				}
			}

			if (shouldPlaySound)
			{
				pendingHealTick = currentTick;
			}
		}

		lastHp = currentHp;
	}

	private void processPendingHeal()
	{
		int currentTick = client.getTickCount();
		if (pendingHealTick == -1 || currentTick <= pendingHealTick)
		{
			return;
		}

		pendingHealTick = -1;

		if (lastConsumingAnimTick != -1 && currentTick - lastConsumingAnimTick <= CONSUMING_SUPPRESSION_TICKS)
		{
			log.debug("Pending healing sound suppressed — player consumed an item");
			return;
		}

		if (lastInventoryChangeTick != -1 && currentTick - lastInventoryChangeTick <= INVENTORY_CHANGE_SUPPRESSION_TICKS)
		{
			log.debug("Pending healing sound suppressed — inventory changed recently");
			return;
		}

		if (lastBloodSpellTick != -1 && currentTick - lastBloodSpellTick <= 5)
		{
			log.debug("Pending healing sound suppressed — player cast Blood Spell recently");
			return;
		}

		if (lastSoundTick == currentTick)
		{
			return;
		}

		playHealingSound();
		lastSoundTick = currentTick;
	}

	private void markBloodSpell()
	{
		lastBloodSpellTick = client.getTickCount();
	}

	private boolean isBloodSpellAnimation(int animation)
	{
		return animation == 1978 || animation == 1979;
	}

	private boolean isSanguinestEquipped()
	{
		return isItemEquipped(SANGUINESTI_STAFF);
	}

	private boolean isBloodFuryEquipped()
	{
		return isItemEquipped(BLOOD_FURY);
	}

	private boolean isCurrentlyConsuming()
	{
		return client.getLocalPlayer() != null
			&& client.getLocalPlayer().getAnimation() == AnimationID.CONSUMING;
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

		int volumeConfig = config.soundVolume();
		if (volumeConfig <= 0)
		{
			return;
		}

		try
		{
			// Convert the linear volume (1-100) to a decibel scale (-40dB to 0dB) 
			// because passing 0.0f to the player was treated as 0 dB (full volume).
			float volume = Math.max(1f, Math.min(100f, volumeConfig));
			float decibels = (float) (Math.log10(volume / 100f) * 20f);
			
			// Play the custom healing sound using RuneLite's AudioPlayer
			audioPlayer.play(this.getClass(), "/healing_sound.wav", decibels);
			log.debug("Healing sound played via AudioPlayer with dB: {}", decibels);
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
