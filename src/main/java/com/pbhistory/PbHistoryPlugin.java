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
import com.google.inject.Provides;
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
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.ScreenshotTaken;
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
public class PbHistoryPlugin extends Plugin implements PbPanel.Backend
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

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private PbHistoryConfig config;

	@Inject
	private EventBus eventBus;
	/** what each new screenshot is of, from the game event that made RuneLite take it (not from its file) */
	private volatile ShotNamer namer;
	private ShotNamer.Watch watch;

	/** PB History's own settings (goal times; the what's-new line), per RuneScape account where it matters */
	static final String GROUP = "pbhistory";

	/** the PBs (their times) that have a picture: PB History's copy of RuneLite's boss-kill screenshot of that kill */
	private final java.util.Set<Long> pictured = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** a boss-kill screenshot that came before its PB was recorded: matched when the PB arrives */
	private Pictures.Shot earlyShot;
	private BufferedImage earlyImage;

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
	/** the room messages seen ({room, seconds, when, raid}): this raid's are saved under its mode when it ends with a timed kill */
	private final List<Object[]> runRooms = new ArrayList<>();
	/** the ending raid's "total completion time" (Theatre of Blood, Tombs of Amascut), seconds; 0 = none seen */
	private double pendingTotal;
	/** rooms older than this are from a raid that never finished: dropped */
	private static final long RUN_MS = 3 * 3_600_000L;

	@Override
	protected void startUp()
	{
		panel = new PbPanel();
		panel.setTheme(config.theme());
		panel.setIcons(this::bossIcon);
		panel.setBackend(this);
		pictured.clear();
		namer = new ShotNamer(ShotNamer.from(configManager));
		watch = new ShotNamer.Watch(client, namer);
		eventBus.register(watch);
		executor.execute(this::readPictures);
		checkSetup();
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
		if (watch != null)
		{
			eventBus.unregister(watch);
			watch = null;
		}
		namer = null;
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
			if (History.RAIDS.equals(History.tab(boss)) && !runRooms.isEmpty())
			{
				String mode = Rooms.mode(boss);
				// only this raid's rooms: since it began (its total time, else twice its own time, plus 2 minutes)
				double window = (pendingTotal > 0 ? pendingTotal : 2 * pendingSecs) + 120;
				for (Object[] r : Rooms.ofThisRaid(runRooms, Rooms.raid(boss), System.currentTimeMillis(), window))
				{
					record(Entry.room(mode, (String) r[0], (double) r[1], (long) r[2], kc));
				}
			}
			runRooms.clear();
			pendingTotal = 0;
		}
		else if (now - pendingTick > WINDOW)
		{
			pendingTick = -1;   // a time with no kill count saved for it (e.g. Chat Commands is off)
			pendingTotal = 0;
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
		Object[] room = Rooms.parse(m.getMessage());
		if (room != null)
		{
			long now = System.currentTimeMillis();
			runRooms.removeIf(r -> now - (long) r[2] > RUN_MS);
			runRooms.add(new Object[]{room[0], room[1], now, room[2]});
		}
		double total = Times.total(m.getMessage());
		if (total > 0)
		{
			pendingTotal = total;
		}
		double[] d = Times.duration(m.getMessage());
		if (d != null)
		{
			pendingSecs = d[0];
			pendingFresh = d[1] > 0;
			pendingTick = client.getTickCount();
		}
	}

	@Provides
	PbHistoryConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PbHistoryConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (SetupHint.watches(e.getGroup(), e.getKey()))
		{
			checkSetup();
			refresh();
			return;
		}
		if (GROUP.equals(e.getGroup()) && "theme".equals(e.getKey()) && panel != null)
		{
			PbPanel p = panel;
			PbHistoryConfig.Theme s = config.theme();
			SwingUtilities.invokeLater(() -> p.setTheme(s));
			return;
		}
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
		Double goal = goal(boss);
		if (goal != null && now <= goal + 1e-9 && (was <= 0 || was > goal + 1e-9))
		{
			ChatMessageBuilder b = new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("PB History")
				.append(ChatColorType.NORMAL).append(" - goal reached! " + Times.name(boss) + " " + Times.format(now, true)
					+ " (your goal was " + Times.format(goal, true) + ")");
			chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE).runeLiteFormattedMessage(b.build()).build());
		}
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
		synchronized (this)
		{
			if (earlyShot != null && Pictures.sameKill(earlyShot, e))
			{
				BufferedImage img = earlyImage;
				executor.execute(() -> writePicture(e.at, img));
			}
		}
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

	// ---- PB pictures (RuneLite's boss-kill screenshot of the kill that set a PB) ----

	/**
	 * RuneLite saved a screenshot: if it is a boss kill (told from the game event that made RuneLite take it,
	 * {@link ShotNamer}, never from its file), it may be the picture of a PB.
	 */
	@Subscribe
	public void onScreenshotTaken(ScreenshotTaken e)
	{
		ShotNamer n = namer;
		if (n == null)
		{
			return;
		}
		ShotNamer.Name name = n.take(System.currentTimeMillis());   // every screenshot takes its place in the queue
		Pictures.Shot shot = ShotNamer.BOSS_KILLS.equals(name.type) ? Pictures.named(name.file, name.at) : null;
		if (shot == null || e.getScreenshot() == null)
		{
			return;
		}
		BufferedImage img = e.getScreenshot();
		executor.execute(() -> attach(shot, img));
	}

	/** A boss-kill screenshot: the picture of each PB it's the kill of, or kept until that PB is recorded. */
	private void attach(Pictures.Shot shot, BufferedImage img)
	{
		boolean any = false;
		for (Entry e : lines)
		{
			if (Pictures.sameKill(shot, e) && !pictured.contains(e.at))
			{
				any = true;
				writePicture(e.at, img);         // a raid's PB is saved twice (the mode, and the mode with the team size)
			}
		}
		if (!any)
		{
			synchronized (this)
			{
				earlyShot = shot;
				earlyImage = img;
			}
		}
	}

	private boolean writePicture(long at, BufferedImage img)
	{
		try
		{
			Filepath dir = getPluginDirectory().joinSegment(Pictures.DIR);
			dir.createDirectories();
			Pictures.write(img, dir.joinSegment(Pictures.fileName(at)));
			pictured.add(at);
		}
		catch (IOException | RuntimeException ex)
		{
			log.warn("PB History: could not keep a PB's picture", ex);
			return false;
		}
		refresh();
		return true;
	}

	/**
	 * Pictures for PBs set before this version (user 2026-11-08: "sounds good"): you choose your .runelite folder (or its
	 * screenshots folder, your name's folder or its Boss Kills folder), and each PB PB History recorded gets the
	 * boss-kill screenshot RuneLite saved of the kill that set it. RuneLite's files are only read, never changed.
	 */
	@Override
	public void importPictures(java.awt.Component parent)
	{
		String title = "Import PB pictures";
		if (profile == null)
		{
			JOptionPane.showMessageDialog(parent, "Log in first: the pictures go to the PBs of the account logged in.", title,
				JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		// hidden folders shown: .runelite starts with a dot (as Death Log's import, 2026-11-08: it was hard to find)
		Filepath.Chooser chooser = new Filepath.Chooser().setIsOpen().setAcceptsDirectories().setFileHidingEnabled(false)
			.setDialogTitle("PB History: pick your .runelite folder, then click Open");
		if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows"))
		{
			chooser.setFileName(".runelite");   // it opens in the home folder (Linux, Mac), where .runelite is
		}
		List<Filepath> picked = chooser.showDialog(parent);
		if (picked == null || picked.isEmpty())
		{
			return;
		}
		Filepath root = picked.get(0);
		List<Entry> recorded = lines;
		executor.execute(() ->
		{
			String say;
			try
			{
				Map<Filepath, Pictures.Shot> shots = Pictures.find(root, java.time.ZoneId.systemDefault());
				Map<Long, Filepath> pick = Pictures.match(recorded, shots, pictured);
				// one kill can be two PBs (a raid's mode, and the mode with the team size): read each file once
				Map<Filepath, String> kills = new java.util.LinkedHashMap<>();   // file → the shortest boss name it went to
				Map<Filepath, BufferedImage> read = new HashMap<>();
				for (Map.Entry<Long, Filepath> p : pick.entrySet())
				{
					BufferedImage img = read.get(p.getValue());
					if (img == null && !read.containsKey(p.getValue()))
					{
						try
						{
							img = Pictures.read(p.getValue());
						}
						catch (IOException e)
						{
							log.warn("PB History: could not read a boss-kill screenshot", e);
						}
						read.put(p.getValue(), img);
					}
					if (img != null && writePicture(p.getKey(), img))
					{
						String boss = bossAt(recorded, p.getKey());
						kills.merge(p.getValue(), boss, (a, b) -> b.length() < a.length() ? b : a);
					}
				}
				java.util.Set<Long> missing = new java.util.HashSet<>();
				for (Entry e : recorded)
				{
					if (Entry.PB.equals(e.kind) && e.at > 0 && !pictured.contains(e.at))
					{
						missing.add(e.at);
					}
				}
				if (!kills.isEmpty())
				{
					List<String> names = new ArrayList<>();
					kills.values().forEach(b -> names.add(Times.name(b)));
					say = "Added " + (kills.size() == 1 ? "the picture of 1 PB kill" : "the pictures of " + kills.size() + " PB kills")
						+ ": " + String.join(", ", names) + ".\n\nSee it on the boss's page (and on the Newest PB card if it's your newest).";
				}
				else if (shots.isEmpty())
				{
					say = "No boss-kill screenshots were found there.\n\nRuneLite saves them only with its Screenshot plugin's "
						+ "\"Screenshot boss kills\" setting on, in .runelite/screenshots/<your name>/Boss Kills.";
				}
				else if (missing.isEmpty())
				{
					say = "Every PB that PB History recorded already has its picture.";
				}
				else
				{
					say = (shots.size() == 1 ? "The boss-kill screenshot there isn't" : "None of the " + shots.size()
						+ " boss-kill screenshots there is") + " of a kill that set a PB."
						+ "\n\nOnly PBs set since you installed PB History can get a picture: older ones have no date to match.";
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("PB History: could not read the screenshots", e);
				say = "PB History couldn't read that folder (is it there?). Try again and pick your .runelite folder.";
			}
			String message = say;
			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE));
		});
	}

	/** The boss of the PB recorded at {@code at}. */
	private static String bossAt(List<Entry> recorded, long at)
	{
		for (Entry e : recorded)
		{
			if (Entry.PB.equals(e.kind) && e.at == at)
			{
				return e.boss;
			}
		}
		return "";
	}

	private void readPictures()
	{
		try
		{
			Filepath dir = getPluginDirectory().joinSegment(Pictures.DIR);
			if (dir.exists())
			{
				try (java.util.stream.Stream<Filepath> files = dir.walk(1))
				{
					for (Filepath f : (Iterable<Filepath>) files::iterator)
					{
						long at = Pictures.timeOf(f.getFileName());
						if (at > 0)
						{
							pictured.add(at);
						}
					}
				}
			}
		}
		catch (IOException ex)
		{
			log.warn("PB History: could not read the pictures folder", ex);
		}
		refresh();
	}

	// ---- PbPanel.Backend ----

	@Override
	public boolean hasPicture(long at)
	{
		return pictured.contains(at);
	}

	@Override
	public void picture(long at, int maxWidth, Consumer<BufferedImage> then)
	{
		executor.execute(() ->
		{
			BufferedImage img = null;
			try
			{
				img = Pictures.read(getPluginDirectory().joinSegment(Pictures.DIR).joinSegment(Pictures.fileName(at)));
				if (img != null && maxWidth > 0)
				{
					img = Pictures.scaled(img, maxWidth);
				}
			}
			catch (IOException | RuntimeException ex)
			{
				log.debug("PB History: could not read a PB's picture", ex);
			}
			BufferedImage out = img;
			SwingUtilities.invokeLater(() -> then.accept(out));
		});
	}

	/** A boss's goal time (seconds) for the account logged in; null = none (user 2026-11-08: goal times "a big yes"). */
	@Override
	public Double goal(String key)
	{
		String v = profile == null ? null : configManager.getRSProfileConfiguration(GROUP, "goal." + key);
		double d = parseDouble(v);
		return d > 0 ? d : null;
	}

	@Override
	public void setGoal(String key, Double secs)
	{
		if (profile == null)
		{
			return;
		}
		if (secs == null || secs <= 0)
		{
			configManager.unsetRSProfileConfiguration(GROUP, "goal." + key);
		}
		else
		{
			configManager.setRSProfileConfiguration(GROUP, "goal." + key, String.valueOf(secs));
		}
		refresh();
	}

	/** Another RuneLite profile: its settings may differ. */
	@Subscribe
	public void onProfileChanged(ProfileChanged e)
	{
		checkSetup();
		refresh();
	}

	// ---- the setup hint: what's off among the RuneLite settings PB History needs (read only), until the ✕ ----

	private static final String HINT_CLOSED = "setupHintClosed";
	private volatile List<SetupHint.Item> setupMissing = Collections.emptyList();

	/** Re-read the settings the hint looks at (at start-up, and when one of them or the profile changes). */
	private void checkSetup()
	{
		List<SetupHint.Item> m = SetupHint.missing(configManager::getConfiguration);
		setupMissing = m;
		if (m.isEmpty() && configManager.getConfiguration(GROUP, HINT_CLOSED) != null)
		{
			configManager.unsetConfiguration(GROUP, HINT_CLOSED);   // all on: a later "off" shows it again
		}
	}

	@Override
	public List<String> setupHint()
	{
		List<SetupHint.Item> m = setupMissing;
		if (m.isEmpty() || SetupHint.fingerprint(m).equals(configManager.getConfiguration(GROUP, HINT_CLOSED)))
		{
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		for (SetupHint.Item i : m)
		{
			out.add(i.text);
		}
		return out;
	}

	@Override
	public void closeSetupHint()
	{
		configManager.setConfiguration(GROUP, HINT_CLOSED, SetupHint.fingerprint(setupMissing));
	}

	/** What's new after an update, shown until closed (change BOTH with each update worth telling). */
	static final String WHATS_NEW_ID = "2026-11-goals";
	static final String WHATS_NEW = "New: each PB's kill screenshot; goal times, consistency and raid room times (open "
		+ "a boss); themes in the settings.";

	@Override
	public String whatsNew()
	{
		return WHATS_NEW_ID.equals(configManager.getConfiguration(GROUP, "whatsNewSeen")) ? null : WHATS_NEW;
	}

	@Override
	public void closeWhatsNew()
	{
		configManager.setConfiguration(GROUP, "whatsNewSeen", WHATS_NEW_ID);
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
