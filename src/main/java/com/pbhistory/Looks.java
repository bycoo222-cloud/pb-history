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
import net.runelite.client.ui.ColorScheme;

/**
 * ⭐ The themes shared by Death Log, Flip Sidebar, PB History and Screenshot Gallery (user, 2026-11-08: "want to see all
 * these looks as themes that all of them have"). ONE palette per theme, in the same file in each plugin (only the
 * package line differs), so a theme looks the same everywhere; each plugin maps it onto its own drawing. Change a
 * colour here, then copy the file to the other three.
 */
final class Looks
{
	/** Every shared theme, in the order the settings list them. */
	enum Id
	{
		PLAIN, DASHBOARD, GRAVESTONE, OLD_SCHOOL, ECTOPLASM, INFERNAL, AUTUMN, HALLOWEEN, WINTER, CHRISTMAS
	}

	/** How cards are shaped. */
	enum Shape
	{
		ROUND, STONE, BEVEL, FLAT
	}

	final Id id;
	Shape shape = Shape.ROUND;
	int radius = 10;

	/** Behind everything. */
	Color background;
	/** Cards. */
	Color card;
	/** A thin outline round each card; null = none. */
	Color edge;
	/** A card or row under the mouse. */
	Color hover;
	/** Separators and thin rules. */
	Color line;
	Color text = Color.WHITE;
	/** Small and secondary text. */
	Color dim;
	/** Headings, links, the plugin's own titles: never red or green (Flip Sidebar's profit and loss keep those). */
	Color highlight;
	/** The theme's own strong colour (Death Log's deaths). */
	Color accent;
	/** Good news: a run, a best. */
	Color good;
	/** Titles drawn in the theme's title colour. */
	Color title = Color.WHITE;
	/** Big numbers. */
	Color number;
	/** Tags, buttons, bar tracks. */
	Color chip;
	/** Bars. */
	Color bar;
	/** The top card's gradient. */
	Color heroTop;
	Color heroBottom;
	/** A soft glow on big numbers; null = none. */
	Color glow;
	/** Dots of light in the top card; null = none. */
	Color motes;
	/** Three small coloured stripes (counter tiles); null = none. */
	Color[] stripes;
	/** The game's bevelled stone (Old School). */
	Color bevelLight;
	Color bevelDark;
	/** Faint stone blocks behind everything (Old School); null = none. */
	Color blocks;
	/** Text with the game's black shadow. */
	boolean shadow;
	/** Molten rock: the top card is basalt over a lava pool, cards glow at their lower edge. */
	boolean lava;
	boolean upperCaseHeaders;
	/** The seasonal or themed decoration (Decor). */
	Decor.Kind decor = Decor.Kind.NONE;
	/**
	 * How solid cards are, 0-255. Under 255 = see-through cards over the background's scene (user, 2026-11-08, on
	 * Halloween: "the background with the transparent boxes over it is beautiful"); the scene behind is kept dark and
	 * faint, so text on a card stays as readable as on a solid one.
	 */
	int cardAlpha = 255;

	private Looks(Id id)
	{
		this.id = id;
	}

	/** The theme's settings name. */
	static String label(Id id)
	{
		switch (id)
		{
			case PLAIN:
				return "Plain";
			case DASHBOARD:
				return "Dashboard";
			case GRAVESTONE:
				return "Gravestone";
			case OLD_SCHOOL:
				return "Old School";
			case ECTOPLASM:
				return "Ectoplasm";
			case INFERNAL:
				return "Infernal";
			case AUTUMN:
				return "Autumn";
			case HALLOWEEN:
				return "Halloween";
			case WINTER:
				return "Winter";
			default:
				return "Christmas";
		}
	}

	private static Color c(int rgb)
	{
		return new Color(rgb);
	}

