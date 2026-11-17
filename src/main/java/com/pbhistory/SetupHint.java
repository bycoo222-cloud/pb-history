/*
 * Copyright (c) 2026, bycoo222
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.pbhistory;

import java.util.ArrayList;
import java.util.List;

/**
 * The setup hint (user, 2026-11-08: "do everything u suggested"): which RuneLite settings PB History needs are off.
 * Read only: PB History never changes another plugin's settings. Shown as a card at the top of the list; its ✕ hides
 * it until the list changes. The Chambers of Xeric room times also need the CoX Timers of the CoX Additions Hub
 * plugin, but that one is only explained (README, the Rooms tooltip): a Hub plugin's switch is saved only once it's
 * installed, so "not set" can't tell "not installed" from "on".
 */
final class SetupHint
{
	/** One setting as RuneLite keeps it (group, key); null = never set, so its default applies. */
	interface Settings
	{
		String get(String group, String key);
	}

	/** One thing to turn on: a stable code (what the ✕ remembers) and what to do. */
	static final class Item
	{
		final String code;
		final String text;

		Item(String code, String text)
		{
			this.code = code;
			this.text = text;
		}
	}

	private SetupHint()
	{
	}

	/** A RuneLite plugin is on unless switched off: Chat Commands and Screenshot don't set enabledByDefault = false. */
	static boolean pluginOn(Settings s, String plugin)
	{
		String v = s.get("runelite", plugin);
		return v == null || Boolean.parseBoolean(v);
	}

	static boolean on(Settings s, String group, String key, boolean byDefault)
	{
		String v = s.get(group, key);
		return v == null ? byDefault : Boolean.parseBoolean(v);
	}

	/**
	 * What's off: RuneLite's Chat Commands plugin (every PB and kill count PB History shows is the one it saves), and
	 * for PB pictures RuneLite's Screenshot plugin with its "Screenshot boss kills" ticked (off until ticked).
	 */
	static List<Item> missing(Settings s)
	{
		List<Item> out = new ArrayList<>();
		if (!pluginOn(s, "chatcommandsplugin"))
		{
			out.add(new Item("chat-commands", "Turn on RuneLite's Chat Commands plugin: PB History gets your PBs and "
				+ "kill counts from it."));
		}
		if (!pluginOn(s, "screenshotplugin"))
		{
			out.add(new Item("screenshot-plugin", "PB pictures: turn on RuneLite's Screenshot plugin, then tick "
				+ "\"Screenshot boss kills\" in its settings."));
		}
		else if (!on(s, "screenshot", "boss", false))
		{
			out.add(new Item("screenshot-boss", "PB pictures: tick \"Screenshot boss kills\" in RuneLite's Screenshot "
				+ "plugin settings."));
		}
		return out;
	}

	/** A change to one of the settings {@link #missing} reads. */
	static boolean watches(String group, String key)
	{
		return "runelite".equals(group) && ("chatcommandsplugin".equals(key) || "screenshotplugin".equals(key))
			|| "screenshot".equals(group) && "boss".equals(key);
	}

	/** What the ✕ remembers: the codes, in order ("" = nothing off). */
	static String fingerprint(List<Item> items)
	{
		StringBuilder b = new StringBuilder();
		for (Item i : items)
		{
			b.append(b.length() == 0 ? "" : ",").append(i.code);
		}
		return b.toString();
	}
}
