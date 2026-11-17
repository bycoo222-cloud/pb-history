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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Raid room times, from the game's own messages as each room ends (user 2026-11-08: "sum of best yes everyone wants room
 * time to know where to improve"). The formats are the game's, as recorded in the tests of the Plugin Hub's Raid Data
 * Tracker (github.com/Raitab/raid-data-tracker, RaidTrackerTest):
 * <ul>
 * <li>Chambers of Xeric: "Combat room `Vanguards` complete! Duration: 3:19 Total: 16:16", "Puzzle `Crabs` complete! ..." —
 * NOT the game's: the game prints only floor times in the Chambers of Xeric. These come from the "CoX Timers" part of the
 * Plugin Hub's CoX Additions (github.com/dey0/pluginhub-plugins, CoxTimersPlugin: backticks), so CoX rooms need it on;
 * straight quotes (Raid Data Tracker's tests) are read too</li>
 * <li>Theatre of Blood: "Wave 'The Maiden of Sugadinti' (Normal Mode) complete!&lt;br&gt;Duration: ..."</li>
 * <li>Tombs of Amascut: "Challenge complete: Path of Crondis. Duration: 0:34.20. Total: 0:34.20"</li>
 * </ul>
 * Shown after the raid, never during it.
 */
final class Rooms
{
	private static final String TIME = "(?<d>\\d+(?::\\d{1,2}){0,2}(?:\\.\\d+)?)";
	private static final Pattern TOB_ROOM = Pattern.compile("Wave '(?<room>[^']+)' \\([^)]*\\) complete!.*?Duration: " + TIME);
	private static final Pattern COX_ROOM = Pattern.compile("^(?:[A-Za-z]+ room |Puzzle )?[`'](?<room>[^`']+)[`'] complete! "
		+ "Duration: " + TIME);
	private static final Pattern TOA_ROOM = Pattern.compile("Challenge complete: (?<room>[^.]+)\\. Duration: " + TIME);

	static final String TOB = "tob";
	static final String TOA = "toa";
	static final String COX = "cox";
	static final String TFA = "tfa";
	/**
	 * A "Challenge complete: &lt;room&gt;. Duration:" line whose room is on neither list below: the Tombs of Amascut and The
	 * Fractured Archive word their room lines the same, so it counts for whichever of the two ends next (the raid's
	 * time window keeps another raid's rooms out).
	 */
	static final String CHALLENGE = "challenge";
	/** The Theatre of Blood's waves and the Tombs of Amascut's challenges: always the same rooms, so their best times add up. */
	static final List<String> TOB_ROOMS = Arrays.asList("The Maiden of Sugadinti", "The Pestilent Bloat", "The Nylocas", "Sotetseg",
		"Xarpus", "The Final Challenge");
	static final List<String> TOA_ROOMS = Arrays.asList("Path of Scabaras", "Kephri", "Path of Het", "Akkha", "Path of Crondis", "Zebak",
		"Path of Apmeken", "Ba-Ba", "The Wardens");
	/**
	 * The Fractured Archive's rooms in stage order: the Archive Guardian, then one of two rooms at each of stages 2-4,
	 * then Ocellus. MEASURED from eight real raids' messages (the TFA Splits Tracker plugin's recorded-raids.json):
	 * Archive Guardian, Bork, Dreadnoth the Jellied, Xarth, Ocellus. Ezekiel, Moia and Dagon'hai Trio are that plugin's
	 * names for the other three (its TfaRoom), not seen in a message; a room not on this list still counts (CHALLENGE).
	 */
	static final List<String> TFA_ROOMS = Arrays.asList("Archive Guardian", "Ezekiel", "Bork", "Moia", "Dreadnoth the Jellied",
		"Dagon'hai Trio", "Xarth", "Ocellus");
	private static final Map<String, String> SHORT = Map.of("The Maiden of Sugadinti", "Maiden", "The Pestilent Bloat", "Bloat",
		"The Nylocas", "Nylocas", "The Final Challenge", "Verzik", "Dreadnoth the Jellied", "Dreadnoth");

	private Rooms()
	{
	}

	/** A room-complete message: {room, seconds, raid (TOB / TOA / COX)}; null = not one. */
	static Object[] parse(String message)
	{
		if (message == null || !message.contains("omplete"))
		{
			return null;
		}
		String text = message.replace("<br>", " ").replaceAll("<[^>]*>", "").replace(' ', ' ').trim();
		Pattern[] patterns = {TOB_ROOM, TOA_ROOM, COX_ROOM};
		String[] raids = {TOB, TOA, COX};
		for (int i = 0; i < patterns.length; i++)
		{
			Matcher m = patterns[i].matcher(text);
			if (m.find())
			{
				double secs = Times.parse(m.group("d"));
				String room = m.group("room").replace('\u2019', '\'').trim();
				String raid = raids[i];
				if (TOA.equals(raid))
				{
					raid = has(TFA_ROOMS, room) ? TFA : has(TOA_ROOMS, room) ? TOA : CHALLENGE;
				}
				return secs > 0 ? new Object[]{room, secs, raid} : null;
			}
		}
		return null;
	}

	private static boolean has(List<String> rooms, String room)
	{
		for (String r : rooms)
		{
			if (r.equalsIgnoreCase(room))
			{
				return true;
			}
		}
		return false;
	}

	/** The raid a boss key is (TOB / TOA / COX / TFA); null = not a raid. */
	static String raid(String key)
	{
		String k = key == null ? "" : key;
		return k.startsWith("theatre of blood") ? TOB : k.startsWith("tombs of amascut") ? TOA
			: k.startsWith("chambers of xeric") ? COX : k.startsWith("fractured archive") || k.startsWith("the fractured archive")
			? TFA : null;
	}

	/**
	 * The rooms of the raid that just ended, out of every room message seen ({room, seconds, when ms, raid}): only that
	 * raid's ({@link #raid}), only since it could have begun ({@code windowSecs} before {@code now}), and the latest of
	 * each room — so the rooms of an earlier raid that was left, or of another raid, are never filed with it.
	 */
	static List<Object[]> ofThisRaid(List<Object[]> seen, String raid, long now, double windowSecs)
	{
		Map<String, Object[]> latest = new LinkedHashMap<>();
		for (Object[] r : seen)
		{
			boolean either = CHALLENGE.equals(r.length < 4 ? null : r[3]) && (TOA.equals(raid) || TFA.equals(raid));
			if (raid == null || r.length < 4 || !(raid.equals(r[3]) || either) || now - (long) r[2] > windowSecs * 1000)
			{
				continue;
			}
			Object[] had = latest.get((String) r[0]);
			if (had == null || (long) r[2] >= (long) had[2])
			{
				latest.put((String) r[0], r);
			}
		}
		return new ArrayList<>(latest.values());
	}

	/** A raid key without its team size: "tombs of amascut expert mode 3 players" → "tombs of amascut expert mode". */
	static String mode(String key)
	{
		return key == null ? null : key.replaceFirst(" (?:solo|\\d+\\+? players?|\\d+-\\d+ players)$", "");
	}

	/** The fixed rooms of this raid (Theatre of Blood, Tombs of Amascut); null = rooms that change each raid (Chambers of Xeric, The
	 * Fractured Archive). */
	static List<String> fixed(String key)
	{
		String k = key == null ? "" : key;
		return k.startsWith("theatre of blood") ? TOB_ROOMS : k.startsWith("tombs of amascut") ? TOA_ROOMS : null;
	}

	/** The order a raid's rooms are listed in: its fixed rooms, or The Fractured Archive's stages; null = as they came. */
	static List<String> order(String key)
	{
		List<String> f = fixed(key);
		return f != null ? f : TFA.equals(raid(key)) ? TFA_ROOMS : null;
	}

	/** A room's name in a table: "Maiden", "Verzik"; anything else as the game says it. */
	static String label(String room)
	{
		return SHORT.getOrDefault(room, room);
	}
}
