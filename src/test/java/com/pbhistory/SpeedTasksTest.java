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

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Combat Achievement speed tasks by RuneLite's PB name, and the chat line after a kill. Times = the OSRS Wiki's. */
public class SpeedTasksTest
{
	private static String tiers(List<SpeedTasks.Task> l)
	{
		StringBuilder b = new StringBuilder();
		for (SpeedTasks.Task t : l)
		{
			b.append(b.length() == 0 ? "" : " ").append(SpeedTasks.SHORT[t.tier]).append(' ').append(Times.format(t.secs));
		}
		return b.toString();
	}

	@Test
	public void vorkath()
	{
		List<SpeedTasks.Task> v = SpeedTasks.of("vorkath");
		assertEquals("M 1:15 GM 0:54", tiers(v));
		assertNull(SpeedTasks.best(v, 85.2));
		assertEquals(2, SpeedTasks.next(v, 85.2).tier);
		assertEquals(2, SpeedTasks.best(v, 70).tier);
		assertEquals(3, SpeedTasks.next(v, 70).tier);
		// "in less than 54 seconds": 54.00 itself doesn't count
		assertEquals(2, SpeedTasks.best(v, 54.0).tier);
		assertEquals(3, SpeedTasks.best(v, 53.4).tier);
		assertNull(SpeedTasks.next(v, 53.4));
		// no PB: nothing beaten
		assertNull(SpeedTasks.best(v, 0));
	}

	@Test
	public void withinCountsTheTimeItself()
	{
		List<SpeedTasks.Task> p = SpeedTasks.of("phosani's nightmare");
		assertEquals("M 7:30 GM 6:00", tiers(p));
		assertEquals(2, SpeedTasks.best(p, 450.0).tier);
		// the game's apostrophe
		assertEquals("M 7:30 GM 6:00", tiers(SpeedTasks.of("Phosani’s Nightmare")));
	}

	@Test
	public void raidsByTeamSizeAndMode()
	{
		assertEquals("M 18:00", tiers(SpeedTasks.of("tombs of amascut 3 players")));
		assertEquals("M 18:00", tiers(SpeedTasks.of("tombs of amascut solo")));
		assertEquals("M 18:00", tiers(SpeedTasks.of("tombs of amascut")));
		assertEquals("", tiers(SpeedTasks.of("tombs of amascut entry mode 3 players")));
		assertEquals("GM 20:00", tiers(SpeedTasks.of("tombs of amascut expert mode 2 players")));
		assertEquals("GM 20:00 GM 18:00", tiers(SpeedTasks.of("tombs of amascut expert mode 8 players")));
		assertEquals("M 17:00", tiers(SpeedTasks.of("theatre of blood entry mode")));
		assertEquals("M 17:00", tiers(SpeedTasks.of("theatre of blood entry mode 3 players")));
		assertEquals("", tiers(SpeedTasks.of("theatre of blood")));   // the best at any size: no task is for every size
		assertEquals("M 17:00 GM 15:00", tiers(SpeedTasks.of("theatre of blood 4 players")));
		assertEquals("GM 26:00", tiers(SpeedTasks.of("theatre of blood 2 players")));
		assertEquals("M 27:00 GM 21:00", tiers(SpeedTasks.of("theatre of blood hard mode 4 players")));
		assertEquals("M 1:10:00", tiers(SpeedTasks.of("theatre of blood hard mode solo")));
		assertEquals("M 16:30 GM 14:30", tiers(SpeedTasks.of("chambers of xeric 3 players")));
		assertEquals("", tiers(SpeedTasks.of("chambers of xeric 2 players")));
		assertEquals("E 42:00", tiers(SpeedTasks.of("chambers of xeric challenge mode 7 players")));
		assertEquals("E 50:00 M 35:00 GM 27:00", tiers(SpeedTasks.of("chambers of xeric challenge mode 3 players")));
		assertEquals("E 1:20:00", tiers(SpeedTasks.of("chambers of xeric challenge mode 24+ players")));
		// The Fractured Archive: no speed tasks on the wiki's list yet
		assertEquals("", tiers(SpeedTasks.of("fractured archive")));
	}

