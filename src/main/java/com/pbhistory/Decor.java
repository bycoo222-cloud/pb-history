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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Collections;
import java.util.List;

/**
 * The themes' decorations, shared by the four plugins like {@link Looks}: the top card's scene, a small icon before each
 * heading, a scene over the whole panel's background (behind see-through cards) and a trim on cards. User, 2026-11-08:
 * Halloween's "background with the transparent boxes over it is beautiful"; Christmas "just some lights", Autumn "mostly
 * just a color", Infernal "make the lava drip". Every place comes from a fixed seed, so nothing flickers or moves between
 * paints. Nothing bright is drawn over a rectangle the caller says to keep clear (text, icons, pictures): only faint dark
 * silhouettes may pass behind text, and those can only make it easier to read.
 */
final class Decor
{
	enum Kind
	{
		NONE, FOG, ECTO, LAVA, LEAVES, HALLOWEEN, SNOW, CHRISTMAS
	}

	private static final Color[] AUTUMN_LEAF = {new Color(0xD84A1E), new Color(0xF08C22), new Color(0xE8BC3A), new Color(0xB0321A)};
	/** Leaves in the background: darker, so a heading over one keeps 4.5:1 (measured against each theme's colours). */
	private static final Color[] BG_LEAF = {new Color(0x8E3A18), new Color(0xA04A1A), new Color(0x7C5A1C), new Color(0x7A3A16)};
	private static final Color[] BAUBLE = {new Color(0xE23A3A), new Color(0xF2C84A), new Color(0xC8D4E0), new Color(0x3A8AE0)};
	private static final Color[] BULB = {new Color(0xFF4A4A), new Color(0xFFD24A), new Color(0x4AD86A), new Color(0x5AA8FF)};

	private Decor()
	{
	}

	// ---- the top card ----

	/** The top card's scene in a {@code w} x {@code h} box (the caller clips to the card's shape). */
	static void hero(Graphics2D g, Kind k, int w, int h, List<Rectangle> clear)
	{
		if (k == null || k == Kind.NONE)
		{
			return;
		}
		List<Rectangle> keep = clear == null ? Collections.emptyList() : clear;
		Composite was = g.getComposite();
		Stroke stroke = g.getStroke();
		AffineTransform at = g.getTransform();
		switch (k)
		{
			case FOG:
				fogHero(g, w, h, keep);
				break;
			case ECTO:
				wisps(g, w, h, keep);
				break;
			case LAVA:
				embers(g, w, h, keep, 16, 0.55f);
				lavaRim(g, w, keep, 0, 16);
				break;
			case LEAVES:
				skyline(g, w, h, false, new Color(0, 0, 0, 70));
				leafGround(g, w, h, keep);
				leafPile(g, w - 34, h, 30, keep, 5);
				for (int len : new int[]{Math.min(78, w / 3), 56, 40})
				{
					if (free(keep, w - len, -4, len, 24))
					{
						branch(g, w, 0, -1, len, keep, 1f, 1f, AUTUMN_LEAF, 7);
						break;
					}
				}
				leaves(g, w, h, keep, 26, AUTUMN_LEAF, 0.65f);
				break;
			case HALLOWEEN:
				halloween(g, w, h, keep);
				break;
			case SNOW:
				skyline(g, w, h, true, new Color(0, 0, 0, 64));
				frost(g, 0, 0, 1, 26, keep, 0.6f);
				frost(g, w, 0, -1, 26, keep, 0.6f);
				icicles(g, w, keep);
				forest(g, w, h, keep, false);
				flakes(g, w, h, keep, 44, 0.5f, true);
				ground(g, w, h, keep, new Color(0xF2F8FF), 0.88f);
				break;
			case CHRISTMAS:
				skyline(g, w, h, true, new Color(0, 0, 0, 70));
				flakes(g, w, h, keep, 26, 0.45f, false);
				forest(g, w, h, keep, true);
				ground(g, w, h, keep, new Color(0xF6FAFF), 0.9f);
				sparkles(g, w, h, keep, 5);
				holly(g, 16, h - 14, 0.9, keep);
				garland(g, w, keep);
				break;
			default:
				break;
		}
		g.setTransform(at);
		g.setStroke(stroke);
		g.setComposite(was);
	}

	// ---- the panel's background ----

