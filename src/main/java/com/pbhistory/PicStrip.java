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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** A PB's picture as a strip (its middle, filling the box); a click opens it as big as the screen allows. */
final class PicStrip extends JComponent
{
	private final PbPanel.Backend backend;
	private final long at;
	private BufferedImage img;
	private boolean failed;

	PicStrip(PbPanel.Backend backend, long at, int w, int h, String tip)
	{
		this.backend = backend;
		this.at = at;
		setPreferredSize(new Dimension(w, h));
		setMaximumSize(new Dimension(w, h));
		setToolTipText(tip + " - click to see it big");
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		backend.picture(at, 480, read ->
		{
			img = read;
			failed = read == null;
			repaint();
		});
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					showBig();
				}
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		int w = getWidth(), h = getHeight();
		g.clip(new RoundRectangle2D.Float(0, 0, w, h, 6, 6));
		if (img == null)
		{
			g.setColor(ColorScheme.DARK_GRAY_COLOR);
			g.fillRect(0, 0, w, h);
			g.setFont(FontManager.getRunescapeSmallFont());
			g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			String say = failed ? "Can't open this picture" : "Loading picture...";
			g.drawString(say, (w - g.getFontMetrics().stringWidth(say)) / 2, h / 2 + 4);
		}
		else
		{
			double box = w / (double) h, src = img.getWidth() / (double) img.getHeight();
			int sw = img.getWidth(), sh = img.getHeight(), sx = 0, sy = 0;
			if (src > box)
			{
				sw = (int) Math.round(sh * box);
				sx = (img.getWidth() - sw) / 2;
			}
			else
			{
				sh = (int) Math.round(sw / box);
				sy = (img.getHeight() - sh) / 2;
			}
			g.drawImage(img, 0, 0, w, h, sx, sy, sx + sw, sy + sh, null);
		}
		g.dispose();
	}

	private void showBig()
	{
		backend.picture(at, 0, full ->
		{
			if (full == null)
			{
				JOptionPane.showMessageDialog(this, "That picture can't be opened.", "PB History", JOptionPane.WARNING_MESSAGE);
				return;
			}
			JFrame f = new JFrame("PB History");
			f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			JComponent c = new JComponent()
			{
				@Override
				protected void paintComponent(Graphics g0)
				{
					Graphics2D g = (Graphics2D) g0;
					g.setColor(Color.BLACK);
					g.fillRect(0, 0, getWidth(), getHeight());
					double k = Math.min(1.0, Math.min(getWidth() / (double) full.getWidth(), getHeight() / (double) full.getHeight()));
					int w = (int) Math.round(full.getWidth() * k), h = (int) Math.round(full.getHeight() * k);
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g.drawImage(full, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
				}
			};
			Rectangle screen = getGraphicsConfiguration() != null ? getGraphicsConfiguration().getBounds() : new Rectangle(0, 0, 1280, 800);
			double k = Math.min(1.0, Math.min(screen.width * 0.75 / full.getWidth(), screen.height * 0.75 / full.getHeight()));
			c.setPreferredSize(new Dimension((int) (full.getWidth() * k), (int) (full.getHeight() * k)));
			f.setContentPane(c);
			f.getRootPane().registerKeyboardAction(e -> f.dispose(),
				KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
			f.pack();
			f.setLocationRelativeTo(this);
			f.setVisible(true);
		});
	}
}
