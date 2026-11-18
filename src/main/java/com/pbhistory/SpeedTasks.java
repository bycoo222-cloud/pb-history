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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.Icon;
import net.runelite.client.ui.FontManager;

/**
 * The Combat Achievement speed tasks of a PB: each tier's time to beat, by RuneLite's name for the PB (user 2026-11-17,
 * after the mockup: "Yes build that"). The times are the OSRS Wiki's, from its list of all 655 tasks (Combat
 * Achievements/All tasks, read 2026-11-17), with the team-size tables of "Dust Seeker" (CoX CM target times) and "Hard
 * Mode? Completed It" (ToB HM challenge times). PB History only compares the PB with each time: it never reads which
 * tasks the game gave you, and some tasks have more rules (no slayer task; a team size RuneLite's PB doesn't keep).
 * <p>
 * Left out on purpose: tasks that aren't one kill's time (Araxxor, the Shellbane Gryphon, the Mad Angel and the Maggot
 * King: kills within a time; Yama: an average of four kills), the Moons of Peril and the Doom of Mokhaiotl (no PB of
 * RuneLite's matches the task's run) and the quest bosses (Galvek, Glough, the Fragment of Seren: no PB).
 */
final class SpeedTasks
{
	static final String[] TIERS = {"Hard", "Elite", "Master", "Grandmaster"};
	static final String[] SHORT = {"H", "E", "M", "GM"};
	/** our own colours for the tiers (not the game's) */
	static final Color[] COLOURS = {new Color(120, 190, 90), new Color(72, 170, 220), new Color(170, 110, 230), new Color(236, 98, 160)};

	static final class Task
	{
		/** index in {@link #TIERS} */
		final int tier;
		final double secs;
		/** "in less than": the time itself doesn't count; else "within" / "or less" / "in": it does */
		final boolean strict;
		/** false: the task needs a team size RuneLite's PB doesn't keep (one Nightmare PB for every size), so it can't be told */
		final boolean sure;
		/** "" or a rule shown under the tier ("no slayer task", "solo") */
		final String note;
		/** the wiki's own words for the task */
		final String text;

		Task(int tier, double secs, boolean strict, boolean sure, String note, String text)
		{
			this.tier = tier;
			this.secs = secs;
			this.strict = strict;
			this.sure = sure;
			this.note = note;
			this.text = text;
		}

		/** does a PB of {@code pb} seconds beat this time */
		boolean beatenBy(double pb)
		{
			return pb > 0 && (strict ? pb < secs - 1e-9 : pb <= secs + 1e-9);
		}
	}