	/**
	 * The panel's background scene, under every card (cards are see-through in these themes, {@link Looks#cardAlpha}):
	 * trees peeking in from the edges, falling snow or leaves, bats, embers, and a ground scene along the panel's bottom.
	 * Big shapes are dark (a heading's text over them only gets easier to read); bright dots stay small and faint.
	 */
	static void background(Graphics2D g, Kind k, int w, int h)
	{
		if (k == null || k == Kind.NONE || w <= 0 || h <= 0)
		{
			return;
		}
		Composite was = g.getComposite();
		Stroke stroke = g.getStroke();
		AffineTransform at = g.getTransform();
		int cell = 180;
		for (int top = 0, n = 0; top < h; top += cell, n++)
		{
			long s = 977L * (n + 1) + k.ordinal() * 131L;
			boolean left = n % 2 == 0;
			switch (k)
			{
				case CHRISTMAS:
				case SNOW:
				{
					// a pine peeking in from one edge, half off the panel
					s = next(s);
					double ph = 70 + (s >>> 40) % 40;
					double px = left ? 3 + (s >>> 33) % 6 : w - 3 - (s >>> 33) % 6;
					alpha(g, 1f);
					pine(g, px, top + cell - 8, ph, k == Kind.SNOW ? new Color(0x22364E) : new Color(0x173A24),
						new Color(255, 255, 255, 60), false, false, s);
					// falling snow
					for (int i = 0; i < 10; i++)
					{
						s = next(s);
						int x = (int) ((s >>> 33) % Math.max(1, w));
						int y = top + (int) ((s >>> 13) % cell);
						alpha(g, 0.12f + ((s >>> 40) % 15) / 100f);   // at most 0.26: a heading over a flake keeps 4.5:1
						g.setColor(Color.WHITE);
						if (k == Kind.SNOW && i < 2)
						{
							g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
							flake(g, x, y, 4);
						}
						else
						{
							g.fillOval(x - 1, y - 1, 3, 3);
						}
					}
					if (k == Kind.SNOW && n % 2 == 1)
					{
						// frost growing in from the other edge
						s = next(s);
						frost(g, left ? w : 0, top + (int) ((s >>> 13) % (cell - 30)), left ? -1 : 1, 30, Collections.emptyList(), 0.26f);
					}
					if (k == Kind.CHRISTMAS)
					{
						s = next(s);
						alpha(g, 0.32f);
						star(g, (int) ((s >>> 33) % Math.max(1, w)), top + (int) ((s >>> 13) % cell), 3, new Color(0xF2C84A));
					}
					break;
				}
				case LEAVES:
				{
					// a branch reaching in from one edge, and leaves falling
					s = next(s);
					branch(g, left ? 0 : w, top + 20 + (int) ((s >>> 13) % (cell - 60)), left ? 1 : -1, 46, Collections.emptyList(),
						0.55f, 0.46f, BG_LEAF, s);
					for (int i = 0; i < 6; i++)
					{
						s = next(s);
						alpha(g, 0.3f + ((s >>> 40) % 17) / 100f);
						leaf(g, (int) ((s >>> 33) % Math.max(1, w)), top + (int) ((s >>> 13) % cell), 5 + (s >>> 50) % 3,
							((s >>> 20) % 628) / 100.0, BG_LEAF[(int) ((s >>> 45) % BG_LEAF.length)], (s >>> 52) % 2 == 0);
					}
					break;
				}
				case HALLOWEEN:
					for (int i = 0; i < 2; i++)
					{
						s = next(s);
						alpha(g, 0.5f);
						bat(g, (int) ((s >>> 33) % Math.max(1, w)), top + (int) ((s >>> 13) % cell), 7 + (int) ((s >>> 50) % 4),
							new Color(0x5E3E78));
					}
					break;
				case ECTO:
					for (int i = 0; i < 5; i++)
					{
						s = next(s);
						alpha(g, 0.08f + ((s >>> 40) % 16) / 100f);
						g.setColor(new Color(0x9EFFD6));
						int r = 1 + (int) ((s >>> 50) % 2);
						g.fillOval((int) ((s >>> 33) % Math.max(1, w)), top + (int) ((s >>> 13) % cell), r * 2, r * 2);
					}
					break;
				case LAVA:
					for (int i = 0; i < 6; i++)
					{
						s = next(s);
						alpha(g, 0.12f + ((s >>> 40) % 15) / 100f);
						g.setColor(i % 2 == 0 ? new Color(0xFF8A2A) : new Color(0xFFC060));
						g.fillOval((int) ((s >>> 33) % Math.max(1, w)), top + (int) ((s >>> 13) % cell), 2, 2);
					}
					for (int i = 0; i < 2; i++)
					{
						s = next(s);
						alpha(g, 0.16f);
						crack(g, (int) ((s >>> 33) % Math.max(1, w)), top + (int) ((s >>> 13) % cell), s);
					}
					break;
				case FOG:
					s = next(s);
					alpha(g, 0.035f);
					g.setColor(Color.WHITE);
					g.fill(new Ellipse2D.Double((s >>> 33) % Math.max(1, w) - 60, top + (s >>> 13) % cell, 150, 34));
					break;
				default:
					break;
			}
		}
		// the ground along the panel's bottom
		alpha(g, 1f);
		switch (k)
		{
			case CHRISTMAS:
			case SNOW:
			{
				// dark trees with faint snow: a heading over them stays easy to read
				Color fill = k == Kind.CHRISTMAS ? new Color(0x143520) : new Color(0x22385A);
				double[][] trees = {{w * 0.12, 54}, {w * 0.36, 74}, {w * 0.62, 46}, {w * 0.86, 64}};
				for (int i = 0; i < trees.length; i++)
				{
					pine(g, trees[i][0], h - 9, trees[i][1], fill, new Color(255, 255, 255, 60), false, false, 500 + i);
				}
				// snow in the dark, not white: text at the panel's foot (a link, a button) sits on it and stays readable
				alpha(g, 1f);
				bank(g, w, h, 12, k == Kind.CHRISTMAS ? new Color(0x2A3C34) : new Color(0x30425A));
				break;
			}
			case LEAVES:
				autumnTree(g, w + 4, h - 6, 92);          // half off the right edge: headings start at the left
				break;
			case LAVA:
				g.setPaint(new GradientPaint(0, h - 26, new Color(0xFF, 0x50, 0x00, 0), 0, h, new Color(0xFF, 0x6A, 0x10, 90)));
				g.fillRect(0, h - 26, w, 26);
				break;
			default:
				break;
		}
		if (k == Kind.HALLOWEEN)
		{
			alpha(g, 0.28f);
			web(g, w, 0, 36, new Color(0xD8CCE8));
		}
		g.setTransform(at);
		g.setStroke(stroke);
		g.setComposite(was);
	}

	// ---- headings and cards ----

	/** A small icon for a heading in a {@code size} box with its top-left at x, y; the width it took (0 = none). */
	static int motif(Graphics2D g, Kind k, int x, int y, int size)
	{
		if (k == null || k == Kind.NONE)
		{
			return 0;
		}
		Composite was = g.getComposite();
		Stroke stroke = g.getStroke();
		AffineTransform at = g.getTransform();
		double cx = x + size / 2.0;
		double cy = y + size / 2.0;
		switch (k)
		{
			case FOG:
				g.setColor(new Color(0x9A9CA2));
				g.fill(tombstone(x + size * 0.2, y + size * 0.1, size * 0.6, size * 0.85));
				g.setColor(new Color(0x55575C));
				g.drawLine((int) cx, (int) (y + size * 0.35), (int) cx, (int) (y + size * 0.65));
				g.drawLine((int) (cx - size * 0.15), (int) (y + size * 0.45), (int) (cx + size * 0.15), (int) (y + size * 0.45));
				break;
			case ECTO:
				ghost(g, x, y, size, new Color(0x9EFFD6));
				break;
			case LAVA:
				flame(g, x, y, size);
				break;
			case LEAVES:
				leaf(g, (int) cx, (int) cy, size * 0.5, 0.5, new Color(0xF08C22), true);
				break;
			case HALLOWEEN:
				pumpkin(g, cx, y + size, size * 0.42, true);
				break;
			case SNOW:
				g.setColor(new Color(0xC8ECFF));
				g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				flake(g, (int) cx, (int) cy, size / 2 - 1);
				break;
			case CHRISTMAS:
				holly(g, (int) cx, (int) cy, size / 16.0, null);
				break;
			default:
				break;
		}
		g.setTransform(at);
		g.setStroke(stroke);
		g.setComposite(was);
		return size + 4;
	}

