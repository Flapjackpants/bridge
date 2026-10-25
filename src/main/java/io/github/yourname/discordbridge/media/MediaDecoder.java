package io.github.yourname.discordbridge.media;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

public final class MediaDecoder {
	private MediaDecoder() {}

	public record Limits(int maxSide, int maxFrames, int maxSourceSide, int maxSourceFrames,
			long maxTotalPixels, int maxSheetSide) {}

	/** argb: one int[width*height] per frame, row-major ARGB. delaysMs.length == argb.size(). */
	public record Frames(int width, int height, List<int[]> argb, int[] delaysMs, boolean animated) {}

	/** IOException for unsupported format, corrupt data, or source side > maxSourceSide. */
	public static Frames decode(byte[] data, boolean animate, Limits limits) throws IOException {
		boolean gif = startsWith(data, 0, "GIF87a") || startsWith(data, 0, "GIF89a");
		boolean webp = startsWith(data, 0, "RIFF") && startsWith(data, 8, "WEBP");
		boolean other = data.length >= 4 && (u(data[0]) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G')
				|| data.length >= 3 && u(data[0]) == 0xFF && u(data[1]) == 0xD8 && u(data[2]) == 0xFF
				|| data.length >= 2 && data[0] == 'B' && data[1] == 'M';
		if (!gif && !webp && !other) {
			throw new IOException("unsupported format");
		}
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			ImageReader reader;
			if (webp) {
				// Built directly: the shaded plugin is deliberately not registered with ImageIO
				reader = new WebPImageReaderSpi().createReaderInstance();
			} else {
				Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
				if (!readers.hasNext()) {
					throw new IOException("unsupported format");
				}
				reader = readers.next();
			}
			try {
				reader.setInput(in, false, false);
				if (gif) {
					return decodeGif(reader, animate, limits);
				}
				return webp ? decodeWebp(reader, data, animate, limits) : decodeStatic(reader, limits);
			} finally {
				reader.dispose();
			}
		} catch (RuntimeException e) {
			throw new IOException("corrupt image: " + e.getMessage(), e);
		}
	}

	private static int u(byte b) {
		return b & 0xFF;
	}