	/**
	 * key(s) | tier | time | rule | note | the wiki's words. Keys are RuneLite's PB names (lower case, as its Chat Commands
	 * saves them; MEASURED on the user's own saved PBs: "vorkath", "hueycoatl", "nightmare", "tztok-jad", "chambers of xeric 3
	 * players"...); "raid mode *" = that mode at every team size. Rule: "<" in less than, "=" within, "?" can't be told.
	 */
	private static final String[] DATA = {
		"amoxliatl;H;1:00;<;;Kill Amoxliatl in less than 1 minute.",
		"amoxliatl;E;0:30;<;;Kill Amoxliatl in less than 30 seconds.",
		"royal titans|the royal titans;H;1:30;<;;Kill the Royal Titans in less than 1:30 minutes.",
		"brutus;E;0:02;<;;Kill Brutus in less than 2 seconds.",
		"duke sucellus;E;1:00;<;no slayer task;Kill Duke Sucellus in less than 1:00 minutes without a slayer task.",
		"duke sucellus;M;0:50;<;no slayer task;Kill Duke Sucellus in less than 50 seconds without a slayer task.",
		"duke sucellus;GM;0:40;<;no slayer task;Kill Duke Sucellus in less than 40 seconds without a slayer task.",
		"grotesque guardians;E;2:00;<;;Kill the Grotesque Guardians in less than 2 minutes.",
		"grotesque guardians;M;1:40;<;;Kill the Grotesque Guardians in less than 1:40 minutes.",
		"grotesque guardians;GM;1:20;<;;Kill the Grotesque Guardians in less than 1:20 minutes.",
		"hespori;E;0:48;<;;Kill the Hespori in less than 48 seconds.",
		"hespori;M;0:36;<;;Kill the Hespori in less than 36 seconds.",
		"leviathan|the leviathan;E;1:50;<;no slayer task;Kill the Leviathan in less than 1:50 without a slayer task.",
		"leviathan|the leviathan;M;1:25;<;no slayer task;Kill the Leviathan in less than 1:25 without a slayer task.",
		"leviathan|the leviathan;GM;1:10;<;no slayer task;Kill the Leviathan in less than 1:10 without a slayer task.",
		"phantom muspah;E;3:00;<;no slayer task;Kill the Phantom Muspah in less than 3 minutes without a slayer task.",
		"phantom muspah;M;2:00;<;no slayer task;Kill the Phantom Muspah in less than 2 minutes without a slayer task.",
		"phantom muspah;GM;1:30;<;no slayer task;Kill the Phantom Muspah in less than 1 minute and 30 seconds without a slayer task.",
		"hueycoatl|the hueycoatl;E;2:30;=;;Kill the Hueycoatl in 2:30",
		"hueycoatl|the hueycoatl;M;2:30;=?;5 or fewer players;Kill the Hueycoatl in 2:30 with five or fewer players.",
		"hueycoatl|the hueycoatl;GM;2:30;=?;3 or fewer players;Kill the Hueycoatl in 2:30 with three or fewer players.",
		"nightmare|the nightmare;E;5:00;<?;5-scale;Defeat the Nightmare (5-scale) in less than 5 minutes.",
		"nightmare|the nightmare;E;23:00;<?;solo;Defeat the Nightmare (Solo) in less than 23 minutes.",
		"nightmare|the nightmare;M;4:00;<?;5-scale;Defeat the Nightmare (5-scale) in less than 4 minutes.",
		"nightmare|the nightmare;M;19:00;<?;solo;Defeat the Nightmare (Solo) in less than 19 minutes.",
		"nightmare|the nightmare;GM;3:30;<?;5-scale;Defeat the Nightmare (5-scale) in less than 3:30 minutes.",
		"nightmare|the nightmare;GM;16:00;<?;solo;Defeat the Nightmare (Solo) in less than 16 minutes.",
		"phosani's nightmare;M;7:30;=;;Kill Phosani's Nightmare within 7 minutes and 30 seconds.",
		"phosani's nightmare;GM;6:00;=;;Kill Phosani's Nightmare within 6 minutes.",
		"tzhaar-ket-rak's first challenge;E;0:45;<;;Complete TzHaar-Ket-Rak's first challenge in less than 45 seconds.",
		"tzhaar-ket-rak's third challenge;M;3:00;<;;Complete TzHaar-Ket-Rak's third challenge in less than 3 minutes.",
		"tzhaar-ket-rak's fifth challenge;GM;5:00;<;;Complete TzHaar-Ket-Rak's fifth challenge in less than 5 minutes.",
		"vardorvis;E;1:15;<;no slayer task;Kill Vardorvis in less than 1:15 minutes without a slayer task.",
		"vardorvis;M;1:05;<;no slayer task;Kill Vardorvis in less than 1:05 without a slayer task.",
		"vardorvis;GM;0:55;<;no slayer task;Kill Vardorvis in less than 0:55 without a slayer task.",
		"whisperer|the whisperer;E;3:00;<;no slayer task;Kill the Whisperer in less than 3:00 without a slayer task.",
		"whisperer|the whisperer;M;2:25;<;no slayer task;Kill the Whisperer in less than 2:25 without a slayer task.",
		"whisperer|the whisperer;GM;2:05;<;no slayer task;Kill the Whisperer in less than 2:05 without a slayer task.",
		"zulrah;E;1:20;<;no slayer task;Kill Zulrah in less than 1 minute 20 seconds, without a slayer task.",
		"zulrah;M;1:00;<;no slayer task;Kill Zulrah in less than 1 minute, without a slayer task.",
		"zulrah;GM;0:54;<;no slayer task;Kill Zulrah in less than 54 seconds, without a slayer task.",
		"alchemical hydra;M;1:45;<;;Kill the Alchemical Hydra in less than 1 minute 45 seconds.",
		"alchemical hydra;GM;1:20;<;;Kill the Alchemical Hydra in less than 1 minute 20 seconds.",
		"corrupted gauntlet;M;7:05;<;;Complete a Corrupted Gauntlet in less than 7 minutes and 5 seconds.",
		"corrupted gauntlet;GM;6:05;<;;Complete a Corrupted Gauntlet in less than 6 minutes and 5 seconds.",
		"gauntlet;M;4:45;<;;Complete the Gauntlet in less than 4 minutes and 45 seconds.",
		"gauntlet;GM;3:45;<;;Complete the Gauntlet in less than 3 minutes and 45 seconds.",
		"sol heredit;M;28:00;=;;Complete the Colosseum with a total time of 28:00 or less.",
		"sol heredit;GM;24:00;=;;Complete the Colosseum with a total time of 24:00 or less.",
		"tztok-jad;M;30:00;<;;Complete the Fight Caves in less than 30 minutes.",
		"tztok-jad;GM;26:30;<;;Complete the Fight Caves in less than 26 minutes and 30 seconds.",
		"tzkal-zuk;GM;1:05:00;<;;Complete the Inferno in less than 65 minutes.",
		"vorkath;M;1:15;<;;Kill Vorkath in less than 1 minute and 15 seconds.",
		"vorkath;GM;0:54;<;;Kill Vorkath in less than 54 seconds.",
		// Chambers of Xeric
		"chambers of xeric solo;M;21:00;<;;Complete a Chambers of Xeric (Solo) in less than 21 minutes.",
		"chambers of xeric solo;GM;17:00;<;;Complete a Chambers of Xeric (Solo) in less than 17 minutes.",
		"chambers of xeric 3 players;M;16:30;<;;Complete a Chambers of Xeric (Trio) in less than 16 minutes and 30 seconds.",
		"chambers of xeric 3 players;GM;14:30;<;;Complete a Chambers of Xeric (Trio) in less than 14 minutes and 30 seconds.",
		"chambers of xeric 5 players;M;15:00;<;;Complete a Chambers of Xeric (5-scale) in less than 15 minutes.",
		"chambers of xeric 5 players;GM;12:30;<;;Complete a Chambers of Xeric (5-scale) in less than 12 minutes and 30 seconds.",
		"chambers of xeric challenge mode solo;E;1:10:00;=;;Complete a Chambers of Xeric Challenge mode raid in the target time (solo: 1 hour 10 minutes).",
		"chambers of xeric challenge mode 2 players;E;1:05:00;=;;Complete a Chambers of Xeric Challenge mode raid in the target time (2 players: 1 hour 5 minutes).",
		"chambers of xeric challenge mode 3 players;E;50:00;=;;Complete a Chambers of Xeric Challenge mode raid in the target time (3 players: 50 minutes).",
		"chambers of xeric challenge mode 4 players;E;45:00;=;;Complete a Chambers of Xeric Challenge mode raid in the target time (4 players: 45 minutes).",
		"chambers of xeric challenge mode 5 players|chambers of xeric challenge mode 6 players|chambers of xeric challenge mode 7 players"
			+ "|chambers of xeric challenge mode 8 players|chambers of xeric challenge mode 9 players|chambers of xeric challenge mode 10 players"
			+ ";E;42:00;=;;Complete a Chambers of Xeric Challenge mode raid in the target time (5-10 players: 42 minutes).",
		"chambers of xeric challenge mode 11-15 players;E;45:00;=;;Complete a Chambers of Xeric Challenge mode raid in the target time (11-15 players: 45 minutes).",
		"chambers of xeric challenge mode 16-23 players;E;1:00:00;=;;Complete a Chambers of Xeric Challenge mode raid in the target time (16-23 players: 1 hour).",
		"chambers of xeric challenge mode 24+ players;E;1:20:00;=;;Complete a Chambers of Xeric Challenge mode raid in the target time (24+ players: 1 hour 20 minutes).",
		"chambers of xeric challenge mode solo;M;45:00;<;;Complete a Chambers of Xeric: Challenge Mode (Solo) in less than 45 minutes.",
		"chambers of xeric challenge mode solo;GM;38:30;<;;Complete a Chambers of Xeric: Challenge Mode (Solo) in less than 38 minutes and 30 seconds.",
		"chambers of xeric challenge mode 3 players;M;35:00;<;;Complete a Chambers of Xeric: Challenge Mode (Trio) in less than 35 minutes.",
		"chambers of xeric challenge mode 3 players;GM;27:00;<;;Complete a Chambers of Xeric: Challenge Mode (Trio) in less than 27 minutes.",
		"chambers of xeric challenge mode 5 players;M;30:00;<;;Complete a Chambers of Xeric: Challenge Mode (5-scale) in less than 30 minutes.",
		"chambers of xeric challenge mode 5 players;GM;25:00;<;;Complete a Chambers of Xeric: Challenge Mode (5-scale) in less than 25 minutes.",
		// Theatre of Blood
		"theatre of blood entry mode *;M;17:00;<;;Complete the Theatre of Blood: Entry Mode in less than 17 minutes.",
		"theatre of blood 2 players;GM;26:00;<;;Complete the Theatre of Blood (Duo) in less than 26 minutes.",
		"theatre of blood 3 players;M;20:00;<;;Complete the Theatre of Blood (Trio) in less than 20 minutes.",
		"theatre of blood 3 players;GM;17:30;<;;Complete the Theatre of Blood (Trio) in less than 17 minutes and 30 seconds.",
		"theatre of blood 4 players;M;17:00;<;;Complete the Theatre of Blood (4-scale) in less than 17 minutes.",
		"theatre of blood 4 players;GM;15:00;<;;Complete the Theatre of Blood (4-scale) in less than 15 minutes.",
		"theatre of blood 5 players;M;16:00;<;;Complete the Theatre of Blood (5-scale) in less than 16 minutes.",
		"theatre of blood 5 players;GM;14:15;<;;Complete the Theatre of Blood (5-scale) in less than 14 minutes and 15 seconds.",
		"theatre of blood hard mode solo;M;1:10:00;=;;Complete the Theatre of Blood: Hard Mode within the challenge time (solo: 1:10:00).",
		"theatre of blood hard mode 2 players;M;35:00;=;;Complete the Theatre of Blood: Hard Mode within the challenge time (2 players: 35:00).",
		"theatre of blood hard mode 3 players;M;30:00;=;;Complete the Theatre of Blood: Hard Mode within the challenge time (3 players: 30:00).",
		"theatre of blood hard mode 4 players;M;27:00;=;;Complete the Theatre of Blood: Hard Mode within the challenge time (4 players: 27:00).",
		"theatre of blood hard mode 5 players;M;25:00;=;;Complete the Theatre of Blood: Hard Mode within the challenge time (5 players: 25:00).",
		"theatre of blood hard mode 3 players;GM;23:00;<;;Complete the Theatre of Blood: Hard Mode (Trio) with an overall time of less than 23 minutes.",
		"theatre of blood hard mode 4 players;GM;21:00;<;;Complete the Theatre of Blood: Hard Mode (4-scale) with an overall time of less than 21 minutes.",
		"theatre of blood hard mode 5 players;GM;19:00;<;;Complete the Theatre of Blood: Hard Mode (5-scale) with an overall time of less than 19 minutes.",
		// Tombs of Amascut
		"tombs of amascut *;M;18:00;=;;Complete the Tombs of Amascut (normal) within 18 mins at any group size.",
		"tombs of amascut expert mode *;GM;20:00;=;;Complete the Tombs of Amascut (expert) within 20 mins at any group size.",
		"tombs of amascut expert mode 8 players;GM;18:00;=;group of 8;Complete the Tombs of Amascut (expert) within 18 mins in a group of 8.",
	};