	/**
	 * A trim along a card's top edge, under the card's text: snow lying on it (Winter, Christmas), a few leaves come to
	 * rest at its right end (Autumn), a molten rim with short drips running down (Infernal).
	 */
	static void cardTop(Graphics2D g, Kind k, int x, int y, int w)
	{
		if (w < 24)
		{
			return;
		}
		Composite was = g.getComposite();
		AffineTransform at = g.getTransform();
		switch (k)
		{
			case SNOW:
			case CHRISTMAS:
			{
				Path2D p = new Path2D.Double();
				p.moveTo(x + 5, y + 1);
				int step = 9;
				int i = 0;
				for (int xx = x + 5; xx + step <= x + w - 5; xx += step, i++)
				{
					p.quadTo(xx + step / 2.0, y + 4.5 + (i % 3 == 1 ? 1.5 : 0), xx + step, y + 1.5);
				}
				p.lineTo(x + w - 5, y);
				p.lineTo(x + 5, y);
				p.closePath();
				alpha(g, 0.9f);
				g.setColor(new Color(0xF4FAFF));
				g.fill(p);
				break;
			}
			case LEAVES:
			{
				long s = 7L * w + 31L * x + 3;
				for (int i = 0; i < 3; i++)
				{
					s = next(s);
					alpha(g, 0.92f);
					leaf(g, x + w - 12 - i * 9 - (int) ((s >>> 40) % 4), y + 3 + (int) ((s >>> 33) % 2), 4.5,
						1.2 + ((s >>> 20) % 120) / 100.0, AUTUMN_LEAF[(int) ((s >>> 45) % AUTUMN_LEAF.length)], i == 1);
				}
				break;
			}
			case LAVA:
			{
				alpha(g, 1f);
				g.setPaint(new GradientPaint(0, y, new Color(0xFF, 0xC0, 0x40, 230), 0, y + 3, new Color(0xD8, 0x3A, 0x00, 160)));
				g.fillRect(x + 4, y, w - 8, 2);
				long s = 5L * w + 17L * x + 11;
				for (int i = 0; i < Math.max(2, w / 70); i++)
				{
					s = next(s);
					int dx = x + 10 + (int) ((s >>> 33) % Math.max(1, w - 20));
					drip(g, dx, y + 1, 3 + (int) ((s >>> 40) % 4), 2.4);
				}
				break;
			}
			default:
				break;
		}
		g.setTransform(at);
		g.setComposite(was);
	}

	/** A lava pool along the card's bottom, heat haze over it and crusted rock floating on it (Infernal). */
	static void lavaPool(Graphics2D g, int w, int h)
	{
		Composite was = g.getComposite();
		int top = h - Math.min(22, Math.max(11, h / 7));
		Path2D pool = new Path2D.Double();
		pool.moveTo(0, h);
		pool.lineTo(0, top + 6);
		for (int x = 0; x <= w; x += 4)
		{
			pool.lineTo(x, top + 4 * Math.sin(x / 13.0) + 2 * Math.sin(x / 5.0 + 1));
		}
		pool.lineTo(w, h);
		pool.closePath();
		g.setPaint(new GradientPaint(0, top - 6, new Color(0xC8, 0x2A, 0x00), 0, h, new Color(0xFF, 0xB0, 0x20)));
		g.fill(pool);
		g.setPaint(new GradientPaint(0, top - 22, new Color(0xFF, 0x60, 0x00, 0), 0, top + 4, new Color(0xFF, 0x60, 0x00, 70)));
		g.fillRect(0, top - 22, w, 26);
		g.setColor(new Color(0x2A, 0x12, 0x0A, 210));
		g.fillOval(w / 5, top + 6, 22, 7);
		g.fillOval(w / 2 + 10, top + 10, 30, 8);
		g.fillOval(w - 40, top + 4, 18, 6);
		g.setComposite(was);
	}

	/** Dots of light (embers, motes) at fixed places. */
	static void motes(Graphics2D g, Color c, int x, int y, int w, int h)
	{
		Composite was = g.getComposite();
		long s = 7;
		g.setColor(c);
		for (int i = 0; i < 18; i++)
		{
			s = next(s);
			int r = 1 + (int) ((s >>> 50) % 2);
			alpha(g, 0.25f + ((s >>> 40) % 50) / 100f);
			g.fillOval(x + (int) (((s >>> 33) % 1000) * w / 1000), y + (int) (((s >>> 13) % 1000) * h / 1000), r * 2, r * 2);
		}
		g.setComposite(was);
	}

	// ---- the scenes ----

	private static void fogHero(Graphics2D g, int w, int h, List<Rectangle> keep)
	{
		// graves along the bottom right, then low mist over them
		int[][] graves = {{w - 22, 15, 18}, {w - 44, 12, 15}};
		g.setColor(new Color(0x2E2F33));
		for (int[] v : graves)
		{
			if (free(keep, v[0], h - v[2], v[1], v[2]))
			{
				g.fill(tombstone(v[0], h - v[2], v[1], v[2] + 2));
			}
		}
		if (free(keep, w - 62, h - 16, 10, 16))
		{
			g.fillRect(w - 58, h - 16, 3, 16);
			g.fillRect(w - 62, h - 12, 11, 3);
		}
		for (int i = 0; i < 3; i++)
		{
			alpha(g, 0.10f + i * 0.03f);
			g.setColor(new Color(0xE8EAEE));
			g.fill(new Ellipse2D.Double(-30 + i * w / 3.0, h - 10 - i % 2 * 4, w * 0.7, 16));
		}
	}

	private static void wisps(Graphics2D g, int w, int h, List<Rectangle> keep)
	{
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		long s = 41;
		for (int i = 0; i < 4; i++)
		{
			s = next(s);
			int x = (int) ((s >>> 33) % Math.max(1, w - 40)) + 20;
			int y = (int) ((s >>> 13) % Math.max(1, h - 24)) + 8;
			if (!free(keep, x - 12, y - 10, 30, 22))
			{
				continue;
			}
			alpha(g, 0.22f);
			g.setColor(new Color(0x9EFFD6));
			Path2D p = new Path2D.Double();
			p.moveTo(x - 10, y + 8);
			p.curveTo(x - 4, y - 6, x + 6, y + 10, x + 14, y - 8);
			g.draw(p);
		}
		if (free(keep, w - 30, h - 30, 20, 22))
		{
			alpha(g, 0.35f);
			ghost(g, w - 30, h - 30, 18, new Color(0xB8FFDF));
		}
	}

	private static void embers(Graphics2D g, int w, int h, List<Rectangle> keep, int n, float a)
	{
		long s = 53;
		g.setStroke(new BasicStroke(1f));
		for (int i = 0; i < n; i++)
		{
			s = next(s);
			int x = 6 + (int) ((s >>> 33) % Math.max(1, w - 12));
			int y = 4 + (int) ((s >>> 13) % Math.max(1, h - 8));
			if (!free(keep, x - 2, y - 6, 5, 9))
			{
				continue;
			}
			Color c = (s >>> 52) % 2 == 0 ? new Color(0xFFC060) : new Color(0xFF7A20);
			alpha(g, a * 0.45f);
			g.setColor(c);
			g.drawLine(x, y, x - 1, y + 5);
			alpha(g, a + ((s >>> 40) % 30) / 100f);
			g.fillOval(x - 1, y - 1, 3, 3);
		}
	}

	private static void leaves(Graphics2D g, int w, int h, List<Rectangle> keep, int n, Color[] tone, float a)
	{
		long s = 11;
		for (int i = 0; i < n; i++)
		{
			s = next(s);
			int x = 8 + (int) ((s >>> 33) % Math.max(1, w - 16));
			int y = 6 + (int) ((s >>> 13) % Math.max(1, h - 12));
			if (!free(keep, x - 7, y - 7, 14, 14))
			{
				continue;
			}
			alpha(g, a + ((s >>> 40) % 30) / 100f);
			leaf(g, x, y, 6.5, ((s >>> 20) % 628) / 100.0, tone[(int) ((s >>> 45) % tone.length)], (s >>> 52) % 3 == 0);
		}
	}

