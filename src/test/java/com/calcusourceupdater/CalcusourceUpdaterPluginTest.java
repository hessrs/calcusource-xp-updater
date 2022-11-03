package com.calcusourceupdater;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CalcusourceUpdaterPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CalcusourceUpdaterPlugin.class);
		RuneLite.main(args);
	}
}