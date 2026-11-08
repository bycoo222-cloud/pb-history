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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps every personal best with its date, and every kill time.
 * <ul>
 * <li>A new PB: RuneLite's Chat Commands plugin saves each PB (group "personalbest", its own boss names — raid team sizes
 * and modes included), and the change arrives here as a ConfigChanged with the old and new time. "fresh" = the game's
 * message that tick said "new personal best" (not a PB RuneLite only learned of, e.g. on a first kill after installing).
 * <li>A kill's time: the game's kill-time message (Times.duration), matched on the next tick with the kill count (or PB)
 * RuneLite saved in the same few ticks — so it carries RuneLite's boss name.
 * <li>PBs from before PB History: RuneLite's saved PBs for the profile, shown without a date.
 * </ul>
 */
@PluginDescriptor(
	name = "PB History",
	internalName = "pb-history",   // getPluginDirectory() needs it (RuneLite: "internalName must be set")
	description = "Every personal best you set, with its date, and every kill time, as a graph per boss",
	tags = {"pb", "personal best", "kill time", "boss", "raids", "history", "graph", "speedrun"}
)
public class PbHistoryPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(PbHistoryPlugin.class);
	static final String PB_GROUP = "personalbest";
	static final String KC_GROUP = "killcount";
	/** a kill's time and its kill count / PB arrive within this many game ticks of each other */
	private static final int WINDOW = 3;

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private SpriteManager spriteManager;

	/** boss icons by RuneLite's name for the boss: the game's hiscore sprites, asked for once each */
	private final Map<String, BufferedImage> icons = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<String> iconAsked = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private PbPanel panel;
	private NavigationButton navButton;

	/** the RuneScape profile shown (RuneLite's key, e.g. "rsprofile.AbC123"); null = none yet */
	private volatile String profile;
	private volatile List<Entry> lines = Collections.emptyList();

	// what this tick (and the last few) brought: matched on the next tick
	private double pendingSecs;
	private boolean pendingFresh;
	private int pendingTick = -1;
	private String kcBoss;
	private int kcValue;
	private int kcTick = -100;
	private String pbBoss;
	private int pbTick = -100;

	@Override
	protected void startUp()
	{
		panel = new PbPanel();
		panel.setIcons(this::bossIcon);
		icons.clear();
		iconAsked.clear();
		navButton = NavigationButton.builder().tooltip("PB History").icon(icon()).priority(11).panel(panel).build();
		clientToolbar.addNavigation(navButton);
		profile = null;
		lines = Collections.emptyList();
		pendingTick = -1;
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
	}

	/** The sidebar icon: a stopwatch, drawn here. */
	private static BufferedImage icon()
	{
		try
		{
			return ImageUtil.loadImageResource(PbHistoryPlugin.class, "pb.png");
		}
		catch (RuntimeException e)
		{
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.setColor(java.awt.Color.ORANGE);
			g.fillOval(2, 3, 12, 12);
			g.dispose();
			return img;
		}
	}

	/**
	 * The boss's icon: the game's own hiscore sprite (HiscoreSkill), by RuneLite's name for the boss — a raid's mode or,
	 * failing that, the raid itself; null until it has loaded (the panel draws again then) or when there is none.
	 */
	BufferedImage bossIcon(String key)
	{
		BufferedImage have = icons.get(key);
		if (have != null || !iconAsked.add(key))
		{
			return have;
		}
		HiscoreSkill hs = hiscoreFor(key);
		if (hs != null && hs.getSpriteId() > 0)
		{
			spriteManager.getSpriteAsync(hs.getSpriteId(), 0, img ->
			{
				if (img != null)
				{
					icons.put(key, img);
					PbPanel p = panel;
					if (p != null)
					{
						SwingUtilities.invokeLater(p::iconsArrived);
					}
				}
			});
		}
		return null;
	}

	/** "tombs of amascut expert mode 2 players" → TOMBS_OF_AMASCUT_EXPERT; "tombs of amascut entry mode" → TOMBS_OF_AMASCUT */
	static HiscoreSkill hiscoreFor(String key)
	{
		List<String> tries = new ArrayList<>();
		tries.add(norm(key));
		History.Raid r = History.raid(key);
		if (r != null)
		{
			tries.add(norm(History.RAID_PREFIX[r.raid] + " " + r.mode));
			tries.add(norm(History.RAID_PREFIX[r.raid]));
		}
		for (String t : tries)
		{
			for (HiscoreSkill hs : HiscoreSkill.values())
			{
				if (norm(hs.getName()).equals(t))
				{
					return hs;
				}
			}
		}
		return null;
	}

	/** lower case, no "the", no punctuation: "TzKal-Zuk" = "tzkal-zuk", "The Nightmare" = "nightmare" */
	static String norm(String s)
	{
		return s.toLowerCase().replaceAll("\\bthe\\b", " ").replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
	}

	@Subscribe
	public void onGameTick(GameTick t)
	{
		String key = configManager.getRSProfileKey();
		if (key != null && !key.equals(profile))
		{
			load(key);
		}
		int now = client.getTickCount();
		if (pendingTick < 0 || now - pendingTick < 1)
		{
			return;   // everything from the kill's own tick has arrived by the next one
		}
		String boss = Math.abs(pbTick - pendingTick) <= WINDOW ? pbBoss : Math.abs(kcTick - pendingTick) <= WINDOW ? kcBoss : null;
		if (boss != null)
		{
			int kc = Math.abs(kcTick - pendingTick) <= WINDOW ? kcValue : 0;
			record(Entry.kill(boss, pendingSecs, System.currentTimeMillis(), kc));
			pendingTick = -1;
		}
		else if (now - pendingTick > WINDOW)
		{
			pendingTick = -1;   // a time with no kill count saved for it (e.g. Chat Commands is off)
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage m)
	{
		ChatMessageType type = m.getType();
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM && type != ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			return;
		}
		double[] d = Times.duration(m.getMessage());
		if (d != null)
		{
			pendingSecs = d[0];
			pendingFresh = d[1] > 0;
			pendingTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		boolean pb = PB_GROUP.equals(e.getGroup());
		if (!pb && !KC_GROUP.equals(e.getGroup()) || e.getKey() == null || profile == null
			|| e.getProfile() != null && !e.getProfile().equals(profile) || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		String boss = e.getKey().toLowerCase();
		int tick = client.getTickCount();
		if (!pb)
		{
			int was = parseInt(e.getOldValue()), now = parseInt(e.getNewValue());
			if (now > was)
			{
				kcBoss = boss;
				kcValue = now;
				kcTick = tick;
			}
			return;
		}
		double now = parseDouble(e.getNewValue()), was = parseDouble(e.getOldValue());
		if (now <= 0 || was > 0 && Math.abs(now - was) < 1e-9)
		{
			return;
		}
		pbBoss = boss;
		pbTick = tick;
		boolean fresh = pendingTick >= 0 && Math.abs(pendingTick - tick) <= WINDOW && pendingFresh && Math.abs(pendingSecs - now) < 0.05;
		int kc = Math.abs(kcTick - tick) <= WINDOW ? kcValue : 0;
		record(Entry.pb(boss, now, was, System.currentTimeMillis(), kc, fresh));
	}

	private static int parseInt(String s)
	{
		try
		{
			return s == null ? 0 : Integer.parseInt(s.trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private static double parseDouble(String s)
	{
		try
		{
			return s == null ? 0 : Double.parseDouble(s.trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	// ---- the profile's history ----

	private void record(Entry e)
	{
		String key = profile;
		if (key == null)
		{
			return;
		}
		List<Entry> next = new ArrayList<>(lines);
		next.add(e);
		lines = Collections.unmodifiableList(next);
		executor.execute(() -> append(key, e));
		refresh();
	}

	/** A different account (or the first tick of one): its lines from the file, RuneLite's PBs and kill counts. */
	private void load(String key)
	{
		profile = key;
		lines = Collections.emptyList();
		executor.execute(() ->
		{
			List<Entry> read = read(key);
			if (key.equals(profile))
			{
				List<Entry> all = new ArrayList<>(read);
				all.addAll(lines);   // anything recorded while the file was read
				lines = Collections.unmodifiableList(all);
				refresh();
			}
		});
		refresh();
	}

	/** RuneLite's saved values in a group for the profile: name → value text. */
	private Map<String, String> saved(String group)
	{
		Map<String, String> out = new HashMap<>();
		if (profile == null)
		{
			return out;
		}
		for (String k : configManager.getRSProfileConfigurationKeys(group, profile, ""))
		{
			String v = configManager.getRSProfileConfiguration(group, k);
			if (v != null)
			{
				out.put(k.toLowerCase(), v);
			}
		}
		return out;
	}

	private void refresh()
	{
		Map<String, Double> stored = new HashMap<>();
		saved(PB_GROUP).forEach((k, v) -> stored.put(k, parseDouble(v)));
		Map<String, Integer> kcs = new HashMap<>();
		saved(KC_GROUP).forEach((k, v) -> kcs.put(k, parseInt(v)));
		List<History.Boss> bosses = History.build(lines, stored, kcs);
		PbPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.set(bosses));
		}
	}

	private Filepath file(String key) throws IOException
	{
		return getPluginDirectory().joinSegment("history").joinSegment(key.replaceAll("[^A-Za-z0-9._-]", "_") + ".jsonl");
	}

	private List<Entry> read(String key)
	{
		List<Entry> out = new ArrayList<>();
		try
		{
			Filepath f = file(key);
			if (!f.exists())
			{
				return out;
			}
			try (BufferedReader r = f.openBufferedReader())
			{
				for (String line = r.readLine(); line != null; line = r.readLine())
				{
					try
					{
						Entry e = line.isBlank() ? null : gson.fromJson(line, Entry.class);
						if (e != null)
						{
							out.add(e);
						}
					}
					catch (JsonParseException bad)
					{
						log.debug("PB History: a bad line skipped", bad);
					}
				}
			}
		}
		catch (IOException e)
		{
			log.warn("PB History: could not read the history", e);
		}
		return out;
	}

	private void append(String key, Entry e)
	{
		try
		{
			Filepath f = file(key);
			f.getParent().createDirectories();
			f.write((gson.toJson(e) + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException ex)
		{
			log.warn("PB History: could not save", ex);
		}
	}
}