	private static void halloween(Graphics2D g, int w, int h, List<Rectangle> keep)
	{
		// a moon, a web in the corner, bats, and two lit jack-o'-lanterns
		int mr = 11;
		int mx = w - 52;
		int my = 15;
		if (free(keep, mx - mr - 3, my - mr - 3, mr * 2 + 6, mr * 2 + 6))
		{
			alpha(g, 0.25f);
			g.setColor(new Color(0xFFE8A0));
			g.fill(new Ellipse2D.Double(mx - mr - 5, my - mr - 5, mr * 2 + 10, mr * 2 + 10));
			alpha(g, 1f);
			g.setColor(new Color(0xFFF2C4));
			g.fill(new Ellipse2D.Double(mx - mr, my - mr, mr * 2, mr * 2));
			g.setColor(new Color(0xE8D69A));
			g.fillOval(mx - 5, my - 4, 4, 4);
			g.fillOval(mx + 2, my + 2, 5, 5);
		}
		if (free(keep, w - 30, 0, 30, 30))
		{
			alpha(g, 0.55f);
			web(g, w, 0, 28, new Color(0xE4DAF0));
		}
		long s = 67;
		for (int i = 0; i < 10; i++)
		{
			s = next(s);
			int x = 14 + (int) ((s >>> 33) % Math.max(1, w - 28));
			int y = 8 + (int) ((s >>> 13) % Math.max(1, h * 2 / 3));
			int size = 5 + (int) ((s >>> 50) % 4);
			if (!free(keep, x - size - 1, y - size, size * 2 + 2, size + 2))
			{
				continue;
			}
			alpha(g, 0.85f);
			bat(g, x, y, size, new Color(0x0E0812));
		}
		alpha(g, 1f);
		double[][] jacks = {{w - 18, 9}, {w - 37, 7.5}};
		for (double[] j : jacks)
		{
			int r = (int) j[1];
			if (free(keep, (int) j[0] - r - 2, h - r * 2 - 5, r * 2 + 4, r * 2 + 5))
			{
				pumpkin(g, j[0], h - 2, j[1], true);
			}
		}
	}

	private static void icicles(Graphics2D g, int w, List<Rectangle> keep)
	{
		long s = 83;
		for (int x = 6; x < w - 4; x += 7)
		{
			s = next(s);
			int len = 4 + (int) ((s >>> 40) % 8);
			if (!free(keep, x - 2, 0, 5, len + 1))
			{
				continue;
			}
			Path2D p = new Path2D.Double();
			p.moveTo(x - 2.5, 0);
			p.lineTo(x + 2.5, 0);
			p.lineTo(x, len);
			p.closePath();
			g.setPaint(new GradientPaint(x, 0, new Color(240, 250, 255, 230), x, len, new Color(190, 225, 255, 90)));
			g.fill(p);
		}
	}

	private static void flakes(Graphics2D g, int w, int h, List<Rectangle> keep, int n, float a, boolean big)
	{
		long s = 29;
		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (int i = 0; i < n; i++)
		{
			s = next(s);
			int x = 6 + (int) ((s >>> 33) % Math.max(1, w - 12));
			int y = 6 + (int) ((s >>> 13) % Math.max(1, h - 14));
			int r = big && (s >>> 52) % 3 == 0 ? 4 : 2;
			if (!free(keep, x - r - 1, y - r - 1, r * 2 + 2, r * 2 + 2))
			{
				continue;
			}
			alpha(g, a + ((s >>> 40) % 40) / 100f);
			g.setColor(Color.WHITE);
			if (r > 2 || (s >>> 51) % 2 == 0)
			{
				flake(g, x, y, r);
			}
			else
			{
				g.fillOval(x - 1, y - 1, 3, 3);
			}
		}
	}

	private static void sparkles(Graphics2D g, int w, int h, List<Rectangle> keep, int n)
	{
		long s = 97;
		for (int i = 0; i < n; i++)
		{
			s = next(s);
			int x = 10 + (int) ((s >>> 33) % Math.max(1, w - 20));
			int y = 14 + (int) ((s >>> 13) % Math.max(1, h - 28));
			if (!free(keep, x - 5, y - 5, 10, 10))
			{
				continue;
			}
			alpha(g, 0.85f);
			star(g, x, y, 4, new Color(0xFFE28A));
		}
	}

	/** Pine along the top with glowing bulbs, left out where something is kept clear. */
	private static void garland(Graphics2D g, int w, List<Rectangle> keep)
	{
		int n = Math.max(4, w / 24);
		double step = (w - 4) / (double) n;
		alpha(g, 1f);
		for (int i = 0; i < n; i++)
		{
			double x0 = 2 + step * i;
			double x1 = x0 + step;
			if (!free(keep, (int) x0, 0, (int) step, 19))
			{
				continue;
			}
			// needles along the sagging curve
			long s = 300 + i;
			for (int k = 0; k <= 12; k++)
			{
				double t = k / 12.0;
				double x = x0 + (x1 - x0) * t;
				double y = 1 + 6 * 4 * t * (1 - t);
				for (int m = 0; m < 3; m++)
				{
					s = next(s);
					double ang = ((s >>> 20) % 628) / 100.0;
					g.setColor((s >>> 45) % 2 == 0 ? new Color(0x1E5A2E) : new Color(0x2E7A3E));
					g.drawLine((int) x, (int) y, (int) (x + Math.cos(ang) * 3.5), (int) (y + Math.sin(ang) * 3.5));
				}
			}
			int bx = (int) (x0 + step / 2);
			if (i % 2 == 1)
			{
				// a bauble hanging from the low point on a short thread
				Color c = BAUBLE[(i / 2) % BAUBLE.length];
				g.setColor(new Color(0xD8C890));
				g.drawLine(bx, 7, bx, 10);
				g.setColor(new Color(0x9A9A9A));
				g.fillRect(bx - 1, 10, 3, 2);
				g.setColor(c);
				g.fill(new Ellipse2D.Double(bx - 3.5, 11.5, 7, 7));
				g.setColor(new Color(255, 255, 255, 190));
				g.fill(new Ellipse2D.Double(bx - 2, 13, 2.2, 2.2));
				continue;
			}
			// a bulb at the low point, with its glow
			int by = 8;
			Color c = BULB[i % BULB.length];
			alpha(g, 0.3f);
			g.setColor(c);
			g.fillOval(bx - 5, by - 3, 10, 11);
			alpha(g, 1f);
			g.setColor(new Color(0x6A6A6A));
			g.fillRect(bx - 1, by - 2, 3, 2);
			g.setColor(c);
			g.fillOval(bx - 2, by, 5, 6);
			g.setColor(new Color(255, 255, 255, 170));
			g.fillOval(bx - 1, by + 1, 2, 2);
		}
	}