	private static final class Row
	{
		final String[] keys;
		final Task task;

		Row(String[] keys, Task task)
		{
			this.keys = keys;
			this.task = task;
		}
	}

	private static final List<Row> ROWS = new ArrayList<>();

	static
	{
		for (String d : DATA)
		{
			String[] f = d.split(";", -1);
			int tier = java.util.Arrays.asList(SHORT).indexOf(f[1]);
			double secs = Times.parse(f[2]);
			if (f.length != 6 || tier < 0 || secs <= 0)
			{
				throw new IllegalStateException("speed task row: " + d);
			}
			ROWS.add(new Row(f[0].split("\\|"), new Task(tier, secs, f[3].startsWith("<"), !f[3].endsWith("?"), f[4], f[5])));
		}
	}

	/** the speed tasks of a PB (RuneLite's name for it), easiest first: by tier, then the slower time first; empty if none */
	static List<Task> of(String key)
	{
		if (key == null)
		{
			return Collections.emptyList();
		}
		String k = key.toLowerCase(Locale.ROOT).replace('’', '\'').trim();
		List<Task> out = new ArrayList<>();
		for (Row r : ROWS)
		{
			for (String s : r.keys)
			{
				if (matches(k, s))
				{
					out.add(r.task);
					break;
				}
			}
		}
		out.sort(Comparator.<Task>comparingInt(t -> t.tier).thenComparing(t -> -t.secs));
		return out;
	}

