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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/** The sidebar: every boss with its PB and when it was set; click one for its graph and every PB it took. */
final class PbPanel extends PluginPanel
{
	static final int PAD = 10;
	static final int WIDTH = PANEL_WIDTH - 2 * PAD;
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

	private final IconTextField search = new IconTextField();
	private final JPanel body = new JPanel()
	{
		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Themed.background(g, look, getWidth(), getHeight());   // the theme's faint layer: snow, leaves, bats...
		}
	};
	private List<History.Boss> bosses = new ArrayList<>();
	private String open;   // the boss shown, null = the list
	private String tab = History.BOSSES;
	private boolean tabPicked;   // the player picked a tab (until then: the tab with the latest PB)
	private int raidTab = -1;    // CoX / ToB / ToA (History.RAID_SHORT); -1 = the raid done last

	/*
	 * The design (user, 2026-11-07, after three looks: "for pb i dont see why we cant do a and have b in the bottom section
	 * with the + time next to it i like that. does it should - times too when you miss it"): the Trophy case's showpiece
	 * card for the newest PB on top, then a split-timer table — Boss · Last · PB — where Last is your last kill against
	 * your PB: green - (a new PB by that much), red + (missed it by that much). Gold = a PB set this week.
	 */

	/** a boss's icon (the game's hiscore sprite, by RuneLite's name for it); null = none */
	interface Icons
	{
		java.awt.image.BufferedImage of(String key);
	}

	/** What the panel asks of the plugin (null = the offline preview: no pictures, no goals). */
	interface Backend
	{
		boolean hasPicture(long at);

		void picture(long at, int maxWidth, java.util.function.Consumer<java.awt.image.BufferedImage> then);

		Double goal(String key);

		void setGoal(String key, Double secs);

		/** the what's-new line, or null once closed */
		String whatsNew();

		void closeWhatsNew();

		/** the setup hint's lines (RuneLite settings PB History needs that are off); empty = none, or closed */
		default java.util.List<String> setupHint()
		{
			return java.util.Collections.emptyList();
		}

		default void closeSetupHint()
		{
		}

		/** Pictures for PBs set before this version, from RuneLite's boss-kill screenshots (asks for the folder). */
		default void importPictures(java.awt.Component parent)
		{
		}
	}

	private Backend backend;

	void setBackend(Backend b)
	{
		backend = b;
		redraw();
	}

	static final Color GOLD = new Color(0xFF, 0xD2, 0x4A);
	private static final Color STRIPE = new Color(0x2B, 0x2B, 0x2B);
	private Icons icons = key -> null;

	PbPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(new EmptyBorder(PAD, PAD, PAD, PAD));
		setBackground(look.background);
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(WIDTH, 30));
		search.setMaximumSize(new Dimension(WIDTH, 30));
		search.setBackground(look.card);
		search.setHoverBackgroundColor(look.hover);
		search.setAlignmentX(LEFT_ALIGNMENT);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				redraw();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				redraw();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				redraw();
			}
		});
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(look.background);
		body.setAlignmentX(LEFT_ALIGNMENT);
		add(body);
		redraw();
	}

	/** The theme (the setting, shared with Death Log, Flip Sidebar and Screenshot Gallery): the whole panel's colours. */
	private Looks look = Looks.of(Looks.Id.PLAIN);

	void setTheme(PbHistoryConfig.Theme th)
	{
		look = Looks.of(th == null ? Looks.Id.PLAIN : th.id);
		setBackground(look.background);
		body.setBackground(look.background);
		search.setBackground(look.card);
		search.setHoverBackgroundColor(look.hover);
		redraw();
	}

	/** A PB's own colour: gold in Plain (as before), the theme's highlight otherwise. */
	private Color gold()
	{
		return look.id == Looks.Id.PLAIN ? GOLD : look.highlight;
	}

	/** Every other row of a table: a shade lighter than the cards (#2B2B2B in Plain, as before). */
	private Color stripe()
	{
		return look.id == Looks.Id.PLAIN ? STRIPE : Themed.mix(look.card, look.text, 0.05);
	}

	void setIcons(Icons i)
	{
		icons = i;
		redraw();
	}

	private boolean iconRedraw;

	/** A boss icon has loaded (Swing's thread): drawn again once for however many arrive together. */
	void iconsArrived()
	{
		if (iconRedraw)
		{
			return;
		}
		iconRedraw = true;
		SwingUtilities.invokeLater(() ->
		{
			iconRedraw = false;
			redraw();
		});
	}

	/** Shows one raid (History.RAID_SHORT index) on the Raids tab, as a click on its tab does. */
	void showRaid(int raid)
	{
		tab = History.RAIDS;
		tabPicked = true;
		raidTab = raid;
		open = null;
		redraw();
	}

	/** Shows one tab, as a click on it does. */
	void showTab(String t)
	{
		tab = t;
		tabPicked = true;
		open = null;
		redraw();
	}

	/** Shows one boss (its key), as a click on its row does. */
	void openBoss(String key)
	{
		open = key;
		redraw();
	}

	/** New history (Swing's thread): the list or the open boss is drawn again. */
	void set(List<History.Boss> list)
	{
		bosses = list;
		redraw();
	}

	private void redraw()
	{
		body.removeAll();
		History.Boss b = open == null ? null : find(open);
		if (b != null)
		{
			detail(b);
		}
		else
		{
			open = null;
			drawList();
			importLink();
		}
		body.revalidate();
		body.repaint();
	}

	/** "Import PB pictures" at the foot of the list, while a PB that PB History recorded has no picture yet. */
	private void importLink()
	{
		if (backend == null)
		{
			return;
		}
		boolean missing = false;
		for (History.Boss b : bosses)
		{
			for (Entry e : b.steps())
			{
				missing |= e.at > 0 && !backend.hasPicture(e.at);
			}
		}
		if (!missing)
		{
			return;
		}
		body.add(Box.createVerticalStrut(10));
		JLabel l = small("Import PB pictures", look.highlight);
		l.setToolTipText("<html>A PB set before this update gets the screenshot of the kill that set it, from RuneLite's own "
			+ "boss-kill screenshots.<br>Pick your .runelite folder, then click Open. RuneLite's files are only read, never "
			+ "changed.</html>");
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		l.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				backend.importPictures(PbPanel.this);
			}
		});
		body.add(l);
	}

	private History.Boss find(String key)
	{
		for (History.Boss b : bosses)
		{
			if (b.key.equals(key))
			{
				return b;
			}
		}
		return null;
	}

	// ---- the list ----

	private void drawList()
	{
		String news = backend == null ? null : backend.whatsNew();
		if (news != null)
		{
			body.add(newsCard(news));
			body.add(Box.createVerticalStrut(8));
		}
		java.util.List<String> hint = backend == null ? java.util.Collections.emptyList() : backend.setupHint();
		if (!hint.isEmpty())
		{
			body.add(hintCard(hint));
			body.add(Box.createVerticalStrut(8));
		}
		if (look.id == Looks.Id.PLAIN)
		{
			body.add(title("PB History"));
		}
		else
		{
			Themed.Heading h = new Themed.Heading(() -> look, title("PB History"), null, null, 0, 4);
			h.setAlignmentX(LEFT_ALIGNMENT);
			h.setMaximumSize(new Dimension(WIDTH, h.getPreferredSize().height));
			body.add(h);
		}
		int fresh = 0;
		long monthAgo = System.currentTimeMillis() - 30L * 86_400_000L;
		for (History.Boss b : bosses)
		{
			for (Entry e : b.steps())
			{
				fresh += e.at > monthAgo ? 1 : 0;
			}
		}
		body.add(small(bosses.isEmpty() ? "" : bosses.size() + (bosses.size() == 1 ? " boss" : " bosses")
			+ (fresh > 0 ? " · " + fresh + " new PB" + (fresh == 1 ? "" : "s") + " in 30 days" : ""), look.dim));
		body.add(Box.createVerticalStrut(8));
		if (!bosses.isEmpty())
		{
			JPanel hero = hero();
			if (hero != null)
			{
				body.add(hero);
				body.add(Box.createVerticalStrut(10));
			}
		}
		if (bosses.isEmpty())
		{
			JLabel l = small("<html><b>No personal bests yet.</b><br><br>Kill a boss (or finish a raid, a lap, a challenge) "
				+ "that shows a kill time in the chat: each time is kept here, and every new personal best with its date."
				+ "<br><br>RuneLite's <b>Chat Commands</b> plugin must be on: PB History reads the PBs it saves, so the "
				+ "names match its !pb command.</html>", look.dim);
			l.setPreferredSize(new Dimension(WIDTH, 170));
			l.setVerticalAlignment(SwingConstants.TOP);
			body.add(l);
			return;
		}
		body.add(search);
		body.add(Box.createVerticalStrut(6));
		String q = search.getText().trim().toLowerCase(Locale.ROOT);
		// the tabs: Bosses, Raids, Challenges (+ Other when it has any); a search looks in every tab
		java.util.Map<String, Integer> count = new java.util.LinkedHashMap<>();
		for (String t : History.TABS)
		{
			count.put(t, 0);
		}
		for (History.Boss b : bosses)
		{
			count.merge(History.tab(b.key), 1, Integer::sum);
		}
		if (!tabPicked && !bosses.isEmpty())
		{
			tab = History.tab(bosses.get(0).key);   // the latest activity's tab (the list is newest first)
		}
		if (q.isEmpty())
		{
			body.add(tabs(count));
			body.add(Box.createVerticalStrut(6));
		}
		if (q.isEmpty() && History.RAIDS.equals(tab))
		{
			drawRaids();
			return;
		}
		int shown = 0;
		body.add(splitsHeader("Boss"));
		for (History.Boss b : bosses)
		{
			if (!q.isEmpty() ? !b.key.contains(q) : !tab.equals(History.tab(b.key)))
			{
				continue;
			}
			body.add(splitsRow(b, b.name, shown % 2 == 1, true));
			shown++;
		}
		if (shown == 0)
		{
			body.add(small(q.isEmpty() ? "No " + tab.toLowerCase(Locale.ROOT) + " yet" : "None match", look.dim));
		}
	}

	// ---- raids (user, 2026-11-07: "sub tabs for raids ... for toa so its entry normal hard and then do team size, for cox
	// do callenge mode and regular ... we got to make that look neater its a mess") ----

	/** CoX / ToB / ToA as their own tabs; in each, a block per mode with a slim row per team size. */
	private void drawRaids()
	{
		java.util.Map<Integer, java.util.Map<String, List<History.Boss>>> by = new java.util.TreeMap<>();
		int latest = -1;
		long latestAt = -1;
		for (History.Boss b : bosses)
		{
			History.Raid r = History.raid(b.key);
			if (r == null)
			{
				continue;
			}
			by.computeIfAbsent(r.raid, k -> new java.util.LinkedHashMap<>()).computeIfAbsent(r.mode, k -> new ArrayList<>()).add(b);
			if (b.lastAt() > latestAt)
			{
				latestAt = b.lastAt();
				latest = r.raid;
			}
		}
		if (by.isEmpty())
		{
			body.add(small("No raids yet", look.dim));
			return;
		}
		if (raidTab < 0 || !by.containsKey(raidTab))
		{
			raidTab = latest >= 0 ? latest : by.keySet().iterator().next();
		}
		body.add(raidTabs(by.keySet()));
		body.add(Box.createVerticalStrut(8));
		java.util.Map<String, List<History.Boss>> modes = by.get(raidTab);
		List<String> order = new ArrayList<>(java.util.Arrays.asList(History.RAID_MODES[raidTab]));
		for (String m : modes.keySet())
		{
			if (!order.contains(m))
			{
				order.add(m);       // a mode the game added later: after the known ones
			}
		}
		for (String m : order)
		{
			List<History.Boss> l = modes.get(m);
			if (l != null)
			{
				body.add(splitsMode(raidTab, m, l));
				body.add(Box.createVerticalStrut(6));
			}
		}
	}

	private JPanel raidTabs(java.util.Set<Integer> have)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(look.background);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(WIDTH, 24));
		for (int i = 0; i < History.RAID_SHORT.length; i++)
		{
			if (!have.contains(i))
			{
				continue;
			}
			final int raid = i;
			boolean on = raid == raidTab;
			JLabel l = new JLabel(History.RAID_SHORT[i], SwingConstants.CENTER);
			l.setFont(FontManager.getRunescapeBoldFont());
			l.setForeground(on ? Color.BLACK : look.dim);
			l.setOpaque(true);
			l.setBackground(on ? look.highlight : look.card);
			l.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
			l.setToolTipText(History.RAID_LONG[i]);
			l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			l.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					raidTab = raid;
					redraw();
				}
			});
			if (row.getComponentCount() > 0)
			{
				row.add(Box.createHorizontalStrut(4));
			}
			row.add(l);
		}
		row.add(Box.createHorizontalGlue());
		return row;
	}
	/** "2 players ....... 26:40" — orange when it is the mode's best, green when set in the last week; click = its graph. */
	// ---- TROPHY: the newest PB as a showpiece, then quiet rows with icons ----

	/** The newest PB set since PB History was installed: icon, name, the time big, what it beat and when. Null if none. */
	private JPanel hero()
	{
		History.Boss top = null;
		for (History.Boss b : bosses)
		{
			if (b.bestAt() > 0 && (top == null || b.bestAt() > top.bestAt()))
			{
				top = b;
			}
		}
		if (top == null)
		{
			return null;
		}
		JPanel card = new Themed.Card(new BorderLayout(10, 0), () -> look);
		card.setBackground(look.card);
		boolean plain = look.id == Looks.Id.PLAIN;
		// garlands and icicles hang from the top edge: the words start below them
		boolean hang = look.decor == Decor.Kind.CHRISTMAS || look.decor == Decor.Kind.SNOW;
		card.setBorder(plain ? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, gold()),
			new EmptyBorder(8, 10, 8, 10)) : new EmptyBorder(hang ? 16 : 9, 12, look.lava ? 16 : 9, 10));   // lava: the words sit above the pool
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.add(icon(top.key, 30), BorderLayout.WEST);
		JPanel words = new JPanel();
		words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
		words.setOpaque(false);
		words.add(small("Newest PB · " + top.name, Themed.onHero(look)));
		JLabel t = new JLabel(top.time(top.best()));
		t.setFont(FontManager.getRunescapeBoldFont().deriveFont(26f));
		t.setForeground(look.id == Looks.Id.PLAIN ? GOLD : look.number);   // on the theme's card: its own big-number colour
		t.setAlignmentX(LEFT_ALIGNMENT);
		words.add(t);
		double g = gain(top);
		words.add(small((g > 0 ? "-" + (g < 60 ? String.format(Locale.ROOT, "%.2fs", g) : Times.format(g)) + " · " : "") + ago(top.bestAt()),
			new Color(0x4FD16A)));
		if (top.kc > 0)
		{
			words.add(small("kc " + top.kc, Themed.onHero(look)));
		}
		card.add(words, BorderLayout.CENTER);
		List<Entry> steps = top.steps();
		long pbAt = steps.isEmpty() ? 0 : steps.get(steps.size() - 1).at;
		if (backend != null && pbAt > 0 && backend.hasPicture(pbAt))
		{
			PicStrip pic = new PicStrip(backend, pbAt, WIDTH - 16, 84, "The kill that set it");
			JPanel south = new JPanel(new BorderLayout());
			south.setOpaque(false);
			south.setBorder(new EmptyBorder(8, 0, 0, 0));
			south.add(pic, BorderLayout.CENTER);
			card.add(south, BorderLayout.SOUTH);
		}
		card.setMaximumSize(new Dimension(WIDTH, card.getPreferredSize().height));
		clickable(card, top.key, null);
		return card;
	}

	/** What's new after an update, with an ✕ that closes it for good. */
	private JPanel newsCard(String text)
	{
		JPanel c = new JPanel(new BorderLayout(6, 0));
		c.setBackground(look.card);
		c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(0x4FD16A)),
			new EmptyBorder(6, 8, 6, 6)));
		c.setAlignmentX(LEFT_ALIGNMENT);
		c.add(small(wrapped(small("", look.text), text, WIDTH - 40), look.text), BorderLayout.CENTER);
		JLabel x = small("✕", look.dim);
		x.setVerticalAlignment(SwingConstants.TOP);
		x.setToolTipText("Close it for good");
		x.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		x.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				backend.closeWhatsNew();
				redraw();
			}
		});
		c.add(x, BorderLayout.EAST);
		c.setMaximumSize(new Dimension(WIDTH, c.getPreferredSize().height + 20));
		return c;
	}

	/** The setup hint: a RuneLite setting PB History needs is off, and how to turn it on; the ✕ hides it until that changes. */
	private JPanel hintCard(java.util.List<String> items)
	{
		JPanel c = new JPanel(new BorderLayout(6, 0));
		c.setBackground(look.card);
		c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, look.accent),
			new EmptyBorder(6, 8, 6, 6)));
		c.setAlignmentX(LEFT_ALIGNMENT);
		JPanel lines = new JPanel();
		lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
		lines.setOpaque(false);
		for (int i = 0; i < items.size(); i++)
		{
			JLabel l = small(wrapped(small("", look.text), items.get(i), WIDTH - 40), look.text);
			l.setBorder(new EmptyBorder(i == 0 ? 0 : 4, 0, 0, 0));
			lines.add(l);
		}
		c.add(lines, BorderLayout.CENTER);
		c.setToolTipText("<html>A RuneLite setting this needs is off. PB History only reads it and never changes it.<br>"
			+ "Click the ✕ to hide this until the setting changes.</html>");
		JLabel x = small("✕", look.dim);
		x.setVerticalAlignment(SwingConstants.TOP);
		x.setToolTipText("Hide this until the setting changes");
		x.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		x.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				backend.closeSetupHint();
				redraw();
			}
		});
		c.add(x, BorderLayout.EAST);
		c.setMaximumSize(new Dimension(WIDTH, c.getPreferredSize().height + 20));
		return c;
	}
	// ---- SPLITS: a split-timer table — Boss · Gain · PB, striped, gold times for PBs set this week ----

	private JPanel splitsHeader(String first)
	{
		JPanel h = new JPanel(new BorderLayout());
		h.setOpaque(false);
		h.setAlignmentX(LEFT_ALIGNMENT);
		h.setBorder(new EmptyBorder(0, 4, 3, 4));
		h.setMaximumSize(new Dimension(WIDTH, 18));
		h.add(small(first, look.line), BorderLayout.WEST);
		JLabel g = small("Last", look.line);
		g.setHorizontalAlignment(SwingConstants.RIGHT);
		g.setToolTipText("Your last kill against your PB: green - = a new PB by that much, red + = that much slower");
		JLabel p = small("PB", look.line);
		p.setHorizontalAlignment(SwingConstants.RIGHT);
		h.add(columns(g, p), BorderLayout.EAST);
		return h;
	}

	/** One split: [icon] label ........ last  PB — Last = the last kill against the PB (green - / red +); gold = a PB this week. */
	private JPanel splitsRow(History.Boss b, String label, boolean stripe, boolean withIcon)
	{
		long at = b.bestAt();
		boolean gold = at > 0 && System.currentTimeMillis() - at < 7L * 86_400_000L;
		JPanel r = new JPanel(new BorderLayout(6, 0));
		r.setOpaque(true);
		r.setBackground(stripe ? stripe() : look.card);
		r.setBorder(new EmptyBorder(3, 4, 3, 4));
		r.setAlignmentX(LEFT_ALIGNMENT);
		if (withIcon)
		{
			r.add(icon(b.key, 16), BorderLayout.WEST);
		}
		// a long name ("TzHaar-Ket-Rak's First Challenge") wraps instead of being cut
		// the room the name really has: the row less its padding (8), the icon (16 + 6) and the Last / PB columns (108 + 6)
		// (lines broken here by measuring with the real font: Swing's HTML width hint clipped instead of wrapping)
		JLabel name = small("", look.text);
		name.setText(wrapped(name, label, WIDTH - 8 - (withIcon ? 22 : 0) - 114 - 4));
		r.add(name, BorderLayout.CENTER);
		double d = lastDelta(b);
		JLabel gl = small(Double.isNaN(d) ? "" : delta(d), Double.isNaN(d) ? look.text : d < -1e-9 ? new Color(0x4FD16A)
			: d > 1e-9 ? new Color(0xE0, 0x56, 0x4A) : look.dim);
		gl.setHorizontalAlignment(SwingConstants.RIGHT);
		JLabel pl = new JLabel(b.time(b.best()));
		pl.setFont(FontManager.getRunescapeBoldFont());
		pl.setForeground(gold ? gold() : look.text);
		pl.setHorizontalAlignment(SwingConstants.RIGHT);
		r.add(columns(gl, pl), BorderLayout.EAST);
		r.setToolTipText("<html>" + b.name + ": <b>" + b.time(b.best()) + "</b> - " + (at == 0 ? "set before PB History" : "set " + day(at))
			+ (b.kc > 0 ? " · kc " + b.kc : "") + (Double.isNaN(d) ? "" : "<br>last kill " + b.time(b.kills.get(b.kills.size() - 1).secs)
			+ (d < -1e-9 ? " - a new PB by " + delta(-d).substring(1) : d > 1e-9 ? " - " + delta(d) + " over your PB" : " - tied your PB"))
			+ "</html>");
		r.setMaximumSize(new Dimension(WIDTH, r.getPreferredSize().height));
		clickable(r, b.key, look.hover);
		return r;
	}

	/** A raid mode in splits: its name and best as a header strip, then a split per team size. */
	private JPanel splitsMode(int raid, String mode, List<History.Boss> list)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setOpaque(false);
		card.setAlignmentX(LEFT_ALIGNMENT);
		List<History.Boss> sizes = new ArrayList<>();
		History.Boss any = null;
		double best = Double.MAX_VALUE;
		for (History.Boss b : list)
		{
			if (History.raid(b.key).size.isEmpty())
			{
				any = b;
			}
			else
			{
				sizes.add(b);
			}
			best = Math.min(best, b.best());
		}
		sizes.sort(java.util.Comparator.comparingInt(b -> History.sizeOrder(History.raid(b.key).size)));
		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(true);
		head.setBackground(Themed.mix(look.card, Color.BLACK, 0.13));
		head.setBorder(new EmptyBorder(4, 4, 4, 4));
		head.setAlignmentX(LEFT_ALIGNMENT);
		head.setMaximumSize(new Dimension(WIDTH, 24));
		JLabel n = new JLabel(History.modeName(raid, mode));
		n.setFont(FontManager.getRunescapeBoldFont());
		n.setForeground(look.highlight);
		head.add(n, BorderLayout.WEST);
		History.Boss top = any != null ? any : sizes.isEmpty() ? null : sizes.get(0);
		JLabel t = new JLabel(top == null ? "" : top.time(best));
		t.setFont(FontManager.getRunescapeBoldFont());
		t.setForeground(look.highlight);
		head.add(t, BorderLayout.EAST);
		if (any != null)
		{
			clickable(head, any.key, null);
		}
		card.add(head);
		int i = 0;
		for (History.Boss b : sizes)
		{
			card.add(splitsRow(b, History.sizeName(History.raid(b.key).size), i++ % 2 == 1, false));
		}
		card.setMaximumSize(new Dimension(WIDTH, card.getPreferredSize().height));
		return card;
	}

	/** {@code text} in lines no wider than {@code max} px in the label's font, broken at spaces or after hyphens */
	static String wrapped(JLabel l, String text, int max)
	{
		java.awt.FontMetrics fm = l.getFontMetrics(l.getFont());
		if (fm.stringWidth(text) <= max)
		{
			return text;
		}
		List<String> parts = new ArrayList<>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("[^ -]+-?|\\s+").matcher(text);
		while (m.find())
		{
			parts.add(m.group());
		}
		StringBuilder out = new StringBuilder("<html>");
		StringBuilder line = new StringBuilder();
		for (String part : parts)
		{
			if (line.length() > 0 && fm.stringWidth((line + part).trim()) > max)
			{
				out.append(line.toString().trim()).append("<br>");
				line.setLength(0);
			}
			line.append(part);
		}
		return out.append(line.toString().trim()).append("</html>").toString();
	}

	/** the Gain and PB columns at fixed widths (an even split cut "1:25.80" short) */
	private static JPanel columns(JLabel gain, JLabel pb)
	{
		JPanel c = new JPanel(new BorderLayout(4, 0));
		c.setOpaque(false);
		gain.setPreferredSize(new Dimension(42, 16));
		pb.setPreferredSize(new Dimension(62, 16));
		c.add(gain, BorderLayout.WEST);
		c.add(pb, BorderLayout.EAST);
		return c;
	}

	/**
	 * The last timed kill against the PB before it (seconds): negative = a new PB by that much, positive = that much
	 * slower; NaN = no timed kill, or nothing earlier to compare with. The PB line RuneLite saved for that same kill
	 * (the same time, within seconds) is not "earlier".
	 */
	static double lastDelta(History.Boss b)
	{
		if (b.kills.isEmpty())
		{
			return Double.NaN;
		}
		Entry k = b.kills.get(b.kills.size() - 1);
		double before = Double.MAX_VALUE;
		for (Entry e : b.pbs)
		{
			boolean same = Math.abs(e.at - k.at) < 10_000 && Math.abs(e.secs - k.secs) < 0.05;
			if (!same && e.at <= k.at)
			{
				before = Math.min(before, e.secs);
			}
		}
		for (Entry e : b.kills)
		{
			if (e != k && e.at <= k.at)
			{
				before = Math.min(before, e.secs);
			}
		}
		return before == Double.MAX_VALUE ? Double.NaN : k.secs - before;
	}

	/** -4.20 / +2.40 / +1:05 / 0.00 */
	static String delta(double d)
	{
		double a = Math.abs(d);
		String n = a < 60 ? String.format(Locale.ROOT, "%.2f", a) : Times.format(a);
		return (d < -1e-9 ? "-" : d > 1e-9 ? "+" : "") + n;
	}

	/** how much the latest PB beat the one before it (seconds); 0 = no earlier PB */
	private static double gain(History.Boss b)
	{
		List<Entry> st = b.steps();
		return st.size() >= 2 ? st.get(st.size() - 2).secs - st.get(st.size() - 1).secs : 0;
	}

	/** the boss's icon at {@code size} px, or an empty square of that size */
	private JLabel icon(String key, int size)
	{
		JLabel l = new JLabel();
		l.setPreferredSize(new Dimension(size, size));
		l.setMinimumSize(new Dimension(size, size));
		java.awt.image.BufferedImage img = icons.of(key);
		if (img != null)
		{
			double k = Math.min((double) size / img.getWidth(), (double) size / img.getHeight());
			int w = Math.max(1, (int) Math.round(img.getWidth() * k)), h = Math.max(1, (int) Math.round(img.getHeight() * k));
			l.setIcon(new javax.swing.ImageIcon(img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH)));
			l.setHorizontalAlignment(SwingConstants.CENTER);
		}
		l.setVerticalAlignment(SwingConstants.TOP);
		return l;
	}

	/** Click = the boss's page; {@code hover} lights the row (null: no light). */
	private void clickable(JPanel p, String key, Color hover)
	{
		p.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		Color normal = p.getBackground();
		p.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				open = key;
				redraw();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (hover != null)
				{
					p.setBackground(hover);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (hover != null)
				{
					p.setBackground(normal);
				}
			}
		});
	}

	/** The tab row: a word per tab (Other only when it has any), the picked one white and underlined in orange. */
	private JPanel tabs(java.util.Map<String, Integer> count)
	{
		// each tab as wide as its word (an even grid cut "Challenges" short), the space shared out between them
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(look.background);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(WIDTH, 26));
		for (String t : History.TABS)
		{
			if (History.OTHER.equals(t) && count.get(t) == 0)
			{
				continue;
			}
			boolean on = t.equals(tab);
			JLabel l = new JLabel(t, SwingConstants.CENTER);
			l.setFont(on ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
			l.setForeground(on ? look.text : count.get(t) == 0 ? look.line : look.dim);
			l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, on ? look.highlight
				: look.background), BorderFactory.createEmptyBorder(0, 2, 3, 2)));
			l.setToolTipText(count.get(t) + (count.get(t) == 1 ? " PB" : " PBs"));
			l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			l.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					tab = t;
					tabPicked = true;
					redraw();
				}
			});
			if (row.getComponentCount() > 0)
			{
				row.add(Box.createHorizontalGlue());
			}
			row.add(l);
		}
		return row;
	}

	// ---- one boss ----

	private void detail(History.Boss b)
	{
		JLabel back = small("< All bosses", look.dim);
		back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		back.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				open = null;
				redraw();
			}
		});
		body.add(back);
		body.add(Box.createVerticalStrut(6));
		body.add(title(b.name));
		JLabel pb = new JLabel(b.time(b.best()));
		pb.setFont(FontManager.getRunescapeBoldFont().deriveFont(28f));
		pb.setForeground(look.highlight);
		pb.setAlignmentX(LEFT_ALIGNMENT);
		if (b.kc > 0)
		{
			// the kill count beside the PB, in the row's empty right side (user 2026-11-08: "in here should say your kc")
			JLabel count = new JLabel(String.format("%,d", b.kc));
			count.setFont(FontManager.getRunescapeBoldFont());
			count.setForeground(look.text);
			count.setAlignmentX(RIGHT_ALIGNMENT);
			JLabel what = small("kill count", look.dim);
			what.setAlignmentX(RIGHT_ALIGNMENT);
			JPanel kc = new JPanel();
			kc.setLayout(new BoxLayout(kc, BoxLayout.Y_AXIS));
			kc.setOpaque(false);
			kc.add(Box.createVerticalStrut(6));
			kc.add(count);
			kc.add(what);
			kc.setToolTipText("Your " + b.name + " kill count, as the game last told you (RuneLite's Chat Commands keeps it)");
			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			row.add(pb, BorderLayout.WEST);
			row.add(kc, BorderLayout.EAST);
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.setMaximumSize(new Dimension(WIDTH, row.getPreferredSize().height));
			body.add(row);
		}
		else
		{
			body.add(pb);
		}
		long at = b.bestAt();
		List<Entry> steps = b.steps();
		Entry last = steps.isEmpty() ? null : steps.get(steps.size() - 1);
		body.add(small(at == 0 ? "your PB from before PB History" : "set " + day(at) + (last != null && last.kc > 0 ? " at kc " + last.kc : ""),
			look.dim));
		if (backend != null && at > 0 && backend.hasPicture(at))
		{
			body.add(Box.createVerticalStrut(6));
			PicStrip pic = new PicStrip(backend, at, WIDTH, 110, "The kill that set this PB");
			pic.setAlignmentX(LEFT_ALIGNMENT);
			body.add(pic);
		}
		goal(b);
		body.add(Box.createVerticalStrut(8));
		PbChart chart = new PbChart(b, WIDTH);
		chart.theme(look);
		chart.setAlignmentX(LEFT_ALIGNMENT);
		body.add(chart);
		body.add(Box.createVerticalStrut(6));
		if (!b.kills.isEmpty())
		{
			body.add(small("average " + b.time(b.average(0)) + (b.kills.size() > 10 ? " · last 10: " + b.time(b.average(10)) : ""),
				look.dim));
			// consistency: how often you play at your PB's pace, and your typical kill
			int near = b.within(0.05), n = b.kills.size();
			String why = "<html>How often your kills come close to your PB (within 5%), and your typical kill<br>"
				+ "(half your kills were faster, half slower). A high share = your PB is your real pace, not a lucky one.</html>";
			JLabel pace = small("within 5% of your PB: " + near + " of " + n + (n == 1 ? " kill" : " kills")
				+ " (" + Math.round(100.0 * near / n) + "%)", look.dim);
			pace.setToolTipText(why);
			body.add(pace);
			JLabel typical = small("typical kill: " + b.time(b.median()), look.dim);
			typical.setToolTipText(why);
			body.add(typical);
		}
		else
		{
			body.add(small("<html>Kill times show here from your next kill.</html>", look.dim));
		}
		rooms(b);
		body.add(Box.createVerticalStrut(10));
		JLabel h = new JLabel("Personal bests");
		h.setFont(FontManager.getRunescapeBoldFont());
		h.setForeground(look.text);
		h.setAlignmentX(LEFT_ALIGNMENT);
		body.add(h);
		body.add(Box.createVerticalStrut(4));
		for (int i = steps.size() - 1; i >= 0; i--)
		{
			Entry e = steps.get(i);
			Entry before = i > 0 ? steps.get(i - 1) : null;
			JPanel r = new JPanel(new BorderLayout());
			r.setBackground(look.card);
			r.setBorder(new EmptyBorder(5, 8, 5, 8));
			r.setAlignmentX(LEFT_ALIGNMENT);
			r.setMaximumSize(new Dimension(WIDTH, 40));
			JLabel t = new JLabel(b.time(e.secs));
			t.setFont(FontManager.getRunescapeBoldFont());
			t.setForeground(i == steps.size() - 1 ? look.highlight : look.text);
			r.add(t, BorderLayout.WEST);
			JLabel by = small(before != null ? Times.faster(before.secs - e.secs) : "", new Color(0x4FD16A));
			by.setHorizontalAlignment(SwingConstants.RIGHT);
			r.add(by, BorderLayout.EAST);
			r.add(small(e.at == 0 ? "before PB History" : day(e.at) + (e.kc > 0 ? " · kc " + e.kc : ""), look.dim),
				BorderLayout.SOUTH);
			body.add(r);
			body.add(Box.createVerticalStrut(3));
		}
	}

	/**
	 * A raid's rooms: each room's best and last time (every team size of this mode: the room messages don't say the size),
	 * and for the Theatre of Blood and the Tombs of Amascut, whose rooms never change, the sum of the best rooms — the run
	 * your PB could be.
	 */
	private void rooms(History.Boss b)
	{
		if (b.rooms.isEmpty())
		{
			return;
		}
		java.util.Map<String, double[]> by = new java.util.LinkedHashMap<>();   // room -> {best, last}
		List<String> fixed = Rooms.fixed(b.key);
		if (fixed != null)
		{
			for (String r : fixed)
			{
				by.put(r, null);
			}
		}
		for (Entry e : b.rooms)
		{
			double[] t = by.get(e.room);
			by.put(e.room, t == null ? new double[]{e.secs, e.secs} : new double[]{Math.min(t[0], e.secs), e.secs});
		}
		List<String> order = Rooms.order(b.key);
		if (fixed == null && order != null)
		{
			// The Fractured Archive: the rooms you've done, in stage order (one of two rooms at stages 2-4), then any other
			java.util.Map<String, double[]> sorted = new java.util.LinkedHashMap<>();
			for (String r : order)
			{
				for (java.util.Map.Entry<String, double[]> x : by.entrySet())
				{
					if (x.getKey().equalsIgnoreCase(r))
					{
						sorted.put(x.getKey(), x.getValue());
					}
				}
			}
			for (java.util.Map.Entry<String, double[]> x : by.entrySet())
			{
				sorted.putIfAbsent(x.getKey(), x.getValue());
			}
			by = sorted;
		}
		body.add(Box.createVerticalStrut(10));
		JLabel h = new JLabel("Rooms");
		h.setFont(FontManager.getRunescapeBoldFont());
		h.setForeground(look.text);
		h.setAlignmentX(LEFT_ALIGNMENT);
		h.setToolTipText("<html>Every team size of " + Times.name(Rooms.mode(b.key)) + ": the game's room messages don't say the "
			+ "team size" + (Rooms.mode(b.key).startsWith("chambers of xeric") ? "<br>In the Chambers of Xeric the room times come "
			+ "from the CoX Timers of the CoX Additions plugin<br>(the game itself only times each floor)" : "") + "</html>");
		body.add(h);
		body.add(small("best · last  (all team sizes)", look.dim));
		if (fixed != null)
		{
			double sum = 0;
			int have = 0;
			for (String r : fixed)
			{
				double[] t = by.get(r);
				if (t != null)
				{
					sum += t[0];
					have++;
				}
			}
			JLabel line;
			if (have == fixed.size())
			{
				double pb = b.best();
				double gap = pb - sum;
				line = small("Sum of best: " + Times.format(sum, true) + (pb > 0 && gap > 0.05 ? " · " + (gap < 60
					? String.format(Locale.ROOT, "%.2fs", gap) : Times.format(gap)) + " to find" : ""), gold());
			}
			else
			{
				line = small("Sum of best: needs all " + fixed.size() + " rooms (" + have + " so far)", look.dim);
			}
			line.setToolTipText("<html>Your best time in each room, added up: the run your PB could be if every room went as<br>"
				+ "well as your best. The gap to your PB is time you've already shown you can save. (A raid's total also has the<br>"
				+ "walking between rooms, so it can't quite reach this.)</html>");
			body.add(line);
		}
		int i = 0;
		for (java.util.Map.Entry<String, double[]> r : by.entrySet())
		{
			double[] t = r.getValue();
			JPanel row = new JPanel(new BorderLayout());
			row.setBackground(i++ % 2 == 1 ? stripe() : look.card);
			row.setBorder(new EmptyBorder(3, 6, 3, 6));
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.add(small(Rooms.label(r.getKey()), look.text), BorderLayout.WEST);
			String times = t == null ? "-" : Times.format(t[0], true) + " · " + Times.format(t[1], true);
			JLabel tl = small(times, t != null && Math.abs(t[0] - t[1]) < 1e-9 ? gold() : look.dim);
			tl.setHorizontalAlignment(SwingConstants.RIGHT);
			row.add(tl, BorderLayout.EAST);
			row.setMaximumSize(new Dimension(WIDTH, 20));
			body.add(row);
		}
	}

	/** The goal line: the goal, how far off you are and a bar; or a link to set one. */
	private void goal(History.Boss b)
	{
		if (backend == null)
		{
			return;
		}
		Double goal = backend.goal(b.key);
		body.add(Box.createVerticalStrut(6));
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		double best = b.best();
		String text;
		Color c;
		if (goal == null)
		{
			text = "No goal time yet";
			c = look.dim;
		}
		else if (best > 0 && best <= goal + 1e-9)
		{
			text = "Goal " + b.time(goal) + " reached!";
			c = new Color(0x4FD16A);
		}
		else
		{
			double off = best - goal;
			text = "Goal " + b.time(goal) + (best > 0 ? " · " + (off < 60 ? String.format(Locale.ROOT, "%.2fs", off) : Times.format(off)) + " to go" : "");
			c = gold();
		}
		row.add(small(text, c), BorderLayout.WEST);
		JLabel set = small(goal == null ? "Set a goal" : "Change", look.highlight);
		set.setToolTipText("A time to beat for " + b.name + ": a chat line tells you the moment a PB beats it");
		set.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		set.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				Object typed = javax.swing.JOptionPane.showInputDialog(PbPanel.this, "A goal time for " + b.name
					+ " (like 2:00 or 1:25.4). Leave it empty for none:", "Goal time", javax.swing.JOptionPane.PLAIN_MESSAGE,
					null, null, goal == null ? "" : Times.format(goal, true));
				if (typed == null)
				{
					return;
				}
				String t = typed.toString().trim();
				double secs = t.isEmpty() ? 0 : Times.parse(t);
				if (!t.isEmpty() && secs <= 0)
				{
					javax.swing.JOptionPane.showMessageDialog(PbPanel.this, "That isn't a time: try 2:00 or 1:25.4.");
					return;
				}
				backend.setGoal(b.key, secs > 0 ? secs : null);
			}
		});
		row.add(set, BorderLayout.EAST);
		JPanel box = new JPanel(new BorderLayout(0, 3));
		box.setOpaque(false);
		box.setAlignmentX(LEFT_ALIGNMENT);
		box.add(row, BorderLayout.NORTH);
		if (goal != null && best > 0 && best > goal)
		{
			List<Entry> steps = b.steps();
			double from = steps.isEmpty() ? best : steps.get(0).secs;
			double done = from <= goal ? 1 : Math.max(0, Math.min(1, (from - best) / (from - goal)));
			box.add(new Bar(done, look.card, gold()), BorderLayout.SOUTH);
		}
		box.setMaximumSize(new Dimension(WIDTH, box.getPreferredSize().height));
		body.add(box);
	}

	/** A thin progress bar: from your first PB to the goal. */
	private static final class Bar extends javax.swing.JComponent
	{
		private final double done;
		private final Color track;
		private final Color fill;

		Bar(double done, Color track, Color fill)
		{
			this.done = done;
			this.track = track;
			this.fill = fill;
			setPreferredSize(new Dimension(WIDTH, 6));
			setToolTipText(Math.round(done * 100) + "% of the way from your first PB to the goal");
		}

		@Override
		protected void paintComponent(java.awt.Graphics g)
		{
			g.setColor(track);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(fill);
			g.fillRect(0, 0, (int) Math.round(getWidth() * done), getHeight());
		}
	}

	// ---- bits ----

	private static String day(long at)
	{
		return DAY.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()));
	}

	/** "today", "yesterday", "3 days ago", else the date */
	static String ago(long at)
	{
		LocalDate d = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate();
		long days = ChronoUnit.DAYS.between(d, LocalDate.now());
		return days <= 0 ? "today" : days == 1 ? "yesterday" : days < 14 ? days + " days ago" : day(at);
	}

	private JLabel title(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(look.title);
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel small(String text, Color c)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(c);
		l.setAlignmentX(LEFT_ALIGNMENT);
		l.setBorder(BorderFactory.createEmptyBorder());
		return l;
	}
}