	// ---- scene pieces (Christmas, Winter, Autumn, Infernal) ----

	/** A clip that leaves out every kept rectangle (grown by 2): big bright shapes (snow, leaf beds) go round text. */
	private static Shape keepOut(Graphics2D g, List<Rectangle> keep, int w, int h)
	{
		Shape old = g.getClip();
		if (keep.isEmpty())
		{
			return old;
		}
		Area a = new Area(old != null ? old : new Rectangle(0, 0, w, h));
		for (Rectangle r : keep)
		{
			a.subtract(new Area(new Rectangle(r.x - 2, r.y - 2, r.width + 4, r.height + 4)));
		}
		g.setClip(a);
		return old;
	}

	/** A faint, dark line of far-off pines or round trees along the bottom (behind text it only deepens the card). */
	private static void skyline(Graphics2D g, int w, int h, boolean pines, Color c)
	{
		alpha(g, 1f);
		g.setColor(c);
		long s = pines ? 211 : 223;
		double base = h - Math.max(9, h * 0.12);
		Path2D p = new Path2D.Double();
		p.moveTo(0, h);
		p.lineTo(0, base);
		double x = 0;
		while (x <= w)
		{
			s = next(s);
			double tw = pines ? 9 + (s >>> 40) % 8 : 16 + (s >>> 40) % 12;
			double th = pines ? 12 + (s >>> 33) % 18 : 6 + (s >>> 33) % 9;
			if (pines)
			{
				p.lineTo(x + tw / 2, base - th);
				p.lineTo(x + tw, base);
			}
			else
			{
				p.quadTo(x + tw / 2, base - th * 2, x + tw, base);
			}
			x += tw;
		}
		p.lineTo(w, base);
		p.lineTo(w, h);
		p.closePath();
		g.fill(p);
	}

	/** Snow lying along the bottom, going round anything kept clear. */
	private static void ground(Graphics2D g, int w, int h, List<Rectangle> keep, Color c, float a)
	{
		Shape old = keepOut(g, keep, w, h);
		alpha(g, a);
		bank(g, w, h, 8, c);
		g.setClip(old);
	}

	/** A wavy bank of snow along the bottom, {@code depth} deep. */
	private static void bank(Graphics2D g, int w, int h, int depth, Color c)
	{
		Path2D p = new Path2D.Double();
		p.moveTo(0, h);
		p.lineTo(0, h - depth * 0.6);
		int i = 0;
		for (int x = 0; x <= w; x += 16, i++)
		{
			p.quadTo(x + 8, h - depth - (i % 3 == 1 ? 3 : i % 3 == 2 ? 1 : 0), x + 16, h - depth * 0.6);
		}
		p.lineTo(w + 16, h);
		p.closePath();
		g.setColor(c);
		g.fill(p);
	}

	/** One or two pines at the bottom right, as big as the free space there allows; at Christmas the big one is dressed. */
	private static void forest(Graphics2D g, int w, int h, List<Rectangle> keep, boolean xmas)
	{
		Color fill = xmas ? new Color(0x1F6E36) : new Color(0x4E7398);
		Color snow = new Color(255, 255, 255, 235);
		int[] sizes = {48, 40, 32, 26, 20};
		for (int pass = 0; pass < 2; pass++)
		{
			double x = pass == 0 ? w - 20 : w - 44;
			for (int ph : sizes)
			{
				if (pass == 1 && ph > 34)
				{
					continue;
				}
				double half = ph * 0.38;
				if (free(keep, (int) (x - half), h - ph - 6, (int) (half * 2), ph + 6))
				{
					alpha(g, 1f);
					pine(g, x, h - 3, ph, pass == 0 ? fill : fill.darker(), snow, xmas && pass == 0, xmas && pass == 0, 900 + pass);
					break;
				}
			}
		}
	}

	/** A pine: three tiers on a trunk, {@code ph} tall, standing on {@code bottom}; snow on each tier, a star, baubles. */
	private static void pine(Graphics2D g, double x, double bottom, double ph, Color fill, Color snow, boolean topStar,
		boolean baubles, long seed)
	{
		double top = bottom - ph;
		g.setColor(new Color(0x3A2616));
		g.fill(new java.awt.geom.Rectangle2D.Double(x - ph * 0.05, bottom - ph * 0.14, ph * 0.1, ph * 0.14));
		for (int i = 0; i < 3; i++)
		{
			double ty = top + i * ph * 0.24;
			double by = ty + ph * 0.42;
			double half = ph * (0.18 + i * 0.09);
			Path2D t = new Path2D.Double();
			t.moveTo(x, ty);
			t.lineTo(x + half, by);
			t.quadTo(x, by - ph * 0.06, x - half, by);
			t.closePath();
			g.setColor(fill);
			g.fill(t);
			if (snow == null)
			{
				continue;
			}
			g.setColor(snow);
			Path2D cap = new Path2D.Double();
			cap.moveTo(x - half, by);
			cap.quadTo(x, by - ph * 0.06, x + half, by);
			cap.lineTo(x + half * 0.72, by - ph * 0.04);
			cap.quadTo(x, by - ph * 0.11, x - half * 0.72, by - ph * 0.04);
			cap.closePath();
			g.fill(cap);
			if (i == 0)
			{
				Path2D tip = new Path2D.Double();
				tip.moveTo(x, ty);
				tip.lineTo(x + half * 0.42, ty + ph * 0.17);
				tip.lineTo(x - half * 0.42, ty + ph * 0.17);
				tip.closePath();
				g.fill(tip);
			}
		}
		if (baubles)
		{
			long s = seed;
			double r = Math.max(1.6, ph * 0.045);
			for (int i = 0; i < 7; i++)
			{
				s = next(s);
				int tier = i % 3;
				double ty = top + tier * ph * 0.24 + ph * 0.31;
				double half = ph * (0.18 + tier * 0.09) * 0.7;
				double bx = x + (((s >>> 33) % 1000) / 1000.0 * 2 - 1) * half;
				g.setColor(BAUBLE[i % BAUBLE.length]);
				g.fill(new Ellipse2D.Double(bx - r, ty - r, r * 2, r * 2));
			}
		}
		if (topStar)
		{
			star(g, (int) Math.round(x), (int) Math.round(top - 1), (int) Math.max(3, ph * 0.11), new Color(0xFFD84A));
		}
	}

