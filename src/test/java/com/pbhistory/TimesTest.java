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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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

	@Test
	public void pbPictureMatchesTheKillThatSetIt()
	{
		java.time.ZoneId z = java.time.ZoneId.of("America/New_York");
		// the user's real CoX CM PB (11-07 22:33:29.809, kc 4) and RuneLite's screenshot of that raid
		Pictures.Shot shot = Pictures.bossShot("/s/bycoo222/Boss Kills/Chambers of Xeric Challenge Mode(4) 2026-11-07_22-33-42.png", z);
		assertNotNull(shot);
		assertEquals("chambers of xeric challenge mode", shot.boss);
		assertEquals(4, shot.kc);
		Entry mode = Entry.pb("chambers of xeric challenge mode", 2614, 2702, 1794108809809L, 4, true);
		Entry size = Entry.pb("chambers of xeric challenge mode 3 players", 2614, 2702, 1794108809812L, 4, true);
		assertTrue(Pictures.sameKill(shot, mode));
		assertTrue(Pictures.sameKill(shot, size));
		assertFalse(Pictures.sameKill(shot, Entry.pb("chambers of xeric", 1800, 1900, 1794108809809L, 4, true)));    // regular CoX
		assertFalse(Pictures.sameKill(shot, Entry.pb("chambers of xeric challenge mode", 2614, 2702, 1794108809809L, 5, true))); // another kill
		assertFalse(Pictures.sameKill(shot, Entry.pb("chambers of xeric challenge mode", 2614, 2702, 1794108809809L - 3_600_000L, 4, true)));
		assertNull(Pictures.bossShot("/s/bycoo222/Deaths/Death 2026-11-07_22-29-19.png", z));
		assertEquals("theatre of blood hard mode", Pictures.norm("Theatre of Blood: Hard Mode"));
	}

	@Test
	public void livePictureFromTheGameEvent()
	{
		// the CM raid: its count message, then the reward chest opens 13 s later and RuneLite takes the picture
		ShotNamer n = new ShotNamer(new ShotNamerTest.Fake().with("boss", true));
		n.chat("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>4</col>.", 1794108809000L, true, true, false, 1, false);
		n.widget(net.runelite.api.gameval.InterfaceID.RAIDS_REWARDS, 1794108822000L);
		ShotNamer.Name name = n.take(1794108822400L);
		assertEquals(ShotNamer.BOSS_KILLS, name.type);
		Pictures.Shot shot = Pictures.named(name.file, name.at);
		assertNotNull(shot);
		assertEquals("chambers of xeric challenge mode", shot.boss);
		assertEquals(4, shot.kc);
		assertTrue(Pictures.sameKill(shot, Entry.pb("chambers of xeric challenge mode 3 players", 2614, 2702, 1794108809812L, 4, true)));
		// a boss kill from its kill-count message
		n.chat("Your Vorkath kill count is: <col=ff0000>852</col>.", 2_000_000L, true, true, false, 1, false);
		Pictures.Shot vork = Pictures.named(n.take(2_000_300L).file, 2_000_000L);
		assertTrue(Pictures.sameKill(vork, Entry.pb("vorkath", 85.8, 90.0, 1_999_999L, 852, true)));
		assertNull(Pictures.named("Death", 1L));
		assertNull(Pictures.named("", 1L));
	}

	@Test
	public void importMatchesOldScreenshotsToRecordedPbs()
	{
		java.time.ZoneId z = java.time.ZoneId.of("America/New_York");
		Map<String, Pictures.Shot> shots = new java.util.LinkedHashMap<>();
		for (String f : new String[]{
			"Chambers of Xeric Challenge Mode(4) 2026-11-07_22-33-42.png",   // the kill that set the CM PB
			"Chambers of Xeric Challenge Mode(3) 2026-11-06_21-10-00.png",   // an earlier raid: not a PB
			"Vorkath(850) 2026-11-07_20-00-05.png"})                         // no Vorkath PB recorded that day
		{
			shots.put(f, Pictures.bossShot("Boss Kills/" + f, z));
		}
		List<Entry> lines = Arrays.asList(
			Entry.pb("chambers of xeric challenge mode", 2614, 2702, 1794108809809L, 4, true),
			Entry.pb("chambers of xeric challenge mode 3 players", 2614, 2702, 1794108809812L, 4, true),
			Entry.kill("chambers of xeric challenge mode 3 players", 2614, 1794108810425L, 4),
			Entry.pb("vorkath", 85.8, 90.0, 0, 0, false));                  // from before PB History: no time to match
		Map<Long, String> got = Pictures.match(lines, shots, new java.util.HashSet<>());
		assertEquals(2, got.size());                                         // a raid's PB is saved twice: both get it
		assertEquals("Chambers of Xeric Challenge Mode(4) 2026-11-07_22-33-42.png", got.get(1794108809809L));
		assertEquals("Chambers of Xeric Challenge Mode(4) 2026-11-07_22-33-42.png", got.get(1794108809812L));
		// a PB that already has its picture is left alone
		assertEquals(1, Pictures.match(lines, shots, new java.util.HashSet<>(Arrays.asList(1794108809809L))).size());
	}

	@Test
	public void consistency()
	{
		History.Boss b = new History.Boss("vorkath");
		for (double t : new double[]{120, 125, 140, 160, 118})
		{
			b.kills.add(Entry.kill("vorkath", t, 1, 0));
		}
		assertEquals(118, b.best(), 1e-9);
		assertEquals(2, b.within(0.05));      // within 5% of 118 = up to 123.9: 118 and 120 (125 is not)
		assertEquals(125, b.median(), 1e-9);
	}

	@Test
	public void roomMessages()
	{
		// the game's own messages, as in the Plugin Hub's Raid Data Tracker tests
		Object[] cox = Rooms.parse("Combat room 'Vanguards' complete! Duration: 3:19 Total: 16:16");
		assertEquals("Vanguards", cox[0]);
		assertEquals(199.0, (double) cox[1], 1e-9);
		Object[] tob = Rooms.parse("Wave 'The Final Challenge' (Normal Mode) complete!<br>Duration: <col=ff0000>5:47.40</col><br>Theatre of Blood completion time: <col=ff0000>21:49.80</col> (new personal best)");
		assertEquals("The Final Challenge", tob[0]);
		assertEquals(347.4, (double) tob[1], 1e-9);
		Object[] toa = Rooms.parse("Challenge complete: Path of Crondis. Duration: 0:34.20. Total: 0:34.20");
		assertEquals("Path of Crondis", toa[0]);
		assertEquals(34.2, (double) toa[1], 1e-9);
		Object[] wardens = Rooms.parse("Challenge complete: The Wardens. Duration: 10:15.60Tombs of Amascut: Expert Mode challenge completion time: 34:07.20. Personal best: 28:16.20");
		assertEquals("The Wardens", wardens[0]);
		assertEquals(615.6, (double) wardens[1], 1e-9);
		assertNull(Rooms.parse("Middle level complete! Duration: 7:53 Total: 20:50"));   // a floor, not a room
		assertNull(Rooms.parse("Congratulations - your raid is complete!"));
		assertEquals("tombs of amascut expert mode", Rooms.mode("tombs of amascut expert mode 3 players"));
		assertEquals("chambers of xeric challenge mode", Rooms.mode("chambers of xeric challenge mode solo"));
		assertEquals("chambers of xeric", Rooms.mode("chambers of xeric 11-15 players"));
		assertEquals("theatre of blood hard mode", Rooms.mode("theatre of blood hard mode 4 players"));
		assertEquals("Verzik", Rooms.label("The Final Challenge"));

		// rooms are filed under the mode, and never become a boss of their own
		List<Entry> lines = new java.util.ArrayList<>();
		lines.add(Entry.pb("theatre of blood 4 players", 1500, 0, 10, 1, true));
		lines.add(Entry.room("theatre of blood", "Xarpus", 120, 9, 1));
		List<History.Boss> bosses = History.build(lines, new HashMap<>(), new HashMap<>());
		assertEquals(1, bosses.size());
		assertEquals(1, bosses.get(0).rooms.size());
		assertEquals(0, bosses.get(0).kills.size());
	}

	/**
	 * The Theatre of Blood's and the Tombs of Amascut's last room message carries the raid's own time AFTER the room's
	 * "Duration:" — the raid's kill time is the raid's, never the last room's. Messages as in RuneLite's
	 * ChatCommandsPluginTest (testTheatreOfBlood, testTheatreOfBloodNoPb, testToA*, testTheFracturedArchive).
	 */
	@Test
	public void raidKillTimeIsTheRaidsNotTheLastRooms()
	{
		assertArrayEquals(new double[]{1020.2, 1}, Times.duration("Wave 'The Final Challenge' (Normal Mode) complete!<br>"
			+ "Duration: <col=ff0000>2:42.0</col><br>Theatre of Blood completion time: <col=ff0000>17:00.20</col> (new personal best)"), 1e-9);
		assertArrayEquals(new double[]{1020, 0}, Times.duration("Wave 'The Final Challenge' (Normal Mode) complete!<br>"
			+ "Duration: <col=ff0000>2:42</col><br>Theatre of Blood completion time: <col=ff0000>17:00</col>. Personal best: 13:52.80"), 1e-9);
		assertArrayEquals(new double[]{511, 1}, Times.duration("Challenge complete: The Wardens. Duration: <col=ef1020>8:30</col><br>"
			+ "Tombs of Amascut challenge completion time: <col=ef1020>8:31</col> (new personal best)"), 1e-9);
		assertArrayEquals(new double[]{580, 0}, Times.duration("Challenge complete: The Wardens. Duration: <col=ef1020>9:40</col><br>"
			+ "Tombs of Amascut: Expert Mode challenge completion time: <col=ef1020>9:40</col>. Personal best: 8:31"), 1e-9);
		// the "total completion time" that follows is not the kill time (RuneLite's PB is the completion time) ...
		assertNull(Times.duration("Theatre of Blood total completion time: <col=ff0000>24:40.20</col>. Personal best: 20:45.00"));
		// ... but it is how long the raid took, for which rooms are this raid's
		assertEquals(1480.2, Times.total("Theatre of Blood total completion time: <col=ff0000>24:40.20</col>. Personal best: 20:45.00"), 1e-9);
		assertEquals(0, Times.total("Your Vorkath kill count is: <col=ff0000>851</col>."), 1e-9);
		// unchanged: the Fractured Archive's own total, and a raid of the Chambers of Xeric
		assertArrayEquals(new double[]{145.8, 1}, Times.duration("The Fractured Archive: Total completion time: <col=ef1020>2:25.80</col> "
			+ "(new personal best)"), 1e-9);
		assertArrayEquals(new double[]{2470, 0}, Times.duration("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: "
			+ "<col=ff0000>3 players</col> Duration:</col> <col=ff0000>41:10</col> Personal best: </col><col=ff0000>40:03</col>"), 1e-9);
	}

	/** The Chambers of Xeric's rooms as CoX Additions' "CoX Timers" writes them (backticks; dey0/pluginhub-plugins). */
	@Test
	public void coxTimersRoomMessages()
	{
		Object[] tekton = Rooms.parse("Combat room `Tekton` complete! Duration: <col=ff0000>2:43</col> Total: <col=ff0000>6:53</col>");
		assertEquals("Tekton", tekton[0]);
		assertEquals(163.0, (double) tekton[1], 1e-9);
		assertEquals(Rooms.COX, tekton[2]);
		Object[] crabs = Rooms.parse("Puzzle `Crabs` complete! Duration: <col=ff0000>1:10.20</col> Total: <col=ff0000>9:00</col>");
		assertEquals("Crabs", crabs[0]);
		assertEquals(70.2, (double) crabs[1], 1e-9);
		Object[] ice = Rooms.parse("Puzzle `Ice demon` complete! Duration: <col=ff0000>3:01</col> Total: <col=ff0000>12:00</col>");
		assertEquals("Ice demon", ice[0]);
		assertEquals(Rooms.TOB, Rooms.parse("Wave 'Xarpus' (Hard Mode) complete!<br>Duration: <col=ff0000>2:00</col>")[2]);
		assertEquals(Rooms.TOA, Rooms.parse("Challenge complete: Path of Het. Duration: <col=ef1020>3:00</col>. Total: <col=ef1020>9:00</col>")[2]);
		assertNull(Rooms.parse("Olm phase 1 duration: <col=ff0000>1:12</col>"));
		assertEquals(Rooms.TOB, Rooms.raid("theatre of blood hard mode 4 players"));
		assertEquals(Rooms.TOA, Rooms.raid("tombs of amascut expert mode"));
		assertEquals(Rooms.COX, Rooms.raid("chambers of xeric challenge mode 3 players"));
		assertNull(Rooms.raid("vorkath"));
	}

	/** A raid's rooms: only its own raid, only since it began, the latest of each — a left raid never leaks into the next. */
	@Test
	public void roomsOfThisRaidOnly()
	{
		long now = 10_000_000L;
		List<Object[]> seen = new java.util.ArrayList<>();
		seen.add(new Object[]{"The Maiden of Sugadinti", 150.0, now - 3_000_000L, Rooms.TOB});   // a ToB left at Verzik, 50 min ago
		seen.add(new Object[]{"Xarpus", 200.0, now - 2_500_000L, Rooms.TOB});
		seen.add(new Object[]{"Path of Het", 90.0, now - 1_500_000L, Rooms.TOA});                // a ToA room since
		seen.add(new Object[]{"The Maiden of Sugadinti", 140.0, now - 1_000_000L, Rooms.TOB});   // the finished ToB
		seen.add(new Object[]{"The Final Challenge", 300.0, now, Rooms.TOB});
		List<Object[]> mine = Rooms.ofThisRaid(seen, Rooms.TOB, now, 1_480.2 + 120);
		assertEquals(2, mine.size());
		assertEquals("The Maiden of Sugadinti", mine.get(0)[0]);
		assertEquals(140.0, (double) mine.get(0)[1], 1e-9);       // this raid's, not the left one's
		assertEquals("The Final Challenge", mine.get(1)[0]);
		assertEquals(0, Rooms.ofThisRaid(seen, null, now, 1e9).size());
		// a wider window takes the left raid's Xarpus, but still each room once and never the ToA's
		List<Object[]> wide = Rooms.ofThisRaid(seen, Rooms.TOB, now, 4_000);
		assertEquals(3, wide.size());
		assertEquals(140.0, (double) wide.get(0)[1], 1e-9);
	}

	// ---- The Fractured Archive (user 2026-11-16: "Its got to track tfa too don't forget the new raid") ----
	// Real game messages, unaltered and in their order, from two of the eight raids in the TFA Splits Tracker plugin's
	// recorded-raids.json (github.com/LogicalSoIutions/tfa-splits-tracker, BSD-2-Clause; no player names in them).

	/** 3 players, 28:04.80 */
	private static final String[] TFA_PB_RAID = {
		"Challenge started: Archive Guardian.",
		"Challenge complete: Archive Guardian. Duration: <col=ef1020>2:09.60</col>. Total: <col=ef1020>2:09.60</col>. Personal best: 1:54.60",
		"Challenge started: Bork.",
		"Challenge complete: Bork. Duration: <col=ef1020>2:51.60</col>. Total: <col=ef1020>5:15.60</col>. Personal best: 2:35.40",
		"Challenge started: Dreadnoth the Jellied.",
		"Challenge complete: Dreadnoth the Jellied. Duration: <col=ef1020>2:47.40</col>. Total: <col=ef1020>8:28.20</col>. Personal best: 2:29.40",
		"Challenge started: Xarth.",
		"Challenge complete: Xarth. Duration: <col=ef1020>3:32.40</col>. Total: <col=ef1020>13:12.60</col>. Personal best: 2:25.80",
		"1/3 party members are now ready.",
		"2/3 party members are now ready.",
		"Challenge started: Ocellus.",
		"Challenge complete: Ocellus. Duration: <col=ef1020>13:58.20</col>. Total: <col=ef1020>28:04.80</col> (new personal best)",
		"The Fractured Archive: Total completion time: <col=ef1020>28:04.80</col> (new personal best)"
	};

	/** 4 players, 24:42.60 */
	private static final String[] TFA_NO_PB_RAID = {
		"Challenge started: Archive Guardian.",
		"Challenge complete: Archive Guardian. Duration: <col=ef1020>2:17.40</col>. Total: <col=ef1020>2:17.40</col>. Personal best: 1:43.80",
		"Challenge started: Bork.",
		"Challenge complete: Bork. Duration: <col=ef1020>2:07.80</col>. Total: <col=ef1020>4:38.40</col> (new personal best)",
		"Challenge started: Dreadnoth the Jellied.",
		"Challenge complete: Dreadnoth the Jellied. Duration: <col=ef1020>2:18.00</col>. Total: <col=ef1020>7:16.80</col>. Personal best: 2:06.00",
		"Challenge started: Xarth.",
		"Challenge complete: Xarth. Duration: <col=ef1020>2:15.00</col>. Total: <col=ef1020>10:32.40</col>. Personal best: 2:03.00",
		"1/4 party members are now ready.",
		"2/4 party members are now ready.",
		"3/4 party members are now ready.",
		"Challenge started: Ocellus.",
		"Challenge complete: Ocellus. Duration: <col=ef1020>9:39.60</col>. Total: <col=ef1020>24:42.60</col> (new personal best)",
		"The Fractured Archive: Total completion time: <col=ef1020>24:42.60</col>. Personal best: 23:04.20"
	};

	@Test
	public void fracturedArchiveIsARaid()
	{
		assertEquals(History.RAIDS, History.tab("fractured archive"));    // RuneLite's key (ChatCommandsPluginTest)
		History.Raid r = History.raid("fractured archive");
		assertNotNull(r);
		assertEquals(History.TFA, r.raid);
		assertEquals("", r.mode);
		assertEquals("", r.size);
		assertEquals("TFA", History.RAID_SHORT[r.raid]);
		assertEquals("Best at any size", History.modeName(r.raid, r.mode));   // one PB for every team size
		assertEquals(History.TFA, History.raid("the fractured archive").raid);
		assertEquals(Rooms.TFA, Rooms.raid("fractured archive"));
		assertNull(Rooms.fixed("fractured archive"));                       // its rooms change: no sum of best
		assertEquals(Rooms.TFA_ROOMS, Rooms.order("fractured archive"));
		// its icon, found by the hiscore's display name: the constant is FRACTURED_ARCHIVE in RuneLite 1.13.6 and
		// THE_FRACTURED_ARCHIVE in RuneLite's newer code, the name "The Fractured Archive" in both
		net.runelite.client.hiscore.HiscoreSkill icon = PbHistoryPlugin.hiscoreFor("fractured archive");
		assertNotNull(icon);
		assertEquals("fractured archive", PbHistoryPlugin.norm(icon.getName()));
		// the other raids as before
		assertEquals(2, History.raid("tombs of amascut expert mode 2 players").raid);
		assertEquals("Normal", History.modeName(2, ""));
		assertEquals("Regular", History.modeName(0, ""));
	}

	/** The time the plugin keeps for a kill: the LAST one read before the kill count is saved (onChatMessage). */
	private static double[] lastTime(String[] messages)
	{
		double[] last = null;
		for (String m : messages)
		{
			double[] d = Times.duration(m);
			if (d != null)
			{
				last = d;
			}
		}
		return last;
	}

	@Test
	public void fracturedArchiveKillTimeIsTheRaidsNotOcellus()
	{
		// a PB raid: 28:04.80, not Ocellus's 13:58.20
		assertArrayEquals(new double[]{28 * 60 + 4.8, 1}, lastTime(TFA_PB_RAID), 0.001);
		// not a PB: 24:42.60 and NOT fresh, although Ocellus's own line says "(new personal best)" (that is the room's)
		assertArrayEquals(new double[]{24 * 60 + 42.6, 0}, lastTime(TFA_NO_PB_RAID), 0.001);
		// the raid's total, which sets how far back its rooms are looked for
		assertEquals(28 * 60 + 4.8, Times.total(TFA_PB_RAID[TFA_PB_RAID.length - 1]), 0.001);
	}

	@Test
	public void fracturedArchiveRooms()
	{
		List<Object[]> seen = new java.util.ArrayList<>();
		long t = 1_000_000L;
		for (String m : TFA_NO_PB_RAID)
		{
			Object[] r = Rooms.parse(m);
			if (r != null)
			{
				seen.add(new Object[]{r[0], r[1], t += 1000, r[2]});
			}
		}
		String[] names = {"Archive Guardian", "Bork", "Dreadnoth the Jellied", "Xarth", "Ocellus"};
		double[] secs = {137.4, 127.8, 138.0, 135.0, 579.6};
		assertEquals(names.length, seen.size());
		for (int i = 0; i < names.length; i++)
		{
			assertEquals(names[i], seen.get(i)[0]);
			assertEquals(secs[i], (double) seen.get(i)[1], 0.001);
			assertEquals(Rooms.TFA, seen.get(i)[3]);
		}
		// a ToA room is still a ToA room (RuneLite's ToA message), and a room on neither list counts for either raid
		Object[] wardens = Rooms.parse("Challenge complete: The Wardens. Duration: <col=ef1020>8:30</col><br>Tombs of Amascut "
			+ "challenge completion time: <col=ef1020>8:31</col> (new personal best)");
		assertEquals("The Wardens", wardens[0]);
		assertEquals(Rooms.TOA, wardens[2]);
		Object[] unknown = Rooms.parse("Challenge complete: A New Room. Duration: <col=ef1020>2:00.00</col>. Total: "
			+ "<col=ef1020>6:00.00</col>");
		assertEquals(Rooms.CHALLENGE, unknown[2]);
		seen.add(new Object[]{unknown[0], unknown[1], t += 1000, unknown[2]});
		seen.add(new Object[]{wardens[0], wardens[1], t += 1000, wardens[2]});
		assertEquals(6, Rooms.ofThisRaid(seen, Rooms.TFA, t, 1600).size());   // the five + the unknown one, never the Wardens
		assertEquals(2, Rooms.ofThisRaid(seen, Rooms.TOA, t, 1600).size());   // the Wardens + the unknown one
		assertEquals(0, Rooms.ofThisRaid(seen, Rooms.TOB, t, 1600).size());
		// the game's curly apostrophe; short names in the table
		Object[] trio = Rooms.parse("Challenge complete: Dagon\u2019hai Trio. Duration: <col=ef1020>3:00.00</col>. Total: "
			+ "<col=ef1020>9:00.00</col>");
		assertEquals("Dagon'hai Trio", trio[0]);
		assertEquals(Rooms.TFA, trio[2]);
		assertEquals("Dreadnoth", Rooms.label("Dreadnoth the Jellied"));
		assertEquals("Ocellus", Rooms.label("Ocellus"));
	}
}
