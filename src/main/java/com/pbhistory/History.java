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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The history per boss: its PB now, every PB it took to get there (with dates) and every timed kill. */
final class History
{
	static final class Boss
	{
		final String key;
		final String name;
		/** PBs oldest first; a PB from before PB History has at = 0 */
		final List<Entry> pbs = new ArrayList<>();
		/** timed kills, oldest first */
		final List<Entry> kills = new ArrayList<>();
		int kc;

		Boss(String key)
		{
			this.key = key;
			this.name = Times.name(key);
		}

		/** any time with hundredths: then every time of this boss is shown with them */
		boolean precise()
		{
			for (List<Entry> l : java.util.Arrays.asList(pbs, kills))
			{
				for (Entry e : l)
				{
					if (Math.abs(e.secs * 100 - Math.round(e.secs) * 100) > 0.5)
					{
						return true;
					}
				}
			}
			return false;
		}

		/** a time of this boss, written the way its others are */
		String time(double secs)
		{
			return Times.format(secs, precise());
		}

		/** the personal best now (seconds) */
		double best()
		{
			double b = Double.MAX_VALUE;
			for (Entry e : pbs)
			{
				b = Math.min(b, e.secs);
			}
			for (Entry e : kills)
			{
				b = Math.min(b, e.secs);
			}
			return b == Double.MAX_VALUE ? 0 : b;
		}

		/** the PB line: each time the best came down, oldest first (a PB from before PB History first) */
		List<Entry> steps()
		{
			List<Entry> out = new ArrayList<>();
			double best = Double.MAX_VALUE;
			for (Entry e : pbs)
			{
				if (e.secs < best - 1e-9)
				{
					out.add(e);
					best = e.secs;
				}
			}
			return out;
		}

		/** when the PB now was set; 0 = before PB History */
		long bestAt()
		{
			List<Entry> s = steps();
			return s.isEmpty() ? 0 : s.get(s.size() - 1).at;
		}

		long lastAt()
		{
			long t = 0;
			for (Entry e : pbs)
			{
				t = Math.max(t, e.at);
			}
			for (Entry e : kills)
			{
				t = Math.max(t, e.at);
			}
			return t;
		}

		/** the average of the last {@code n} timed kills (all if n ≤ 0); 0 if none */
		double average(int n)
		{
			int from = n <= 0 ? 0 : Math.max(0, kills.size() - n);
			double sum = 0;
			for (int i = from; i < kills.size(); i++)
			{
				sum += kills.get(i).secs;
			}
			return kills.size() > from ? sum / (kills.size() - from) : 0;
		}
	}

	static final String BOSSES = "Bosses";
	static final String RAIDS = "Raids";
	static final String CHALLENGES = "Challenges";
	static final String OTHER = "Other";
	static final java.util.List<String> TABS = java.util.Arrays.asList(BOSSES, RAIDS, CHALLENGES, OTHER);

	/**
	 * The tab a PB goes in, by RuneLite's name for it (user, 2026-11-07: "pbs should be divided into tabs raids, bosses,
	 * then challenges like inferno collesuim fight caves"). RuneLite names the Inferno, Fight Caves and Colosseum after
	 * their last boss (its kill count message): TzKal-Zuk, TzTok-Jad, Sol Heredit. Laps, skilling bosses and minigames
	 * go in Other; everything else is a boss.
	 */
	static String tab(String key)
	{
		String k = key.toLowerCase(java.util.Locale.ROOT);
		if (k.startsWith("chambers of xeric") || k.startsWith("theatre of blood") || k.startsWith("tombs of amascut"))
		{
			return RAIDS;
		}
		for (String c : new String[]{"tzkal-zuk", "inferno", "tztok-jad", "fight cave", "sol heredit", "colosseum", "tzhaar-ket-rak",
			"gauntlet", "hunllef"})
		{
			if (k.contains(c))
			{
				return CHALLENGES;
			}
		}
		for (String o : new String[]{"agility course", "course", "lap", "tempoross", "wintertodt", "sepulchre", "zalcano",
			"guardians of the rift", "volcanic mine", "mahogany homes", "giants' foundry", "barbarian assault", "rooftop"})
		{
			if (k.contains(o))
			{
				return OTHER;
			}
		}
		return BOSSES;
	}

