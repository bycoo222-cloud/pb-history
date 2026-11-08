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

/**
 * One line of the history: a timed kill ({@link #KILL}) or a personal best RuneLite saved ({@link #PB}). Stored one per
 * line (JSON) in the plugin's folder, per RuneScape profile.
 */
final class Entry
{
	static final String KILL = "kill";
	static final String PB = "pb";

	String kind;
	/** RuneLite's own name for it, lower case, e.g. "vorkath", "tombs of amascut expert mode 2 players" */
	String boss;
	/** the kill's time, or the new personal best (seconds) */
	double secs;
	/** a PB: the one it replaced (0 = none) */
	double was;
	/** when (epoch ms); 0 = before PB History was installed */
	long at;
	/** the kill count with it, 0 = not known */
	int kc;
	/** a PB the game called "new personal best" (not one RuneLite only learned of, like on a first kill after installing) */
	boolean fresh;

	static Entry kill(String boss, double secs, long at, int kc)
	{
		Entry e = new Entry();
		e.kind = KILL;
		e.boss = boss;
		e.secs = secs;
		e.at = at;
		e.kc = kc;
		return e;
	}

	static Entry pb(String boss, double secs, double was, long at, int kc, boolean fresh)
	{
		Entry e = new Entry();
		e.kind = PB;
		e.boss = boss;
		e.secs = secs;
		e.was = was;
		e.at = at;
		e.kc = kc;
		e.fresh = fresh;
		return e;
	}

	boolean isPb()
	{
		return PB.equals(kind);
	}
}
