package com.HealingSounds;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HealingSoundsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HealingSoundsPlugin.class);
		RuneLite.main(args);
	}
}