	private static boolean startsWith(byte[] data, int offset, String prefix) {
		if (data.length < offset + prefix.length()) {
			return false;
		}
		for (int i = 0; i < prefix.length(); i++) {
			if (data[offset + i] != prefix.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static Frames decodeStatic(ImageReader reader, Limits limits) throws IOException {
		int w = reader.getWidth(0);
		int h = reader.getHeight(0);
		if (w > limits.maxSourceSide() || h > limits.maxSourceSide()) {
			throw new IOException("source too large");
		}
		BufferedImage src = reader.read(0);
		MediaGeometry.Box box = MediaGeometry.Box.fit(w, h, limits.maxSide(), limits.maxSide());
		BufferedImage out = new BufferedImage(box.width(), box.height(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(src, 0, 0, box.width(), box.height(), null);
		} finally {
			g.dispose();
		}
		return new Frames(box.width(), box.height(), List.of(pixels(out)), new int[] {0}, false);
	}

	/** Where a frame goes on the canvas: replace = overwrite its rect instead of alpha-blending over it. */
	private record Placement(int left, int top, int delayMs, boolean replace, String disposal) {}

	@FunctionalInterface
	private interface Placements {
		Placement get(int frame) throws IOException;
	}

	private static Frames decodeGif(ImageReader reader, boolean animate, Limits limits) throws IOException {
		int total = reader.getNumImages(true);
		if (total < 1) {
			throw new IOException("no frames");
		}
		int[] logical = logicalSize(reader);
		int lw = logical[0];
		int lh = logical[1];
		if (lw <= 0 || lh <= 0) {
			int[] first = frameInfo(reader.getImageMetadata(0));
			lw = first[0] + reader.getWidth(0);
			lh = first[1] + reader.getHeight(0);
		}
		return compose(reader, lw, lh, animate ? Math.min(total, limits.maxSourceFrames()) : 1, i -> {
			IIOMetadata md = reader.getImageMetadata(i);
			int[] info = frameInfo(md);
			return new Placement(info[0], info[1], delayMs(info[2] * 10), false, disposalOf(md));
		}, limits);
	}

	/** Still WebP decodes like PNG; animated WebP frames are sub-rects placed by the ANMF chunks. */
	private static Frames decodeWebp(ImageReader reader, byte[] data, boolean animate, Limits limits)
			throws IOException {
		WebpAnimation anim = webpAnimation(data);
		if (anim == null) {
			return decodeStatic(reader, limits);
		}
		int total = Math.min(reader.getNumImages(true), anim.frames().size());
		if (total < 1) {
			throw new IOException("no frames");
		}
		return compose(reader, anim.width(), anim.height(), animate ? Math.min(total, limits.maxSourceFrames()) : 1,
				i -> anim.frames().get(i), limits);
	}

	private static int delayMs(int ms) {
		return ms <= 10 ? 100 : ms;
	}

	private static Frames compose(ImageReader reader, int lw, int lh, int n, Placements placements, Limits limits)
			throws IOException {
		if (lw > limits.maxSourceSide() || lh > limits.maxSourceSide()) {
			throw new IOException("source too large");
		}
		MediaGeometry.Box box = MediaGeometry.Box.fit(lw, lh, limits.maxSide(), limits.maxSide());
		int bw = box.width();
		int bh = box.height();
		long pixelCap = Math.max(1, limits.maxTotalPixels() / ((long) bw * bh));
		int sheetFrames = MediaGeometry.Sheet.of(bw, bh, limits.maxFrames(), limits.maxSheetSide()).frames();
		int cap = (int) Math.min(Math.min(limits.maxFrames(), pixelCap), sheetFrames);
		int step = Math.ceilDiv(n, Math.max(1, cap));

		BufferedImage canvas = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB);
		List<int[]> kept = new ArrayList<>();
		List<Integer> delays = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Placement p = placements.get(i);
			String disposal = p.disposal();
			BufferedImage frame = reader.read(i);
			BufferedImage previous = null;
			if (disposal.equals("restoreToPrevious")) {
				previous = copy(canvas);
			}
			Graphics2D g = canvas.createGraphics();
			try {
				g.setComposite(p.replace() ? AlphaComposite.Src : AlphaComposite.SrcOver);
				g.drawImage(frame, p.left(), p.top(), null);
			} finally {
				g.dispose();
			}
			int delay = p.delayMs();
			if (i % step == 0) {
				kept.add(scaledPixels(canvas, bw, bh));
				delays.add(delay);
			} else {
				int last = delays.size() - 1;
				delays.set(last, delays.get(last) + delay);
			}
			if (disposal.equals("restoreToBackgroundColor")) {
				Graphics2D cg = canvas.createGraphics();
				try {
					cg.setComposite(AlphaComposite.Clear);
					cg.fillRect(p.left(), p.top(), frame.getWidth(), frame.getHeight());
				} finally {
					cg.dispose();
				}
			} else if (previous != null) {
				canvas = previous;
			}
		}
		int[] delaysMs = new int[delays.size()];
		for (int i = 0; i < delaysMs.length; i++) {
			delaysMs[i] = delays.get(i);
		}
		return new Frames(bw, bh, List.copyOf(kept), delaysMs, kept.size() > 1);
	}

	private record WebpAnimation(int width, int height, List<Placement> frames) {}

	/** Canvas size and ANMF placements of an animated WebP; null for a still image. */
	private static @Nullable WebpAnimation webpAnimation(byte[] data) throws IOException {
		int pos = 12;
		int width = 0;
		int height = 0;
		boolean animated = false;
		List<Placement> frames = new ArrayList<>();
		while (pos + 8 <= data.length) {
			long size = u32(data, pos + 4);
			int body = pos + 8;
			if (body + size > data.length) {
				throw new IOException("truncated webp chunk");
			}
			if (startsWith(data, pos, "VP8X") && size >= 10) {
				animated = (data[body] & 0x02) != 0;
				width = u24(data, body + 4) + 1;
				height = u24(data, body + 7) + 1;
			} else if (startsWith(data, pos, "ANMF") && size >= 16) {
				int flags = data[body + 15];
				frames.add(new Placement(u24(data, body) * 2, u24(data, body + 3) * 2, delayMs(u24(data, body + 12)),
						(flags & 0x02) != 0, (flags & 0x01) != 0 ? "restoreToBackgroundColor" : "none"));
			}
			pos = body + (int) size + (int) (size & 1);
		}
		return animated && !frames.isEmpty() ? new WebpAnimation(width, height, List.copyOf(frames)) : null;
	}

	private static int u24(byte[] data, int at) {
		return u(data[at]) | u(data[at + 1]) << 8 | u(data[at + 2]) << 16;
	}

	private static long u32(byte[] data, int at) {
		return u24(data, at) | (long) u(data[at + 3]) << 24;
	}

	private static BufferedImage copy(BufferedImage src) {
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setComposite(AlphaComposite.Src);
			g.drawImage(src, 0, 0, null);
		} finally {
			g.dispose();
		}
		return out;
	}

	private static int[] scaledPixels(BufferedImage canvas, int w, int h) {
		if (canvas.getWidth() == w && canvas.getHeight() == h) {
			return pixels(canvas);
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setComposite(AlphaComposite.Src);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(canvas, 0, 0, w, h, null);
		} finally {
			g.dispose();
		}
		return pixels(out);
	}

	private static int[] pixels(BufferedImage img) {
		return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
	}

	private static int[] logicalSize(ImageReader reader) throws IOException {
		IIOMetadata sm = reader.getStreamMetadata();
		if (sm == null) {
			return new int[] {0, 0};
		}
		Node root = sm.getAsTree("javax_imageio_gif_stream_1.0");
		Node lsd = find(root, "LogicalScreenDescriptor");
		if (lsd == null) {
			return new int[] {0, 0};
		}
		return new int[] {attr(lsd, "logicalScreenWidth", 0), attr(lsd, "logicalScreenHeight", 0)};
	}

	/** {left, top, delayTime in centiseconds}. */
	private static int[] frameInfo(IIOMetadata md) {
		Node root = md.getAsTree("javax_imageio_gif_image_1.0");
		Node desc = find(root, "ImageDescriptor");
		Node gce = find(root, "GraphicControlExtension");
		return new int[] {
				desc == null ? 0 : attr(desc, "imageLeftPosition", 0),
				desc == null ? 0 : attr(desc, "imageTopPosition", 0),
				gce == null ? 0 : attr(gce, "delayTime", 0)};
	}

	private static String disposalOf(IIOMetadata md) {
		Node gce = find(md.getAsTree("javax_imageio_gif_image_1.0"), "GraphicControlExtension");
		if (gce == null || gce.getAttributes().getNamedItem("disposalMethod") == null) {
			return "none";
		}
		return gce.getAttributes().getNamedItem("disposalMethod").getNodeValue();
	}

	private static Node find(Node root, String name) {
		for (Node c = root.getFirstChild(); c != null; c = c.getNextSibling()) {
			if (c.getNodeName().equals(name)) {
				return c;
			}
		}
		return null;
	}

	private static int attr(Node node, String name, int fallback) {
		Node a = node.getAttributes().getNamedItem(name);
		if (a == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(a.getNodeValue());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
