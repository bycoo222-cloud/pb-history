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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One boss's times, oldest on the left: every timed kill a grey dot, the personal best a line stepping down, an orange
 * dot where a new PB was set. Lower is faster. Hover a dot for its time and date.
 */
final class PbChart extends JPanel
{
	static final Color PB = ColorScheme.BRAND_ORANGE;
	private static final Color KILL = new Color(150, 150, 150);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
	private static final int L = 50, R = 8, T = 8, B = 18;

	private static final class Point
	{
		final Entry e;
		final double best;   // the PB line here
		final boolean step;  // a new PB here

		Point(Entry e, double best, boolean step)
		{
			this.e = e;
			this.best = best;
			this.step = step;
		}
	}

	private final List<Point> points = new ArrayList<>();
	private final History.Boss boss;
	private double lo, hi;
	private int kills;

	PbChart(History.Boss b, int width)
	{
		boss = b;
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(width, 150));
		setMaximumSize(new Dimension(width, 150));
		setToolTipText("");   // switches tooltips on; the text comes from getToolTipText(MouseEvent)
		List<Entry> all = new ArrayList<>(b.pbs);
		all.addAll(b.kills);
		all.sort((x, y) -> x.at != y.at ? Long.compare(x.at, y.at) : Boolean.compare(!x.isPb(), !y.isPb()));
		double best = Double.MAX_VALUE;
		lo = Double.MAX_VALUE;
		hi = 0;
		for (Entry e : all)
		{
			boolean step = e.secs < best - 1e-9;
			best = Math.min(best, e.secs);
			// a PB line made from a kill's own time adds nothing new: the kill's dot shows it
			if (e.isPb() && !step)
			{
				continue;
			}
			points.add(new Point(e, best, step));
			kills += e.isPb() ? 0 : 1;
			lo = Math.min(lo, e.secs);
			hi = Math.max(hi, e.secs);
		}
		if (points.isEmpty())
		{
			lo = 0;
			hi = 1;
		}
		double pad = Math.max((hi - lo) * 0.08, Math.max(1, hi * 0.02));
		lo = Math.max(0, lo - pad);
		hi = hi + pad;
	}

	private double x(int i)
	{
		int n = points.size();
		return n <= 1 ? L + (getWidth() - L - R) / 2.0 : L + i * (getWidth() - L - R) / (double) (n - 1);
	}

	private double y(double secs)
	{
		return T + (hi - secs) / (hi - lo) * (getHeight() - T - B);
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		super.paintComponent(g0);
		Graphics2D g = (Graphics2D) g0;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(FontManager.getRunescapeSmallFont());
		// the axis: the slowest and fastest times shown
		g.setColor(new Color(255, 255, 255, 25));
		g.drawLine(L, (int) y(hi - (hi - lo) * 0.08), getWidth() - R, (int) y(hi - (hi - lo) * 0.08));
		g.drawLine(L, (int) y(lo + (hi - lo) * 0.08), getWidth() - R, (int) y(lo + (hi - lo) * 0.08));
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		drawRight(g, Times.format(Math.round(hi - (hi - lo) * 0.08)), L - 4, (int) y(hi - (hi - lo) * 0.08) + 4);
		drawRight(g, Times.format(Math.round(lo + (hi - lo) * 0.08)), L - 4, (int) y(lo + (hi - lo) * 0.08) + 4);
		if (points.isEmpty())
		{
			return;
		}
		// the PB line, stepping down
		Path2D line = new Path2D.Double();
		for (int i = 0; i < points.size(); i++)
		{
			double px = x(i), py = y(points.get(i).best);
			if (i == 0)
			{
				line.moveTo(px, py);
			}
			else
			{
				line.lineTo(px, y(points.get(i - 1).best));
				line.lineTo(px, py);
			}
		}
		if (points.size() == 1)
		{
			line.lineTo(getWidth() - R, y(points.get(0).best));
		}
		g.setColor(PB);
		g.setStroke(new BasicStroke(2f));
		g.draw(line);
		// the kills, then the new PBs on top
		for (int i = 0; i < points.size(); i++)
		{
			Point p = points.get(i);
			if (!p.e.isPb())
			{
				g.setColor(KILL);
				fillDot(g, x(i), y(p.e.secs), 4);
			}
		}
		for (int i = 0; i < points.size(); i++)
		{
			Point p = points.get(i);
			if (p.step)
			{
				g.setColor(ColorScheme.DARKER_GRAY_COLOR);
				fillDot(g, x(i), y(p.e.secs), 9);
				g.setColor(PB);
				fillDot(g, x(i), y(p.e.secs), 7);
			}
		}
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g.drawString(kills == 1 ? "1 timed kill" : kills + " timed kills", L, getHeight() - 4);
	}

	private static void fillDot(Graphics2D g, double cx, double cy, double d)
	{
		g.fill(new java.awt.geom.Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d));
	}

	private static void drawRight(Graphics2D g, String s, int right, int base)
	{
		g.drawString(s, right - g.getFontMetrics().stringWidth(s), base);
	}

	@Override
	public String getToolTipText(MouseEvent ev)
	{
		int best = -1;
		double near = 12;
		for (int i = 0; i < points.size(); i++)
		{
			double d = Math.hypot(ev.getX() - x(i), ev.getY() - y(points.get(i).e.secs));
			if (d < near)
			{
				near = d;
				best = i;
			}
		}
		if (best < 0)
		{
			return null;
		}
		Point p = points.get(best);
		String when = p.e.at == 0 ? "before PB History" : DAY.format(Instant.ofEpochMilli(p.e.at).atZone(ZoneId.systemDefault()));
		String what = p.step ? "New PB " + boss.time(p.e.secs) : "Kill " + boss.time(p.e.secs);
		return "<html>" + what + (p.e.kc > 0 ? " (kc " + p.e.kc + ")" : "") + "<br>" + when + "</html>";
	}
}