	/** "tombs of amascut *" = that raid and mode at any team size (and its best at any size) */
	private static boolean matches(String key, String spec)
	{
		if (!spec.endsWith(" *"))
		{
			return key.equals(spec);
		}
		String base = spec.substring(0, spec.length() - 2);
		if (key.equals(base))
		{
			return true;
		}
		History.Raid a = History.raid(key), b = History.raid(base);
		return a != null && b != null && a.raid == b.raid && a.mode.equals(b.mode);
	}

	/** the hardest tier a PB surely beats; null = none */
	static Task best(List<Task> tasks, double pb)
	{
		Task top = null;
		for (Task t : tasks)
		{
			if (t.sure && t.beatenBy(pb) && (top == null || t.tier >= top.tier))
			{
				top = t;
			}
		}
		return top;
	}

	/** the next one to beat: the easiest the PB doesn't beat yet (of those PB History can tell); null = none left */
	static Task next(List<Task> tasks, double pb)
	{
		for (Task t : tasks)
		{
			if (t.sure && !t.beatenBy(pb))
			{
				return t;
			}
		}
		return null;
	}

	/** any task PB History can tell */
	static boolean anySure(List<Task> tasks)
	{
		for (Task t : tasks)
		{
			if (t.sure)
			{
				return true;
			}
		}
		return false;
	}

