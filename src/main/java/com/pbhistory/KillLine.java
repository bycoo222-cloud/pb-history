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
 * The chat line after a timed kill (user 2026-11-17, after the mockup: "Yes build that"): how a kill went against your
 * PB and your other kills, so PB History says something while you play, not only in its panel.
 * <ul>
 * <li>a kill: "2.40s off your PB, 3rd fastest of 44, average 1:31.40"
 * <li>a new PB: "new PB by 1.20s, Grandmaster speed task: 3.00s to go" (or "fast enough for the Grandmaster speed task")
 * </ul>
 * Commas, not the mockup's middle dots: plain characters the game's chat font surely has.
 */
final class KillLine
{
	/** fewer timed kills than this: no rank or average (they'd say nothing yet) */
	static final int MIN_KILLS = 3;

	/**
	 * After a kill that set no PB. {@code pb} = the PB (0 = none known); {@code times} = every timed kill of this PB,
	 * this one included. Null = nothing to say.
	 */
	static String kill(double secs, double pb, double[] times)
	{
		boolean precise = precise(secs) || precise(pb);
		for (double t : times)
		{
			precise |= precise(t);
		}
		List<String> parts = new ArrayList<>();
		if (pb > 0)
		{
			double off = secs - pb;
			if (Math.abs(off) < 0.005)
			{
				parts.add("tied your PB");
			}
			else if (off > 0)
			{
				parts.add(SpeedTasks.gap(off) + " off your PB");
			}
		}
		if (times.length >= MIN_KILLS)
		{
			int faster = 0;
			double sum = 0;
			for (double t : times)
			{
				faster += t < secs - 0.005 ? 1 : 0;
				sum += t;
			}
			parts.add((faster == 0 ? "" : ordinal(faster + 1) + " ") + "fastest of " + times.length);
			parts.add("average " + Times.format(sum / times.length, precise));
		}
		return parts.isEmpty() ? null : String.join(", ", parts);
	}

	/** After a new PB of {@code now} seconds ({@code was} = the PB it beat, 0 = none) with these speed tasks. */
	static String pb(double now, double was, List<SpeedTasks.Task> tasks)
	{
		List<String> parts = new ArrayList<>();
		parts.add(was > now + 0.005 ? "new PB by " + SpeedTasks.gap(was - now) : "new PB");
		SpeedTasks.Task got = null;
		for (SpeedTasks.Task t : tasks)
		{
			if (t.sure && t.beatenBy(now) && !t.beatenBy(was) && (got == null || t.tier >= got.tier))
			{
				got = t;
			}
		}
		SpeedTasks.Task next = SpeedTasks.next(tasks, now);
		if (got != null)
		{
			parts.add("fast enough for the " + SpeedTasks.TIERS[got.tier] + " speed task");
		}
		else if (next != null)
		{
			parts.add(SpeedTasks.TIERS[next.tier] + " speed task: " + SpeedTasks.gap(now - next.secs) + " to go");
		}
		return String.join(", ", parts);
	}

	/** 1 → "1st", 2 → "2nd", 11 → "11th", 23 → "23rd" */
	static String ordinal(int n)
	{
		int ten = n % 100;
		String end = ten >= 11 && ten <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
		return n + end;
	}

	private static boolean precise(double secs)
	{
		return Math.abs(secs * 100 - Math.round(secs) * 100) > 0.5;
	}
}
