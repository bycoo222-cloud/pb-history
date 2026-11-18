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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PbHistoryPlugin.GROUP)
public interface PbHistoryConfig extends Config
{
	/**
	 * The themes shared with Death Log, Flip Sidebar and Screenshot Gallery (Looks), without Gravestone (user, 2026-11-08:
	 * "gravestone is only for the death log plug in that them doesnt suit the rest") or Thanksgiving ("not anything that
	 * means thanksgiving maybe just remove the tanksgiving themes").
	 */
	enum Theme
	{
		PLAIN(Looks.Id.PLAIN),
		DASHBOARD(Looks.Id.DASHBOARD),
		OLD_SCHOOL(Looks.Id.OLD_SCHOOL),
		ECTOPLASM(Looks.Id.ECTOPLASM),
		INFERNAL(Looks.Id.INFERNAL),
		AUTUMN(Looks.Id.AUTUMN),
		HALLOWEEN(Looks.Id.HALLOWEEN),
		WINTER(Looks.Id.WINTER),
		CHRISTMAS(Looks.Id.CHRISTMAS);

		final Looks.Id id;

		Theme(Looks.Id id)
		{
			this.id = id;
		}

		@Override
		public String toString()
		{
			return Looks.label(id);
		}
	}

	// user 2026-11-08: "id like to bring on full themes like we have for everything else ... just wish it would theme
	// the whole side bar" — replaces the unreleased trim-only "Seasonal look" (key seasonalLook), so nothing to migrate
	@ConfigItem(
		position = 0,
		keyName = "theme",
		name = "Theme",
		description = "How the whole panel looks: Plain (RuneLite's own, as before), Dashboard, Old School, Ectoplasm, "
			+ "Infernal, and the seasons: Autumn, Halloween, Winter, Christmas. The same themes as Death Log, "
			+ "Flip Sidebar and Screenshot Gallery"
	)
	default Theme theme()
	{
		return Theme.PLAIN;
	}

	// user 2026-11-17, after the mockup of both: "Yes build that"
	@ConfigItem(
		position = 1,
		keyName = "speedTasks",
		name = "Speed tasks",
		description = "Combat Achievement speed tasks: a badge after each boss with the hardest tier your PB beats "
			+ "(hollow = none yet), and every tier's time on the boss's page. The times are the OSRS Wiki's; a tick means "
			+ "your PB beats the time, not that the game gave you the task"
	)
	default boolean speedTasks()
	{
		return true;
	}

	@ConfigItem(
		position = 2,
		keyName = "killLine",
		name = "Line after each kill",
		description = "A chat line after each timed boss kill, raid or challenge: how far off your PB it was, where it "
			+ "ranks among your kills and your average; after a new PB, by how much and how far the next speed task is"
	)
	default boolean killLine()
	{
		return true;
	}
}
