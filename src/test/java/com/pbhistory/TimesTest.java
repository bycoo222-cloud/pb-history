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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class TimesTest
{
	@Test
	public void killTimesInGameMessages()
	{
		// the message shapes RuneLite's Chat Commands plugin reads (client 1.13.6's patterns)
		assertArrayEquals(new double[]{83.4, 1}, Times.duration("Fight duration: <col=ff0000>1:23.40</col> (new personal best)"), 1e-9);
		assertArrayEquals(new double[]{85.8, 0}, Times.duration("Fight duration: <col=ff0000>1:25.80</col>. Personal best: 1:23.40"), 1e-9);
		assertArrayEquals(new double[]{64, 0}, Times.duration("Lap duration: <col=ff0000>1:04</col>. Personal best: 1:00"), 1e-9);
		assertArrayEquals(new double[]{1500, 1}, Times.duration("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: "
			+ "<col=ff0000>Solo</col> Duration:</col> <col=ff0000>25:00</col> (new personal best)</col>"), 1e-9);
		assertNull(Times.duration("Your Vorkath kill count is: <col=ff0000>851</col>."));
	}

	@Test
	public void timesReadAndWritten()
	{
		assertEquals(83.4, Times.parse("1:23.40"), 1e-9);
		assertEquals(3723, Times.parse("1:02:03"), 1e-9);
		assertEquals("1:23.40", Times.format(83.4));
		assertEquals("1:02:03", Times.format(3723));
		assertEquals("0:45", Times.format(45));
		assertEquals("2.40s faster", Times.faster(2.4));
		assertEquals("Tombs of Amascut Expert Mode 2 Players", Times.name("tombs of amascut expert mode 2 players"));
		assertEquals("Colossal Wyrm Agility Course (Advanced)", Times.name("colossal wyrm agility course (advanced)"));
		assertEquals("TzTok-Jad", Times.name("tztok-jad"));
		assertEquals("TzHaar-Ket-Rak's First Challenge", Times.name("tzhaar-ket-rak's first challenge"));
	}

	@Test
	public void tabs()
	{
		assertEquals(History.RAIDS, History.tab("tombs of amascut expert mode 2 players"));
		assertEquals(History.RAIDS, History.tab("chambers of xeric challenge mode"));
		assertEquals(History.RAIDS, History.tab("theatre of blood entry mode 5 players"));
		assertEquals(History.CHALLENGES, History.tab("tzkal-zuk"));
		assertEquals(History.CHALLENGES, History.tab("tztok-jad"));
		assertEquals(History.CHALLENGES, History.tab("sol heredit"));
		assertEquals(History.CHALLENGES, History.tab("tzhaar-ket-rak's first challenge"));
		assertEquals(History.CHALLENGES, History.tab("corrupted gauntlet"));
		assertEquals(History.OTHER, History.tab("colossal wyrm agility course (advanced)"));
		assertEquals(History.OTHER, History.tab("tempoross"));
		assertEquals(History.BOSSES, History.tab("vorkath"));
		assertEquals(History.BOSSES, History.tab("nightmare"));
		assertEquals(History.BOSSES, History.tab("hueycoatl"));
	}

	@Test
	public void raids()
	{
		// the user's real saved PB names (RuneLite's Chat Commands)
		History.Raid r = History.raid("tombs of amascut expert mode 2 players");
		assertEquals(2, r.raid);
		assertEquals("expert mode", r.mode);
		assertEquals("2 players", r.size);
		r = History.raid("tombs of amascut solo");
		assertEquals("", r.mode);
		assertEquals("solo", r.size);
		r = History.raid("chambers of xeric challenge mode");
		assertEquals(0, r.raid);
		assertEquals("challenge mode", r.mode);
		assertEquals("", r.size);
		r = History.raid("theatre of blood entry mode 5 players");
		assertEquals(1, r.raid);
		assertEquals("entry mode", r.mode);
		assertEquals("5 players", r.size);
		assertEquals("Regular", History.modeName(0, ""));
		assertEquals("Expert", History.modeName(2, "expert mode"));
		assertEquals(1, History.sizeOrder("solo"));
		assertEquals(10, History.sizeOrder("10 players"));
		assertEquals(null, History.raid("vorkath"));
	}

	@Test
	public void lastKillAgainstPb()
	{
		// kills 95, 90 (a new PB, with RuneLite's PB line for it 600 ms earlier), then 97 (missed by 7)
		List<Entry> lines = Arrays.asList(Entry.kill("vorkath", 95, 1000, 10), Entry.pb("vorkath", 90, 95, 1_999_400, 11, true),
			Entry.kill("vorkath", 90, 2_000_000, 11), Entry.kill("vorkath", 97, 3_000_000, 12));
		History.Boss b = History.build(lines, new HashMap<>(), new HashMap<>()).get(0);
		assertEquals(7.0, PbPanel.lastDelta(b), 1e-9);              // +7.00: slower than the PB
		assertEquals("+7.00", PbPanel.delta(7));
		List<Entry> pbKill = Arrays.asList(Entry.kill("zulrah", 60, 1000, 1), Entry.pb("zulrah", 55.8, 60, 1_999_400, 2, true),
			Entry.kill("zulrah", 55.8, 2_000_000, 2));
		History.Boss z = History.build(pbKill, new HashMap<>(), new HashMap<>()).get(0);
		assertEquals(-4.2, PbPanel.lastDelta(z), 1e-9);             // -4.20: a new PB (its own PB line is not "before")
		assertEquals("-4.20", PbPanel.delta(-4.2));
		History.Boss only = History.build(List.of(), java.util.Map.of("tempoross", 343.0), new HashMap<>()).get(0);
		org.junit.Assert.assertTrue(Double.isNaN(PbPanel.lastDelta(only)));   // no timed kill: blank
	}

	@Test
	public void bossIcons()
	{
		// the user's real PB names → RuneLite's hiscore entries (their sprites are the icons)
		String[][] want = {{"vorkath", "VORKATH"}, {"tztok-jad", "TZTOK_JAD"}, {"tzkal-zuk", "TZKAL_ZUK"},
			{"tombs of amascut expert mode 2 players", "TOMBS_OF_AMASCUT_EXPERT"}, {"tombs of amascut solo", "TOMBS_OF_AMASCUT"},
			{"tombs of amascut entry mode solo", "TOMBS_OF_AMASCUT"}, {"chambers of xeric challenge mode 3 players",
			"CHAMBERS_OF_XERIC_CHALLENGE_MODE"}, {"chambers of xeric 10 players", "CHAMBERS_OF_XERIC"},
			{"theatre of blood entry mode 3 players", "THEATRE_OF_BLOOD"}, {"tempoross", "TEMPOROSS"}};
		StringBuilder bad = new StringBuilder();
		for (String[] w : want)
		{
			net.runelite.client.hiscore.HiscoreSkill hs = PbHistoryPlugin.hiscoreFor(w[0]);
			if (hs == null || !hs.name().equals(w[1]))
			{
				bad.append(w[0]).append(" -> ").append(hs).append("; ");
			}
		}
		for (String k : new String[]{"nightmare", "hueycoatl", "sol heredit", "brutus", "tzhaar-ket-rak's first challenge"})
		{
			System.out.println("icon for " + k + ": " + PbHistoryPlugin.hiscoreFor(k));
		}
		assertEquals("", bad.toString());
	}

	@Test
	public void history()
	{
		List<Entry> lines = Arrays.asList(
			Entry.kill("vorkath", 95, 1000, 10), Entry.pb("vorkath", 90, 100, 2000, 11, true),
			Entry.kill("vorkath", 90, 2000, 11), Entry.kill("vorkath", 97, 3000, 12));
		Map<String, Double> stored = new HashMap<>();
		stored.put("vorkath", 90.0);       // RuneLite's saved PB = the one already in the lines: no extra
		stored.put("nightmare", 266.0);    // no lines: a PB from before PB History
		List<History.Boss> b = History.build(lines, stored, new HashMap<>());
		assertEquals("vorkath", b.get(0).key);              // latest activity first
		assertEquals(90, b.get(0).best(), 1e-9);
		assertEquals(1, b.get(0).steps().size());
		assertEquals(12, b.get(0).kc);
		assertEquals(94, b.get(0).average(0), 1e-9);
		assertEquals("nightmare", b.get(1).key);
		assertEquals(0, b.get(1).bestAt());                 // no date
	}
}