	// ---- raids: which raid, which mode, which team size (user, 2026-11-07: "sub tabs for raids ... for toa so its entry
	// normal hard and then do team size, for cox do callenge mode and regular ... we got to make that look neater") ----
	static final String[] RAID_PREFIX = {"chambers of xeric", "theatre of blood", "tombs of amascut"};
	static final String[] RAID_SHORT = {"CoX", "ToB", "ToA"};
	static final String[] RAID_LONG = {"Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut"};
	/** each raid's modes in the order they're shown ("" = its normal mode) */
	static final String[][] RAID_MODES = {{"", "challenge mode"}, {"entry mode", "", "hard mode"}, {"entry mode", "", "expert mode"}};
	private static final java.util.regex.Pattern SIZE = java.util.regex.Pattern.compile("(?:^|\\s)(solo|\\d+ players?)$");

	/** A raid PB's parts, from RuneLite's name: "tombs of amascut expert mode 2 players" → ToA, "expert mode", "2 players". */
	static final class Raid
	{
		final int raid;
		/** "" (normal), "entry mode", "expert mode", "hard mode", "challenge mode", or another the game adds */
		final String mode;
		/** "" = the best at any team size, "solo", "2 players" ... */
		final String size;

		Raid(int raid, String mode, String size)
		{
			this.raid = raid;
			this.mode = mode;
			this.size = size;
		}
	}

	/** null if not a raid */
	static Raid raid(String key)
	{
		String k = key.toLowerCase(java.util.Locale.ROOT).trim();
		for (int i = 0; i < RAID_PREFIX.length; i++)
		{
			if (!k.startsWith(RAID_PREFIX[i]))
			{
				continue;
			}
			String rest = k.substring(RAID_PREFIX[i].length()).trim();
			String size = "";
			java.util.regex.Matcher m = SIZE.matcher(rest);
			if (m.find())
			{
				size = m.group(1);
				rest = rest.substring(0, m.start()).trim();
			}
			return new Raid(i, rest, size);
		}
		return null;
	}

	static String modeName(int raid, String mode)
	{
		switch (mode)
		{
			case "":
				return raid == 0 ? "Regular" : "Normal";
			case "entry mode":
				return "Entry";
			case "expert mode":
				return "Expert";
			case "hard mode":
				return "Hard Mode";
			case "challenge mode":
				return "Challenge Mode";
			default:
				return Times.name(mode);
		}
	}

	static String sizeName(String size)
	{
		return size.isEmpty() ? "Best at any size" : size.equals("solo") ? "Solo" : size;
	}

	/** Solo first, then 2, 3 ... players */
	static int sizeOrder(String size)
	{
		if (size.isEmpty())
		{
			return 0;
		}
		if (size.equals("solo"))
		{
			return 1;
		}
		try
		{
			return Integer.parseInt(size.replaceAll("\\D", ""));
		}
		catch (NumberFormatException e)
		{
			return 99;
		}
	}

	/**
	 * Every boss: from the saved lines, plus RuneLite's own saved PBs ({@code stored}: name → seconds) as "before PB
	 * History" PBs where the lines have none as good, and RuneLite's saved kill counts ({@code kcs}).
	 * Sorted: the latest activity first, then the ones from before PB History by name.
	 */
	static List<Boss> build(List<Entry> lines, Map<String, Double> stored, Map<String, Integer> kcs)
	{
		Map<String, Boss> by = new LinkedHashMap<>();
		for (Entry e : lines)
		{
			if (e.boss == null || e.secs <= 0)
			{
				continue;
			}
			Boss b = by.computeIfAbsent(e.boss, Boss::new);
			(e.isPb() ? b.pbs : b.kills).add(e);
		}
		for (Map.Entry<String, Double> s : stored.entrySet())
		{
			if (s.getValue() == null || s.getValue() <= 0)
			{
				continue;
			}
			Boss b = by.computeIfAbsent(s.getKey(), Boss::new);
			boolean known = false;
			for (Entry e : b.pbs)
			{
				known |= e.secs <= s.getValue() + 1e-9;
			}
			if (!known)
			{
				b.pbs.add(Entry.pb(s.getKey(), s.getValue(), 0, 0, 0, false));   // set before PB History: no date
			}
		}
		for (Boss b : by.values())
		{
			b.pbs.sort(Comparator.comparingLong(e -> e.at));
			b.kills.sort(Comparator.comparingLong(e -> e.at));
			Integer kc = kcs.get(b.key);
			b.kc = kc != null ? kc : 0;
			for (Entry e : b.kills)
			{
				b.kc = Math.max(b.kc, e.kc);
			}
		}
		List<Boss> out = new ArrayList<>(by.values());
		out.sort(Comparator.comparingLong(Boss::lastAt).reversed().thenComparing(b -> b.name));
		return out;
	}
}