	/** how far off a time is: 4.2 → "4.20s", 75 → "1:15" */
	static String gap(double secs)
	{
		return secs < 60 ? String.format(Locale.ROOT, "%.2fs", secs) : Times.format(secs);
	}

	/**
	 * The badge after a boss's name: the hardest tier its PB beats, filled; hollow = none yet (the easiest tier shown); null
	 * when it has no speed tasks PB History can tell.
	 */
	static Icon badge(List<Task> tasks, double pb)
	{
		if (!anySure(tasks))
		{
			return null;
		}
		Task top = best(tasks, pb);
		return new Chip(top != null ? top.tier : tasks.get(0).tier, top != null);
	}

	/** a tier's letters in a small rounded box: filled = done, hollow = not yet */
	static final class Chip implements Icon
	{
		private static final FontMetrics FM = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
			.createGraphics().getFontMetrics(FontManager.getRunescapeSmallFont());
		final int tier;
		final boolean filled;

		Chip(int tier, boolean filled)
		{
			this.tier = tier;
			this.filled = filled;
		}

		@Override
		public int getIconWidth()
		{
			return FM.stringWidth(SHORT[tier]) + 6;
		}

		@Override
		public int getIconHeight()
		{
			return 11;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Color col = COLOURS[tier];
			int w = getIconWidth(), h = getIconHeight();
			if (filled)
			{
				g2.setColor(col);
				g2.fillRoundRect(x, y, w, h, 4, 4);
			}
			else
			{
				g2.setColor(col);
				g2.drawRoundRect(x, y, w - 1, h - 1, 4, 4);
			}
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			Font f = FontManager.getRunescapeSmallFont();
			g2.setFont(f);
			g2.setColor(filled ? new Color(18, 18, 18) : col);
			FontMetrics fm = g2.getFontMetrics();
			g2.drawString(SHORT[tier], x + 3, y + (h - fm.getHeight()) / 2 + fm.getAscent());
			g2.dispose();
		}
	}

	/** a small green tick: the PB beats this time */
	static final class Tick implements Icon
	{
		@Override
		public int getIconWidth()
		{
			return 9;
		}

		@Override
		public int getIconHeight()
		{
			return 8;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setStroke(new java.awt.BasicStroke(2f));
			g2.setColor(new Color(0x4FD16A));
			g2.drawPolyline(new int[]{x + 1, x + 3, x + 8}, new int[]{y + 4, y + 7, y + 1}, 3);
			g2.dispose();
		}
	}
}