	/** A card's fill in this theme: see-through over the scene where the theme has one ({@link #cardAlpha}). */
	Color see(Color fill)
	{
		return fill == null || cardAlpha >= 255 || fill.getAlpha() < 255 ? fill
			: new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), cardAlpha);
	}

	static Looks of(Id id)
	{
		Looks t = new Looks(id);
		switch (id)
		{
			case DASHBOARD:
				t.radius = 12;
				t.background = c(0x1B1B1E);
				t.card = c(0x27272C);
				t.edge = c(0x303037);
				t.hover = c(0x313138);
				t.line = c(0x3A3A42);
				t.dim = c(0x9090A0);
				t.highlight = c(0xF5A623);
				t.accent = c(0xF04E45);
				t.good = c(0x3DD68C);
				t.chip = c(0x34343B);
				t.bar = c(0x6A2A2E);
				t.heroTop = c(0x8A2328);
				t.heroBottom = c(0x2E141A);
				t.stripes = new Color[]{c(0xF04E45), c(0xF5A623), c(0x9B7BF2)};
				break;
			case GRAVESTONE:
				t.shape = Shape.STONE;
				t.radius = 6;
				t.background = c(0x161618);
				t.card = c(0x252629);
				t.edge = c(0x3A3B3F);
				t.hover = c(0x2F3034);
				t.line = c(0x3E3F44);
				t.dim = c(0x98989C);
				t.highlight = c(0xC8C2B0);
				t.accent = c(0xC83A2E);
				t.good = c(0x7AA65A);
				t.title = c(0xDCDCDC);
				t.chip = c(0x333437);
				t.bar = c(0x7A2A24);
				t.heroTop = c(0x4C4E54);         // a dark slate card (Death Log's tombstone keeps its own light stone)
				t.heroBottom = c(0x26272B);
				t.upperCaseHeaders = true;
				t.decor = Decor.Kind.FOG;
				break;
			case OLD_SCHOOL:
				t.shape = Shape.BEVEL;
				t.radius = 0;
				t.background = c(0x3E3529);
				t.card = c(0x2B241C);
				t.hover = c(0x3A3025);
				t.line = c(0x5C4F3D);
				t.dim = c(0xD2C4A4);
				t.highlight = c(0xFF981F);
				t.accent = c(0xFF3030);
				t.good = c(0x0DC10D);
				t.title = c(0xFF981F);
				t.number = c(0xFFFF00);
				t.chip = c(0x4A3E2F);
				t.bar = c(0xB86010);
				t.heroTop = c(0x2B241C);
				t.heroBottom = c(0x2B241C);
				t.bevelLight = c(0x5C4F3D);
				t.bevelDark = c(0x16120D);
				t.blocks = c(0x453B2E);
				t.shadow = true;
				break;
			case ECTOPLASM:
				t.background = c(0x0C1112);
				t.card = c(0x141E20);
				t.edge = c(0x213838);
				t.hover = c(0x1C2C2E);
				t.line = c(0x234040);
				t.text = c(0xE6FFF6);
				t.dim = c(0x86A6A0);
				t.highlight = c(0x9EFFD6);
				t.accent = c(0x6EF0B4);
				t.good = c(0xB48CFF);
				t.title = c(0xCFFFEC);
				t.number = c(0xB8FFDF);
				t.chip = c(0x1E2E30);
				t.bar = c(0x2E7A5E);
				t.heroTop = c(0x1E3E38);
				t.heroBottom = c(0x0E1719);
				t.stripes = new Color[]{c(0x6EF0B4), c(0x5CC8E6), c(0xB48CFF)};
				t.glow = c(0x6EF0B4);
				t.motes = c(0x9EFFD6);
				t.decor = Decor.Kind.ECTO;
				break;
			case INFERNAL:
				t.background = c(0x130907);
				t.card = c(0x21120C);
				t.edge = c(0x5A2810);
				t.hover = c(0x2E1A12);
				t.line = c(0x6A2C10);
				t.text = c(0xFFE6CC);
				t.dim = c(0xBC967E);
				t.highlight = c(0xFF8A2A);
				t.accent = c(0xFF6A1A);
				t.good = c(0xFFC83C);
				t.title = c(0xFFE0C0);
				t.number = c(0xFFB040);
				t.chip = c(0x301A10);
				t.bar = c(0x9A360E);
				t.heroTop = c(0x2A140C);
				t.heroBottom = c(0x120805);
				t.stripes = new Color[]{c(0xFF6A1A), c(0xFFA02A), c(0xFFD04A)};
				t.glow = c(0xFF7020);
				t.motes = c(0xFFC060);
				t.lava = true;
				t.decor = Decor.Kind.LAVA;
				t.cardAlpha = 222;
				break;
			case AUTUMN:
				t.background = c(0x1C1410);
				t.card = c(0x2A1E17);
				t.edge = c(0x47301E);
				t.hover = c(0x372820);
				t.line = c(0x4A3222);
				t.text = c(0xFFEEDC);
				t.dim = c(0xBA9E88);
				t.highlight = c(0xF08C22);
				t.accent = c(0xE05A24);
				t.good = c(0xE8B83A);
				t.title = c(0xFFE6C8);
				t.number = c(0xFFD7A8);
				t.chip = c(0x3A2A1F);
				t.bar = c(0x8A3A18);
				t.heroTop = c(0xB04A16);
				t.heroBottom = c(0x3A1C10);
				t.stripes = new Color[]{c(0xC83A1E), c(0xE88A22), c(0xE8B83A)};
				t.decor = Decor.Kind.LEAVES;
				t.cardAlpha = 212;
				break;
			case HALLOWEEN:
				t.background = c(0x110C14);
				t.card = c(0x1D1522);
				t.edge = c(0x3E2846);
				t.hover = c(0x2A1E32);
				t.line = c(0x44304E);
				t.text = c(0xF6EEFF);
				t.dim = c(0xAE9CBC);
				t.highlight = c(0xFF9A2E);
				t.accent = c(0xFF7A1A);
				t.good = c(0xA6E05A);
				t.title = c(0xFFC070);
				t.number = c(0xFFA040);
				t.chip = c(0x2C2034);
				t.bar = c(0x8A400E);
				t.heroTop = c(0x46205A);
				t.heroBottom = c(0x140A18);
				t.stripes = new Color[]{c(0xFF7A1A), c(0x9B5CFF), c(0xA6E05A)};
				t.glow = c(0xFF8A2A);
				t.decor = Decor.Kind.HALLOWEEN;
				t.cardAlpha = 212;
				break;
			case WINTER:
				t.background = c(0x0F1622);
				t.card = c(0x1A2536);
				t.edge = c(0x2E4058);
				t.hover = c(0x243248);
				t.line = c(0x34486A);
				t.text = c(0xEEF6FF);
				t.dim = c(0xA4B8D0);
				t.highlight = c(0x8CD2FF);
				t.accent = c(0x7CC8FF);
				t.good = c(0xC8F0FF);
				t.title = c(0xF0F8FF);
				t.number = c(0xE6F4FF);
				t.chip = c(0x243248);
				t.bar = c(0x3A6A96);
				t.heroTop = c(0x3E6A9A);
				t.heroBottom = c(0x16243A);
				t.stripes = new Color[]{c(0x7CC8FF), c(0xB4E4FF), c(0xA0A8FF)};
				t.glow = c(0xB4E4FF);
				t.decor = Decor.Kind.SNOW;
				t.cardAlpha = 212;
				break;
			case CHRISTMAS:
				t.background = c(0x0C1610);
				t.card = c(0x15241A);
				t.edge = c(0x2C4A34);
				t.hover = c(0x203628);
				t.line = c(0x2E5038);
				t.text = c(0xFFF6EA);
				t.dim = c(0xA6C0AC);
				t.highlight = c(0xF2C84A);       // gold: on a money tool red means a loss
				t.accent = c(0xE23A3A);
				t.good = c(0xF2C84A);
				t.title = c(0xFFF4E4);
				t.number = c(0xFFE8C8);
				t.chip = c(0x203628);
				t.bar = c(0x8A2222);
				t.heroTop = c(0x1F5C36);
				t.heroBottom = c(0x0B2214);
				t.stripes = new Color[]{c(0xE23A3A), c(0x3AB05A), c(0xF2C84A)};
				t.glow = c(0xFFE28A);
				t.decor = Decor.Kind.CHRISTMAS;
				t.cardAlpha = 212;
				break;
			case PLAIN:
			default:
				// RuneLite's own colours: what PB History and Screenshot Gallery always looked like
				t.shape = Shape.FLAT;
				t.radius = 0;
				t.background = ColorScheme.DARK_GRAY_COLOR;
				t.card = ColorScheme.DARKER_GRAY_COLOR;
				t.hover = ColorScheme.DARK_GRAY_HOVER_COLOR;
				t.line = ColorScheme.MEDIUM_GRAY_COLOR;
				t.dim = ColorScheme.LIGHT_GRAY_COLOR;
				t.highlight = ColorScheme.BRAND_ORANGE;
				t.accent = ColorScheme.BRAND_ORANGE;
				t.good = c(0x4FD16A);
				t.chip = c(0x323232);
				t.bar = c(0x8A5A10);
				t.heroTop = ColorScheme.DARKER_GRAY_COLOR;
				t.heroBottom = ColorScheme.DARKER_GRAY_COLOR;
				break;
		}
		if (t.number == null)
		{
			t.number = t.text;
		}
		return t;
	}
}
