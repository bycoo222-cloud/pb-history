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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The setup hint: only what's really off, read the way RuneLite keeps its settings (null = the default). */
public class SetupHintTest
{
	private static SetupHint.Settings settings(String... groupKeyValue)
	{
		Map<String, String> m = new HashMap<>();
		for (int i = 0; i < groupKeyValue.length; i += 3)
		{
			m.put(groupKeyValue[i] + "." + groupKeyValue[i + 1], groupKeyValue[i + 2]);
		}
		return (group, key) -> m.get(group + "." + key);
	}

	@Test
	public void onlyWhatIsOff()
	{
		// a fresh RuneLite: Chat Commands and Screenshot are on, "Screenshot boss kills" is off until ticked
		List<SetupHint.Item> fresh = SetupHint.missing(settings());
		assertEquals("screenshot-boss", SetupHint.fingerprint(fresh));
		assertTrue(fresh.get(0).text.contains("Screenshot boss kills"));
		// everything PB History needs
		assertTrue(SetupHint.missing(settings("screenshot", "boss", "true")).isEmpty());
		// Chat Commands off: first, it's where every PB comes from
		assertEquals("chat-commands", SetupHint.fingerprint(SetupHint.missing(settings(
			"runelite", "chatcommandsplugin", "false", "screenshot", "boss", "true"))));
		// both plugins off: the Screenshot plugin, not its box (ticking it alone would not help)
		assertEquals("chat-commands,screenshot-plugin", SetupHint.fingerprint(SetupHint.missing(settings(
			"runelite", "chatcommandsplugin", "false", "runelite", "screenshotplugin", "false"))));
	}

	@Test
	public void watchesOnlyItsSettings()
	{
		assertTrue(SetupHint.watches("runelite", "chatcommandsplugin"));
		assertTrue(SetupHint.watches("runelite", "screenshotplugin"));
		assertTrue(SetupHint.watches("screenshot", "boss"));
		assertFalse(SetupHint.watches("screenshot", "playerDeath"));
		assertFalse(SetupHint.watches("runelite", "coxtimersplugin"));
		assertEquals("", SetupHint.fingerprint(Collections.emptyList()));
	}
}