	/** Frost crystals growing in from an edge point: arms with side branches, {@code dir} 1 = rightwards, -1 = leftwards. */
	private static void frost(Graphics2D g, int cx, int cy, int dir, int len, List<Rectangle> keep, float a)
	{
		if (!free(keep, dir > 0 ? cx : cx - len, cy, len, len))
		{
			return;
		}
		g.setColor(new Color(0xDDF2FF));
		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		long s = 401L + cx * 7L + cy;
		for (int arm = 0; arm < 3; arm++)
		{
			s = next(s);
			double th = 0.2 + arm * 0.55;
			double l = len * (0.65 + ((s >>> 40) % 35) / 100.0);
			double ex = cx + dir * Math.cos(th) * l;
			double ey = cy + Math.sin(th) * l;
			alpha(g, a);
			g.draw(new java.awt.geom.Line2D.Double(cx, cy, ex, ey));
			for (double f : new double[]{0.35, 0.6, 0.82})
			{
				double bx = cx + (ex - cx) * f;
				double by = cy + (ey - cy) * f;
				double bl = l * 0.3 * (1.15 - f);
				for (int side = -1; side <= 1; side += 2)
				{
					double b = th + side * 0.75;
					alpha(g, a * 0.8f);
					g.draw(new java.awt.geom.Line2D.Double(bx, by, bx + dir * Math.cos(b) * bl, by + Math.sin(b) * bl));
				}
			}
		}
	}

	/** A branch reaching in from an edge point (dir 1 = rightwards), leaves hanging from it. */
	private static void branch(Graphics2D g, int ex, int ey, int dir, int len, List<Rectangle> keep, float a, float leafAlpha,
		Color[] tone, long seed)
	{
		if (!free(keep, dir > 0 ? ex : ex - len, ey - 4, len, 24))
		{
			return;
		}
		double mx = ex + dir * len * 0.5;
		double my = ey + 10;
		double tx = ex + dir * len;
		double ty = ey + 5;
		Path2D p = new Path2D.Double();
		p.moveTo(ex, ey);
		p.quadTo(mx, my, tx, ty);
		alpha(g, a);
		g.setColor(new Color(0x5A3A22));
		g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(p);
		long s = seed;
		for (int i = 0; i < 8; i++)
		{
			s = next(s);
			double t = 0.18 + i * 0.105;
			double bx = (1 - t) * (1 - t) * ex + 2 * (1 - t) * t * mx + t * t * tx;
			double by = (1 - t) * (1 - t) * ey + 2 * (1 - t) * t * my + t * t * ty;
			alpha(g, leafAlpha);
			leaf(g, (int) bx, (int) (by + 4 + (s >>> 40) % 4), 4.5 + (s >>> 50) % 2,
				(i % 2 == 0 ? 0.5 : -0.5) + ((s >>> 20) % 100) / 100.0, tone[(int) ((s >>> 45) % tone.length)], (s >>> 52) % 2 == 0);
		}
	}

	/** A mound of fallen leaves {@code width} wide standing on {@code bottom}, left out where something is kept clear. */
	private static void leafPile(Graphics2D g, int x, int bottom, int width, List<Rectangle> keep, long seed)
	{
		int ph = Math.max(8, width / 3);
		if (!free(keep, x, bottom - ph, width, ph))
		{
			return;
		}
		long s = seed;
		for (int i = 0; i < width * 3 / 4; i++)
		{
			s = next(s);
			double fx = ((s >>> 33) % 1000) / 1000.0;
			double hgt = ph * (1 - Math.pow(2 * fx - 1, 2));
			alpha(g, 0.95f);
			leaf(g, x + (int) (fx * width), bottom - 2 - (int) (((s >>> 13) % 1000) / 1000.0 * hgt), 4 + (s >>> 50) % 2,
				((s >>> 20) % 628) / 100.0, AUTUMN_LEAF[(int) ((s >>> 45) % AUTUMN_LEAF.length)], (s >>> 52) % 3 == 0);
		}
	}

	/** Small fallen leaves all along the bottom edge, going round anything kept clear. */
	private static void leafGround(Graphics2D g, int w, int h, List<Rectangle> keep)
	{
		Shape old = keepOut(g, keep, w, h);
		long s = 613;
		for (int x = 2; x < w; x += 5)
		{
			s = next(s);
			alpha(g, 0.9f);
			leaf(g, x + (int) ((s >>> 40) % 4), h - 2 - (int) ((s >>> 33) % 4), 3.5, ((s >>> 20) % 628) / 100.0,
				AUTUMN_LEAF[(int) ((s >>> 45) % AUTUMN_LEAF.length)], false);
		}
		g.setClip(old);
	}

	/** A tree in autumn colours (the background's bottom corner): trunk, two boughs, a canopy of dark, warm blobs. */
	private static void autumnTree(Graphics2D g, int x, int bottom, int ht)
	{
		alpha(g, 1f);
		g.setColor(new Color(0x3A2416));
		Path2D trunk = new Path2D.Double();
		trunk.moveTo(x - 5, bottom);
		trunk.lineTo(x - 2, bottom - ht * 0.58);
		trunk.lineTo(x + 2, bottom - ht * 0.58);
		trunk.lineTo(x + 6, bottom);
		trunk.closePath();
		g.fill(trunk);
		g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawLine(x, (int) (bottom - ht * 0.42), x + 16, (int) (bottom - ht * 0.62));
		g.drawLine(x, (int) (bottom - ht * 0.48), x - 12, (int) (bottom - ht * 0.66));
		Color[] canopy = {new Color(0x5E200E), new Color(0x7E3414), new Color(0x8E3A18), new Color(0x7C5A1C)};
		long s = 811;
		for (int i = 0; i < 16; i++)
		{
			s = next(s);
			double cx = x + (((s >>> 33) % 1000) / 1000.0 * 2 - 1) * ht * 0.34;
			double cy = bottom - ht * 0.74 + (((s >>> 13) % 1000) / 1000.0 * 2 - 1) * ht * 0.2;
			double r = ht * (0.1 + ((s >>> 40) % 6) / 100.0);
			alpha(g, 0.48f);
			g.setColor(canopy[i % canopy.length]);
			g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
		}
	}

	/** A molten rim along a card's top edge with drips hanging from it, each as long as the free space below allows. */
	private static void lavaRim(Graphics2D g, int w, List<Rectangle> keep, int y, int maxLen)
	{
		alpha(g, 1f);
		g.setPaint(new GradientPaint(0, y, new Color(0xFF, 0xC8, 0x50), 0, y + 4, new Color(0xD0, 0x3A, 0x00, 200)));
		g.fillRect(0, y, w, 3);
		long s = 59;
		for (int x = 14; x < w - 8; x += 19)
		{
			s = next(s);
			int dx = x + (int) ((s >>> 40) % 8);
			int len = 6 + (int) ((s >>> 33) % Math.max(1, maxLen - 5));
			while (len >= 5 && !free(keep, dx - 2, y, 5, len + 3))
			{
				len -= 3;
			}
			if (len >= 5)
			{
				drip(g, dx, y + 2, len, 3.2);
			}
		}
	}

