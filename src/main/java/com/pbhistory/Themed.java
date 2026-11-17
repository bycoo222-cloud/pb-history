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
import java.awt.Component;
import java.awt.Container;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * A Swing panel in the chosen theme ({@link Looks}, {@link Decor}): the top card with its scene, headings with the
 * theme's icon and line, and the background's faint layer. The same file in PB History and Screenshot Gallery (only
 * the package differs). Plain draws exactly what the panel drew before themes.
 */
final class Themed
{
	private Themed()
	{
	}

	/** {@code c} moved {@code f} of the way to {@code to}. */
	static Color mix(Color c, Color to, double f)
	{
		return new Color((int) (c.getRed() + (to.getRed() - c.getRed()) * f), (int) (c.getGreen() + (to.getGreen() - c.getGreen()) * f),
			(int) (c.getBlue() + (to.getBlue() - c.getBlue()) * f));
	}

	/** {@code c} with alpha {@code a} (0-255). */
	static Color alpha(Color c, int a)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	/** Small text on the top card: the theme's text a little toward the card (the card's gradient is brighter than dim text). */
	static Color onHero(Looks t)
	{
		return t == null || t.id == Looks.Id.PLAIN ? t == null ? Color.LIGHT_GRAY : t.dim : mix(t.text, t.heroTop, 0.12);
	}

	/** The panel's background scene (pines and snow, autumn trees and leaves, bats, embers...), behind every card. */
	static void background(Graphics g0, Looks t, int w, int h)
	{
		if (t == null || t.decor == Decor.Kind.NONE)
		{
			return;
		}
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Decor.background(g, t.decor, w, h);
		g.dispose();
	}

	/** The top card: the theme's gradient, its scene kept clear of the card's labels and pictures; Plain = flat. */
	static final class Card extends JPanel
	{
		private final Supplier<Looks> look;

		Card(LayoutManager layout, Supplier<Looks> look)
		{
			super(layout);
			this.look = look;
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Looks t = look.get();
			Graphics2D g = (Graphics2D) g0.create();
			try
			{
				int w = getWidth();
				int h = getHeight();
				if (t == null || t.id == Looks.Id.PLAIN)
				{
					g.setColor(getBackground());
					g.fillRect(0, 0, w, h);
					return;
				}
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				boolean square = t.shape == Looks.Shape.BEVEL || t.shape == Looks.Shape.FLAT;
				Shape shape = square ? new Rectangle(0, 0, w, h) : new RoundRectangle2D.Double(0, 0, w - 1, h - 1, t.radius + 4, t.radius + 4);
				g.setPaint(new GradientPaint(0, 0, t.heroTop, w, h, t.heroBottom));
				g.fill(shape);
				Shape clip = g.getClip();
				g.clip(shape);
				if (t.lava)
				{
					Decor.lavaPool(g, w, h);
				}
				if (t.motes != null)
				{
					Decor.motes(g, t.motes, 6, 6, w - 12, h - 12);
				}
				Decor.hero(g, t.decor, w, h, keepClear(this));
				g.setClip(clip);
				if (t.shape == Looks.Shape.BEVEL)
				{
					g.setColor(t.bevelDark);
					g.drawRect(0, 0, w - 1, h - 1);
					g.setColor(t.bevelLight);
					g.drawLine(1, 1, w - 2, 1);
					g.drawLine(1, 1, 1, h - 2);
				}
				else if (t.edge != null)
				{
					g.setColor(t.edge);
					g.draw(shape);
				}
			}
			finally
			{
				g.dispose();
			}
		}
	}

	/** A card in the theme's shape and colour (rounded, the game's bevel, flat); Plain = a flat fill as before. */
	static final class Tile extends JPanel
	{
		private final Supplier<Looks> look;

