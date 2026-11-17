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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.runelite.client.util.Filepath;

/**
 * A PB's picture: PB History's own JPEG copy of RuneLite's boss-kill screenshot of the kill that set it (user 2026-11-08:
 * "screenshot yes i like that. it should take screen shots on boss kills now anyway"), kept as pictures/&lt;the PB's
 * time in ms&gt;.jpg in the plugin's folder. RuneLite's own screenshot file is never touched.
 */
final class Pictures
{
	static final String DIR = "pictures";
	/** A boss-kill screenshot this long after a PB (or before it) can be that PB's. RuneLite saves it a few seconds after the kill. */
	static final long AFTER_MS = 120_000L;
	static final long BEFORE_MS = 30_000L;
	static final int KEEP_WIDTH = 1280;
	private static final float QUALITY = 0.85f;
	private static final Pattern FILE = Pattern.compile("^(\\d{10,})\\.jpg$");
	/** RuneLite's boss-kill file: "Vorkath(123) 2026-11-07_22-33-42.png", "Chambers of Xeric Challenge Mode(4) 2026-...png" */
	private static final Pattern KILL = Pattern.compile("^(.+?)\\(([\\d,]+)\\) (\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})(?: ?\\(\\d+\\))?\\.png$");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	/** a boss-kill picture's name without its time: "Vorkath(852)" */
	private static final Pattern NAMED = Pattern.compile("^(.+?)\\(([\\d,]+)\\)$");

	/** One boss-kill screenshot, from its file name. */
	static final class Shot
	{
		/** the boss as RuneLite named the file, made comparable ({@link #norm}) */
		final String boss;
		final int kc;
		final long at;

		Shot(String boss, int kc, long at)
		{
			this.boss = boss;
			this.kc = kc;
			this.at = at;
		}
	}

	private Pictures()
	{
	}

	static String fileName(long at)
	{
		return at + ".jpg";
	}

	static long timeOf(String fileName)
	{
		Matcher m = fileName == null ? null : FILE.matcher(fileName);
		return m != null && m.matches() ? Long.parseLong(m.group(1)) : -1;
	}

	/** "Theatre of Blood: Hard Mode" → "theatre of blood hard mode": letters and digits only, one space between words. */
	static String norm(String s)
	{
		return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	/** An imported screenshot file, as "&lt;its folder&gt;/&lt;its name&gt;" (text from Filepath): one of RuneLite's boss kills, or null. */
	static Shot bossShot(String path, ZoneId zone)
	{
		if (path == null)
		{
			return null;
		}
		String[] parts = path.replace('\\', '/').split("/");
		if (parts.length < 2 || !"Boss Kills".equals(parts[parts.length - 2]))
		{
			return null;
		}
		Matcher m = KILL.matcher(parts[parts.length - 1]);
		if (!m.matches())
		{
			return null;
		}
		try
		{
			long at = LocalDateTime.parse(m.group(3), TIME).atZone(zone).toInstant().toEpochMilli();
			return new Shot(norm(m.group(1)), Integer.parseInt(m.group(2).replace(",", "")), at);
		}
		catch (DateTimeParseException | NumberFormatException e)
		{
			return null;
		}
	}

	/** RuneLite's boss-kill picture name ("Vorkath(852)", from the kill-count message) taken at {@code at}; null = not one. */
	static Shot named(String name, long at)
	{
		Matcher m = name == null ? null : NAMED.matcher(name);
		if (m == null || !m.matches())
		{
			return null;
		}
		try
		{
			return new Shot(norm(m.group(1)), Integer.parseInt(m.group(2).replace(",", "")), at);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** Whether a boss-kill screenshot is of the kill that set this PB: same boss (RuneLite's names), same kill count, close in time. */
	static boolean sameKill(Shot s, Entry pb)
	{
		if (!Entry.PB.equals(pb.kind) || pb.at <= 0 || s.at < pb.at - BEFORE_MS || s.at > pb.at + AFTER_MS)
		{
			return false;
		}
		String boss = norm(pb.boss);
		return (boss.equals(s.boss) || boss.startsWith(s.boss + " ")) && (pb.kc <= 0 || pb.kc == s.kc);
	}

	/** How deep the import looks under the folder you pick: enough for .runelite/screenshots/&lt;name&gt;/Boss Kills/file. */
	static final int DEPTH = 4;

	/**
	 * Every boss-kill screenshot under the folder you picked (.runelite, its screenshots folder, your name's folder or its
	 * Boss Kills folder), by what its name says. Only the file's own folder name is read: never anything above the
	 * picked folder (RuneLite's file rules forbid it).
	 */
	static Map<Filepath, Shot> find(Filepath root, ZoneId zone) throws IOException
	{
		Map<Filepath, Shot> out = new LinkedHashMap<>();
		try (Stream<Filepath> files = root.walk(DEPTH))
		{
			for (Filepath f : (Iterable<Filepath>) files::iterator)
			{
				if (f.equals(root))
				{
					continue;
				}
				Filepath parent = f.getParent();
				String folder = parent == null ? null : parent.getFileName();
				String name = f.getFileName();
				Shot s = folder == null || name == null ? null : bossShot(folder + "/" + name, zone);
				if (s != null)
				{
					out.put(f, s);
				}
			}
		}
		return out;
	}

	/**
	 * The import (user 2026-11-08: older PBs get their pictures too): for each PB PB History recorded (it has a time) and
	 * that has no picture yet, the screenshot of the kill that set it — the closest in time when several match. A raid's
	 * PB is saved twice (the mode, and the mode with the team size): both get the same screenshot. PBs from before PB
	 * History have no time, so nothing can match them. Returns the PB's time → its screenshot.
	 */
	static <F> Map<Long, F> match(List<Entry> lines, Map<F, Shot> shots, Set<Long> have)
	{
		Map<Long, F> out = new HashMap<>();
		for (Entry pb : lines)
		{
			if (!Entry.PB.equals(pb.kind) || pb.at <= 0 || have.contains(pb.at) || out.containsKey(pb.at))
			{
				continue;
			}
			F best = null;
			long gap = Long.MAX_VALUE;
			for (Map.Entry<F, Shot> s : shots.entrySet())
			{
				long g = Math.abs(s.getValue().at - pb.at);
				if (g < gap && sameKill(s.getValue(), pb))
				{
					best = s.getKey();
					gap = g;
				}
			}
			if (best != null)
			{
				out.put(pb.at, best);
			}
		}
		return out;
	}

	static BufferedImage scaled(BufferedImage img, int max)
	{
		int w = img.getWidth(), h = img.getHeight();
		if (w > max)
		{
			h = Math.max(1, (int) Math.round(h * (max / (double) w)));
			w = max;
		}
		else if (img.getType() == BufferedImage.TYPE_INT_RGB)
		{
			return img;
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(img, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	static void write(BufferedImage img, Filepath out) throws IOException
	{
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
		if (!writers.hasNext())
		{
			throw new IOException("no JPEG writer");
		}
		ImageWriter w = writers.next();
		try (OutputStream raw = out.openOutputStream(); ImageOutputStream ios = ImageIO.createImageOutputStream(raw))
		{
			ImageWriteParam param = w.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(QUALITY);
			w.setOutput(ios);
			w.write(null, new IIOImage(scaled(img, KEEP_WIDTH), null, null), param);
		}
		finally
		{
			w.dispose();
		}
	}

	static BufferedImage read(Filepath f) throws IOException
	{
		try (InputStream in = f.openInputStream())
		{
			return ImageIO.read(in);
		}
	}
}