	/** A drip of lava hanging from x, y: a thin run that swells into a round drop at its end. */
	private static void drip(Graphics2D g, double x, double y, double len, double wd)
	{
		Path2D p = new Path2D.Double();
		p.moveTo(x - wd / 2, y);
		p.lineTo(x + wd / 2, y);
		p.quadTo(x + wd * 0.3, y + len * 0.7, x + wd * 0.75, y + len - wd * 0.6);
		p.quadTo(x, y + len + wd * 0.9, x - wd * 0.75, y + len - wd * 0.6);
		p.quadTo(x - wd * 0.3, y + len * 0.7, x - wd / 2, y);
		p.closePath();
		g.setPaint(new GradientPaint(0, (float) y, new Color(0xFF, 0xC0, 0x40), 0, (float) (y + len + wd), new Color(0xE0, 0x40, 0x00)));
		g.fill(p);
		g.setColor(new Color(255, 240, 200, 170));
		g.fill(new Ellipse2D.Double(x - wd * 0.3, y + len - wd * 0.75, wd * 0.4, wd * 0.4));
	}

	// ---- the shapes ----

	private static void leaf(Graphics2D g, int x, int y, double r, double angle, Color c, boolean maple)
	{
		AffineTransform at = g.getTransform();
		g.translate(x, y);
		g.rotate(angle);
		Path2D p = new Path2D.Double();
		if (maple)
		{
			int pts = 10;
			for (int i = 0; i < pts; i++)
			{
				double a = -Math.PI / 2 + i * Math.PI * 2 / pts;
				double rr = i % 2 == 0 ? r * (i == 0 ? 1.15 : 1.0) : r * 0.48;
				if (i == 0)
				{
					p.moveTo(Math.cos(a) * rr, Math.sin(a) * rr);
				}
				else
				{
					p.lineTo(Math.cos(a) * rr, Math.sin(a) * rr);
				}
			}
			p.closePath();
		}
		else
		{
			p.moveTo(0, -r);
			p.quadTo(r * 0.8, -r * 0.2, 0, r);
			p.quadTo(-r * 0.8, -r * 0.2, 0, -r);
		}
		g.setColor(c);
		g.fill(p);
		g.setColor(new Color(0, 0, 0, 80));
		g.drawLine(0, (int) (-r * 0.7), 0, (int) (r * 1.25));
		g.setTransform(at);
	}

	private static void flake(Graphics2D g, int x, int y, int r)
	{
		for (int a = 0; a < 3; a++)
		{
			double ang = a * Math.PI / 3 + Math.PI / 2;
			int dx = (int) Math.round(Math.cos(ang) * r);
			int dy = (int) Math.round(Math.sin(ang) * r);
			g.drawLine(x - dx, y - dy, x + dx, y + dy);
		}
		if (r >= 4)
		{
			for (int a = 0; a < 6; a++)
			{
				double ang = a * Math.PI / 3 + Math.PI / 2;
				double bx = x + Math.cos(ang) * r * 0.6;
				double by = y + Math.sin(ang) * r * 0.6;
				for (int side = -1; side <= 1; side += 2)
				{
					double b = ang + side * 0.8;
					g.drawLine((int) Math.round(bx), (int) Math.round(by), (int) Math.round(bx + Math.cos(b) * r * 0.35),
						(int) Math.round(by + Math.sin(b) * r * 0.35));
				}
			}
		}
	}

	private static void star(Graphics2D g, int x, int y, int r, Color c)
	{
		Path2D p = new Path2D.Double();
		for (int i = 0; i < 8; i++)
		{
			double a = i * Math.PI / 4;
			double rr = i % 2 == 0 ? r : r * 0.3;
			if (i == 0)
			{
				p.moveTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
			}
			else
			{
				p.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
			}
		}
		p.closePath();
		g.setColor(c);
		g.fill(p);
	}

	/** A bat with its wings spread, {@code s} wide each side, centred on x, y. */
	private static void bat(Graphics2D g, int x, int y, int s, Color c)
	{
		Path2D p = new Path2D.Double();
		p.moveTo(x, y - s * 0.15);
		for (int side = -1; side <= 1; side += 2)
		{
			if (side == 1)
			{
				p.moveTo(x, y - s * 0.15);
			}
			p.quadTo(x + side * s * 0.45, y - s * 0.75, x + side * s, y - s * 0.35);
			p.quadTo(x + side * s * 0.85, y + s * 0.05, x + side * s * 0.68, y + s * 0.1);
			p.quadTo(x + side * s * 0.6, y - s * 0.05, x + side * s * 0.45, y + s * 0.2);
			p.quadTo(x + side * s * 0.35, y + s * 0.02, x + side * s * 0.18, y + s * 0.25);
			p.lineTo(x, y + s * 0.15);
			p.closePath();
		}
		g.setColor(c);
		g.fill(p);
		g.fill(new Ellipse2D.Double(x - s * 0.18, y - s * 0.38, s * 0.36, s * 0.62));
	}

	/** A pumpkin sitting on {@code bottom}; lit = a carved, glowing face. */
	private static void pumpkin(Graphics2D g, double cx, double bottom, double r, boolean lit)
	{
		double top = bottom - r * 1.7;
		if (lit)
		{
			Composite was = g.getComposite();
			alpha(g, 0.28f);
			g.setColor(new Color(0xFF9A2E));
			g.fill(new Ellipse2D.Double(cx - r * 1.6, top - r * 0.4, r * 3.2, r * 2.6));
			g.setComposite(was);
		}
		g.setColor(new Color(0xE8761C));
		g.fill(new Ellipse2D.Double(cx - r * 1.15, top, r * 2.3, r * 1.7));
		g.setColor(new Color(0xB8520E));
		g.draw(new Ellipse2D.Double(cx - r * 0.45, top, r * 0.9, r * 1.7));
		g.setColor(new Color(0x4E6E22));
		g.fill(new java.awt.geom.Rectangle2D.Double(cx - r * 0.12, top - r * 0.35, r * 0.24, r * 0.45));
		if (lit && r >= 4)
		{
			g.setColor(new Color(0xFFE47A));
			double ey = top + r * 0.5;
			Path2D eyes = new Path2D.Double();
			for (int side = -1; side <= 1; side += 2)
			{
				eyes.moveTo(cx + side * r * 0.5, ey - r * 0.22);
				eyes.lineTo(cx + side * r * 0.75, ey + r * 0.2);
				eyes.lineTo(cx + side * r * 0.25, ey + r * 0.2);
				eyes.closePath();
			}
			g.fill(eyes);
			Path2D mouth = new Path2D.Double();
			double my = top + r * 1.12;
			mouth.moveTo(cx - r * 0.7, my - r * 0.12);
			mouth.lineTo(cx - r * 0.35, my + r * 0.05);
			mouth.lineTo(cx - r * 0.15, my - r * 0.1);
			mouth.lineTo(cx + r * 0.15, my + r * 0.05);
			mouth.lineTo(cx + r * 0.35, my - r * 0.1);
			mouth.lineTo(cx + r * 0.7, my - r * 0.12);
			mouth.quadTo(cx, my + r * 0.45, cx - r * 0.7, my - r * 0.12);
			g.fill(mouth);
		}
	}