		Tile(LayoutManager layout, Supplier<Looks> look)
		{
			super(layout);
			this.look = look;
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Looks t = look.get();
			Graphics2D g = (Graphics2D) g0.create();
			try
			{
				int w = getWidth();
				int h = getHeight();
				g.setColor(getBackground());
				if (t == null || t.id == Looks.Id.PLAIN || t.shape == Looks.Shape.FLAT)
				{
					g.fillRect(0, 0, w, h);
					return;
				}
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				if (t.shape == Looks.Shape.BEVEL)
				{
					g.fillRect(0, 0, w, h);
					g.setColor(t.bevelDark);
					g.drawRect(0, 0, w - 1, h - 1);
					g.setColor(t.bevelLight);
					g.drawLine(1, 1, w - 2, 1);
					g.drawLine(1, 1, 1, h - 2);
					return;
				}
				RoundRectangle2D r = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, t.radius, t.radius);
				g.setColor(t.see(getBackground()));      // see-through over the background's scene in the seasonal themes
				if (t.shape == Looks.Shape.STONE)
				{
					g.setPaint(new GradientPaint(0, 0, getBackground().brighter(), 0, h, getBackground()));
				}
				g.fill(r);
				if (t.lava)
				{
					g.setPaint(new GradientPaint(0, h - 8, new Color(0xFF, 0x50, 0x00, 0), 0, h, new Color(0xFF, 0x6A, 0x10, 110)));
					g.fill(new RoundRectangle2D.Double(1, h - 9, w - 3, 8, t.radius, t.radius));
				}
				if (h >= 30)
				{
					Decor.cardTop(g, t.decor, 0, 0, w);
				}
				if (t.edge != null)
				{
					g.setColor(t.edge);
					g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 2, h - 2, t.radius, t.radius));
				}
			}
			finally
			{
				g.dispose();
			}
		}
	}

	/** A heading: its title (west), an optional part on the right (east), the theme's icon before it, a line under it. */
	static final class Heading extends JPanel
	{
		private final Supplier<Looks> look;
		private final Color plainLine;
		private final boolean icon;

		Heading(Supplier<Looks> look, JComponent west, JComponent east, Color plainLine, int top, int bottom)
		{
			super(new BorderLayout());
			this.look = look;
			this.plainLine = plainLine;
			Looks t = look.get();
			boolean themed = t != null && t.id != Looks.Id.PLAIN;
			this.icon = themed && t.decor != Decor.Kind.NONE;
			setOpaque(false);
			add(west, BorderLayout.WEST);
			if (east != null)
			{
				add(east, BorderLayout.EAST);
			}
			boolean line = plainLine != null || themed;
			setBorder(new EmptyBorder(top, icon ? 17 : 0, bottom + (line ? 1 : 0), 0));
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			super.paintComponent(g0);
			Looks t = look.get();
			Graphics2D g = (Graphics2D) g0.create();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int lineY = getHeight() - getInsets().bottom;
				int top = getInsets().top;
				if (t == null || t.id == Looks.Id.PLAIN)
				{
					if (plainLine != null)
					{
						g.setColor(plainLine);
						g.fillRect(0, lineY, getWidth(), 1);
					}
					return;
				}
				g.setColor(alpha(t.highlight, 150));
				g.fillRect(0, lineY, getWidth(), 1);
				if (icon)
				{
					int size = 13;
					Decor.motif(g, t.decor, 0, top + Math.max(0, (lineY - top - size) / 2), size);
				}
			}
			finally
			{
				g.dispose();
			}
		}
	}

	/** All the leaf components inside {@code root} (labels, icons, pictures), in its coordinates: drawn around. */
	static List<Rectangle> keepClear(JComponent root)
	{
		List<Rectangle> out = new ArrayList<>();
		collect(root, root, out);
		return out;
	}

	private static void collect(Container c, JComponent root, List<Rectangle> out)
	{
		for (Component k : c.getComponents())
		{
			if (!k.isVisible())
			{
				continue;
			}
			if (k.getClass() == JPanel.class || k instanceof Heading)
			{
				collect((Container) k, root, out);         // a holder: what's inside it counts, not its empty space
			}
			else
			{
				out.add(SwingUtilities.convertRectangle(c, k.getBounds(), root));
			}
		}
	}
}
