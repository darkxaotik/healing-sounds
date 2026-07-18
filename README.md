# Healing Sounds

A RuneLite plugin that plays a notification sound whenever you get healed by a **Sanguinesti Staff** or an **Amulet of Blood Fury**. Never miss a clutch heal during combat again!

## Features

- **Audio Notifications:** Plays a sound effect instantly when your Hitpoints increase due to specific weapon effects.
- **Supported Gear:** 
  - Sanguinesti Staff
  - Amulet of Blood Fury
- **Smart Tracking:** Automatically ignores natural HP regeneration while you are out of combat, preventing annoying and unnecessary sound triggers.
- **Customizable In-Game Sounds:** Choose to play a custom in-game sound effect ID instead of the default notification sound.

## Settings

You can customize the plugin's behavior in the RuneLite configuration menu:
- **Sanguinesti Staff:** Toggle sound notifications specifically for Sanguinesti Staff heals, (Enabled by default).
- **Blood Fury:** Toggle sound notifications specifically for Amulet of Blood Fury heals, (Enabled by default).
- **Sound Volume:** Adjust the volume of the default healing sound (0-100).
- **Use In-Game Sound:** Check this to use an existing Old School RuneScape sound effect instead of the default notification.
- **In-game Sound ID:** If the above is enabled, you can specify exactly which sound effect ID to play (defaults to `166`). You can find a list of in-game sound IDs [here](https://oldschool.runescape.wiki/w/List_of_sound_IDs).
- **Minimum Heal Amount:** Only play the sound for heals of this amount or greater.

![Settings](https://i.imgur.com/1R7cPvL.png)