	@Test
	public void teamSizeRuneLiteDoesNotKeep()
	{
		// one Nightmare PB for every team size: nothing can be told, so no badge and no "next"
		List<SpeedTasks.Task> n = SpeedTasks.of("nightmare");
		assertEquals(6, n.size());
		assertFalse(SpeedTasks.anySure(n));
		assertNull(SpeedTasks.best(n, 200));
		assertNull(SpeedTasks.next(n, 200));
		assertTrue(n.get(0).beatenBy(200));   // the time is beaten (shown with a "?")
		// the Hueycoatl: Elite is any size, Master and Grandmaster need a team size
		List<SpeedTasks.Task> h = SpeedTasks.of("hueycoatl");
		assertEquals("E 2:30 M 2:30 GM 2:30", tiers(h));
		assertEquals(1, SpeedTasks.best(h, 140).tier);
		assertNull(SpeedTasks.next(h, 140));
		assertEquals(1, SpeedTasks.next(h, 151).tier);
	}

	@Test
	public void bossesWithoutTasks()
	{
		assertTrue(SpeedTasks.of("colossal wyrm agility course (basic)").isEmpty());
		assertTrue(SpeedTasks.of("tempoross").isEmpty());
		assertTrue(SpeedTasks.of(null).isEmpty());
		assertEquals("M 30:00 GM 26:30", tiers(SpeedTasks.of("tztok-jad")));
		assertEquals("E 0:45", tiers(SpeedTasks.of("tzhaar-ket-rak's first challenge")));
		assertEquals("E 0:02", tiers(SpeedTasks.of("brutus")));
	}

	@Test
	public void lineAfterAKill()
	{
		// the mockup's normal kill: 44 timed kills, two faster, average 1:31.40
		double[] t = new double[44];
		t[0] = 85.2;
		t[1] = 86.0;
		t[2] = 87.6;
		for (int i = 3; i < 43; i++)
		{
			t[i] = 91.8;
		}
		t[43] = 90.8;
		assertEquals("2.40s off your PB, 3rd fastest of 44, average 1:31.40", KillLine.kill(87.6, 85.2, t));
		assertEquals("tied your PB, fastest of 44, average 1:31.40", KillLine.kill(85.2, 85.2, t));
		// too few kills for a rank
		assertEquals("5.00s off your PB", KillLine.kill(90, 85, new double[]{90}));
		assertNull(KillLine.kill(60, 0, new double[]{60}));
		assertEquals("1.00s off your PB, fastest of 3, average 1:30.33", KillLine.kill(86, 85, new double[]{86, 90, 95}));
		// over a minute off: minutes
		assertEquals("1:05 off your PB", KillLine.kill(1265, 1200, new double[]{1265}));
	}

	@Test
	public void lineAfterANewPb()
	{
		List<SpeedTasks.Task> v = SpeedTasks.of("vorkath");
		assertEquals("new PB by 1.20s, Master speed task: 9.00s to go", KillLine.pb(84.0, 85.2, v));
		assertEquals("new PB by 1.60s, fast enough for the Master speed task", KillLine.pb(74.4, 76.0, v));
		assertEquals("new PB by 3.00s, Grandmaster speed task: 3.00s to go", KillLine.pb(57.0, 60.0, v));
		assertEquals("new PB by 20.00s, fast enough for the Grandmaster speed task", KillLine.pb(53.4, 73.4, v));
		assertEquals("new PB", KillLine.pb(120, 0, Collections.emptyList()));
		assertEquals("new PB, Master speed task: 45.00s to go", KillLine.pb(120, 0, v));
		// a team size it can't tell: never "fast enough"
		assertEquals("new PB by 10.00s", KillLine.pb(200, 210, SpeedTasks.of("nightmare")));
	}

	@Test
	public void ordinals()
	{
		String[] want = {"1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "101st", "111th"};
		int[] n = {1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 101, 111};
		for (int i = 0; i < n.length; i++)
		{
			assertEquals(want[i], KillLine.ordinal(n[i]));
		}
	}
}