	/** A spider web in the corner at (cornerX, cornerY): the right edge's top when cornerX is the width. */
	private static void web(Graphics2D g, int cornerX, int cornerY, int r, Color c)
	{
		g.setColor(c);
		g.setStroke(new BasicStroke(0.8f));
		int spokes = 5;
		double[][] tips = new double[spokes][2];
		for (int i = 0; i < spokes; i++)
		{
			double a = Math.PI / 2 + i * (Math.PI / 2) / (spokes - 1);
			tips[i][0] = Math.cos(a);
			tips[i][1] = Math.sin(a);
			g.drawLine(cornerX, cornerY, (int) (cornerX + tips[i][0] * r), (int) (cornerY + tips[i][1] * r));
		}
		for (int ring = 1; ring <= 3; ring++)
		{
			double rr = r * ring / 3.6;
			Path2D p = new Path2D.Double();
			for (int i = 0; i < spokes; i++)
			{
				double px = cornerX + tips[i][0] * rr;
				double py = cornerY + tips[i][1] * rr;
				if (i == 0)
				{
					p.moveTo(px, py);
				}
				else
				{
					double mx = cornerX + (tips[i][0] + tips[i - 1][0]) / 2 * rr * 0.86;
					double my = cornerY + (tips[i][1] + tips[i - 1][1]) / 2 * rr * 0.86;
					p.quadTo(mx, my, px, py);
				}
			}
			g.draw(p);
		}
	}

	/** A holly sprig: two spiky leaves and three red berries, centred on x, y. */
	private static void holly(Graphics2D g, int x, int y, double scale, List<Rectangle> keep)
	{
		int box = (int) (20 * scale);
		if (keep != null && !free(keep, x - box / 2 - 2, y - box / 2, box + 6, box))
		{
			return;
		}
		for (int side = -1; side <= 1; side += 2)
		{
			AffineTransform at = g.getTransform();
			g.translate(x, y);
			g.rotate(side * 0.7 + (side == 1 ? Math.PI : 0));
			Path2D leaf = new Path2D.Double();
			double L = 9 * scale;
			double W = 3.6 * scale;
			leaf.moveTo(0, 0);
			int bumps = 3;
			for (int i = 1; i <= bumps; i++)
			{
				double t = i / (double) bumps;
				leaf.quadTo(L * (t - 0.17), -W * 0.4, L * t, -W * (i == bumps ? 0 : 1));
			}
			for (int i = bumps - 1; i >= 0; i--)
			{
				double t = i / (double) bumps;
				leaf.quadTo(L * (t + 0.17), W * 0.4, L * t, i == 0 ? 0 : W);
			}
			leaf.closePath();
			g.setColor(new Color(0x1E7A34));
			g.fill(leaf);
			g.setColor(new Color(0x0E4A1E));
			g.draw(new java.awt.geom.Line2D.Double(0, 0, L * 0.9, 0));
			g.setTransform(at);
		}
		double br = 2.4 * scale;
		g.setColor(new Color(0xD8262A));
		g.fill(new Ellipse2D.Double(x - br * 1.9, y - br * 1.2, br * 2, br * 2));
		g.fill(new Ellipse2D.Double(x - br * 0.2, y - br * 1.6, br * 2, br * 2));
		g.fill(new Ellipse2D.Double(x - br, y, br * 2, br * 2));
		g.setColor(new Color(255, 255, 255, 150));
		g.fill(new Ellipse2D.Double(x - br * 1.4, y - br * 0.9, br * 0.6, br * 0.6));
	}

	private static java.awt.Shape tombstone(double x, double y, double w, double h)
	{
		Path2D p = new Path2D.Double();
		p.moveTo(x, y + h);
		p.lineTo(x, y + w / 2);
		p.quadTo(x, y, x + w / 2, y);
		p.quadTo(x + w, y, x + w, y + w / 2);
		p.lineTo(x + w, y + h);
		p.closePath();
		return p;
	}

	private static void ghost(Graphics2D g, int x, int y, int size, Color c)
	{
		Path2D p = new Path2D.Double();
		double w = size * 0.7;
		double left = x + (size - w) / 2;
		p.moveTo(left, y + size);
		p.lineTo(left, y + w / 2);
		p.quadTo(left, y + size * 0.05, left + w / 2, y + size * 0.05);
		p.quadTo(left + w, y + size * 0.05, left + w, y + w / 2);
		p.lineTo(left + w, y + size);
		for (int i = 3; i > 0; i--)
		{
			double x1 = left + w * (i - 0.5) / 3;
			double x2 = left + w * (i - 1) / 3;
			p.quadTo(x1, y + size * 0.82, x2, y + size);
		}
		p.closePath();
		g.setColor(c);
		g.fill(p);
		g.setColor(new Color(0x10, 0x20, 0x20, 200));
		g.fillOval((int) (left + w * 0.25), (int) (y + size * 0.35), 2, 3);
		g.fillOval((int) (left + w * 0.6), (int) (y + size * 0.35), 2, 3);
	}

	private static void flame(Graphics2D g, int x, int y, int size)
	{
		Path2D p = new Path2D.Double();
		double cx = x + size / 2.0;
		p.moveTo(cx, y);
		p.curveTo(cx + size * 0.55, y + size * 0.45, cx + size * 0.45, y + size, cx, y + size);
		p.curveTo(cx - size * 0.45, y + size, cx - size * 0.55, y + size * 0.45, cx, y);
		g.setPaint(new GradientPaint(0, y, new Color(0xFFD04A), 0, y + size, new Color(0xFF4A10)));
		g.fill(p);
	}

	private static void crack(Graphics2D g, int x, int y, long s)
	{
		g.setColor(new Color(0xFF6A1A));
		g.setStroke(new BasicStroke(1f));
		int px = x;
		int py = y;
		for (int i = 0; i < 4; i++)
		{
			s = next(s);
			int nx = px + (int) ((s >>> 33) % 13) - 6;
			int ny = py + 4 + (int) ((s >>> 13) % 7);
			g.drawLine(px, py, nx, ny);
			px = nx;
			py = ny;
		}
	}

	// ---- helpers ----

	private static long next(long s)
	{
		return s * 6364136223846793005L + 1442695040888963407L;
	}

	private static void alpha(Graphics2D g, float a)
	{
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, a))));
	}

	/** Nothing kept clear overlaps this box (grown by 2 pixels). */
	private static boolean free(List<Rectangle> keep, int x, int y, int w, int h)
	{
		for (Rectangle r : keep)
		{
			if (r.intersects(x - 2, y - 2, w + 4, h + 4))
			{
				return false;
			}
		}
		return true;
	}
}
