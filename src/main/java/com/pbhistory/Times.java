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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Game times ("1:23.40", "1:02:03") and boss names, read and written. */
final class Times
{
	/**
	 * A kill's time in a game message, from RuneLite's own patterns (its Chat Commands plugin, client 1.13.6) with the
	 * kill's own time captured: "Fight duration: 1:23.40 (new personal best)", "Fight duration: 1:25.80. Personal best:
	 * 1:23.40", "Subdued in", "Lap duration", "completion time".
	 */
	private static final Pattern KILL = Pattern.compile("(?i)(?:(?:Fight |Lap |Challenge |Corrupted challenge )?duration:|Subdued in|"
		+ "(?<!total )completion time:|The Fractured Archive: Total completion time:) (?:<col=[0-9a-f]{6}>|@.+?@)"
		+ "(?<time>[0-9:]+(?:\\.[0-9]+)?)</col>(?<fresh> \\(new personal best\\))?");
	/** a raid: "Team size: Solo Duration: 25:00 (new personal best)" (RuneLite's raid pattern, the time captured) */
	private static final Pattern RAID = Pattern.compile("Duration:</col> <col=ff0000>(?<time>[0-9:]+(?:\\.[0-9]+)?)</col>"
		+ "(?<fresh> \\(new personal best\\))?");

	/** {seconds, 1 if the game said new personal best} or null */
	static double[] duration(String message)
	{
		for (Pattern p : new Pattern[]{RAID, KILL})
		{
			Matcher m = p.matcher(message);
			if (m.find())
			{
				double s = parse(m.group("time"));
				if (s > 0)
				{
					return new double[]{s, m.group("fresh") != null ? 1 : 0};
				}
			}
		}
		return null;
	}

	/** "1:23.40" → 83.4, "1:02:03" → 3723, "45.6" → 45.6; 0 if it can't be read */
	static double parse(String t)
	{
		try
		{
			double s = 0;
			for (String part : t.trim().split(":"))
			{
				s = s * 60 + Double.parseDouble(part);
			}
			return s;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** 83.4 → "1:23.40", 3723 → "1:02:03", 45 → "0:45" (hundredths only when there are any) */
	static String format(double secs)
	{
		return format(secs, false);
	}

	/** as {@link #format(double)}; {@code hundredths}: always ".00" — for a boss timed to the tick, so a column lines up */
	static String format(double secs, boolean hundredths)
	{
		long cs = Math.round(secs * 100);
		long h = cs / 360000, m = cs / 6000 % 60, s = cs / 100 % 60, frac = cs % 100;
		String base = h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
		return frac == 0 && !hundredths ? base : base + String.format(Locale.ROOT, ".%02d", frac);
	}

	/** how much faster: 2.4 → "2.40s faster", 65 → "1:05 faster" */
	static String faster(double by)
	{
		return (by < 60 ? String.format(Locale.ROOT, by == Math.floor(by) ? "%.0fs" : "%.2fs", by) : format(by)) + " faster";
	}

	private static final java.util.Set<String> SMALL = new java.util.HashSet<>(java.util.Arrays.asList("of", "the", "and", "in", "on", "at"));

	/** names the game writes with capitals inside a word */
	private static final java.util.Map<String, String> PROPER = new java.util.HashMap<>();
	static
	{
		for (String w : new String[]{"TzTok-Jad", "TzKal-Zuk", "TzHaar-Ket-Rak's", "TzHaar", "TzTok", "TzKal"})
		{
			PROPER.put(w.toLowerCase(Locale.ROOT), w);
		}
	}

	/** "tombs of amascut expert mode 2 players" → "Tombs of Amascut Expert Mode 2 Players", "tztok-jad" → "TzTok-Jad" */
	static String name(String key)
	{
		StringBuilder out = new StringBuilder();
		String[] words = key.split(" ");
		for (int i = 0; i < words.length; i++)
		{
			String w = words[i];
			if (i > 0)
			{
				out.append(' ');
			}
			if (PROPER.containsKey(w))
			{
				out.append(PROPER.get(w));
				continue;
			}
			if (i > 0 && SMALL.contains(w))
			{
				out.append(w);
				continue;
			}
			int at = w.startsWith("(") ? 1 : 0;
			out.append(w, 0, at);
			if (w.length() > at)
			{
				out.append(Character.toUpperCase(w.charAt(at))).append(w.substring(at + 1));
			}
		}
		return out.toString();
	}
}
