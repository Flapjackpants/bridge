package io.github.yourname.discordbridge.media;

import static org.junit.jupiter.api.Assertions.*;

import io.github.yourname.discordbridge.media.MediaDecoder.Frames;
import io.github.yourname.discordbridge.media.MediaDecoder.Limits;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;

class MediaDecoderTest {
	private static final int RED = 0xFFFF0000;
	private static final int BLUE = 0xFF0000FF;
	private static final int GREEN = 0xFF00FF00;
	private static final Limits LIMITS = new Limits(256, 64, 2048, 500, 4_194_304L, 4096);

	private record GifFrame(BufferedImage img, String disposal, int delayCs, int left, int top) {}

	private static BufferedImage solid(int w, int h, int argb) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(argb, true));
		g.fillRect(0, 0, w, h);
		g.dispose();
		return img;
	}

	private static byte[] gif(List<GifFrame> frames) throws IOException {
		ImageWriter w = ImageIO.getImageWritersByFormatName("gif").next();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (ImageOutputStream out = ImageIO.createImageOutputStream(bos)) {
			w.setOutput(out);
			w.prepareWriteSequence(null);
			for (GifFrame f : frames) {
				IIOMetadata md = w.getDefaultImageMetadata(
						ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB), null);
				String fmt = "javax_imageio_gif_image_1.0";
				IIOMetadataNode root = (IIOMetadataNode) md.getAsTree(fmt);
				IIOMetadataNode gce = child(root, "GraphicControlExtension");
				gce.setAttribute("disposalMethod", f.disposal());
				gce.setAttribute("delayTime", Integer.toString(f.delayCs()));
				gce.setAttribute("transparentColorFlag", "TRUE");
				gce.setAttribute("transparentColorIndex", "0");
				gce.setAttribute("userInputFlag", "FALSE");
				IIOMetadataNode desc = child(root, "ImageDescriptor");
				desc.setAttribute("imageLeftPosition", Integer.toString(f.left()));
				desc.setAttribute("imageTopPosition", Integer.toString(f.top()));
				md.setFromTree(fmt, root);
				w.writeToSequence(new IIOImage(f.img(), null, md), null);
			}
			w.endWriteSequence();
		} finally {
			w.dispose();
		}
		return bos.toByteArray();
	}

	private static IIOMetadataNode child(IIOMetadataNode root, String name) {
		for (int i = 0; i < root.getLength(); i++) {
			if (root.item(i).getNodeName().equals(name)) {
				return (IIOMetadataNode) root.item(i);
			}
		}
		IIOMetadataNode n = new IIOMetadataNode(name);
		root.appendChild(n);
		return n;
	}

	private static byte[] png(int w, int h) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ImageIO.write(solid(w, h, RED), "png", bos);
		return bos.toByteArray();
	}

	/** red 4x4 canvas, then 2x2 blue at (0,0) with the given disposal, then 1x1 green at (3,3). */
	private static byte[] disposalGif(String disposalOfBlue) throws IOException {
		return gif(List.of(
				new GifFrame(solid(4, 4, RED), "none", 10, 0, 0),
				new GifFrame(solid(2, 2, BLUE), disposalOfBlue, 10, 0, 0),
				new GifFrame(solid(1, 1, GREEN), "none", 10, 3, 3)));
	}

	@Test
	void disposeToBackground() throws Exception {
		Frames f = MediaDecoder.decode(disposalGif("restoreToBackgroundColor"), true, LIMITS);
		assertEquals(3, f.argb().size());
		int[] p = f.argb().get(2);
		assertEquals(4, f.width());
		assertEquals(0, p[0] >>> 24);
		assertEquals(0, p[1 * 4 + 1] >>> 24);
		assertEquals(GREEN, p[3 * 4 + 3]);
		assertEquals(RED, p[2 * 4 + 2]);
		assertEquals(RED, p[0 * 4 + 3]);
	}

	@Test
	void disposeToPrevious() throws Exception {
		Frames f = MediaDecoder.decode(disposalGif("restoreToPrevious"), true, LIMITS);
		assertEquals(BLUE, f.argb().get(1)[0]);
		int[] p = f.argb().get(2);
		assertEquals(RED, p[0]);
		assertEquals(GREEN, p[3 * 4 + 3]);
	}

	@Test
	void doNotDispose() throws Exception {
		Frames f = MediaDecoder.decode(disposalGif("doNotDispose"), true, LIMITS);
		assertEquals(BLUE, f.argb().get(2)[0]);
		assertTrue(f.animated());
	}

	@Test
	void delayClamp() throws Exception {
		byte[] g = gif(List.of(
				new GifFrame(solid(2, 2, RED), "none", 0, 0, 0),
				new GifFrame(solid(2, 2, BLUE), "none", 1, 0, 0),
				new GifFrame(solid(2, 2, GREEN), "none", 2, 0, 0)));
		Frames f = MediaDecoder.decode(g, true, LIMITS);
		assertArrayEquals(new int[] {100, 100, 20}, f.delaysMs());
	}

	@Test
	void decimatesAndSumsDelays() throws Exception {
		GifFrame[] fs = new GifFrame[10];
		for (int i = 0; i < 10; i++) {
			fs[i] = new GifFrame(solid(2, 2, i % 2 == 0 ? RED : BLUE), "none", 5, 0, 0);
		}
		Limits l = new Limits(256, 5, 2048, 500, 4_194_304L, 4096);
		Frames f = MediaDecoder.decode(gif(List.of(fs)), true, l);
		assertEquals(5, f.argb().size());
		assertEquals(5, f.delaysMs().length);
		for (int d : f.delaysMs()) {
			assertEquals(100, d);
		}
	}

	@Test
	void staticWhenNotAnimating() throws Exception {
		Frames f = MediaDecoder.decode(disposalGif("none"), false, LIMITS);
		assertEquals(1, f.argb().size());
		assertFalse(f.animated());
	}

	@Test
	void downscalesPng() throws Exception {
		Frames f = MediaDecoder.decode(png(512, 256), true, LIMITS);
		assertEquals(256, f.width());
		assertEquals(128, f.height());
		assertEquals(1, f.argb().size());
		assertEquals(256 * 128, f.argb().get(0).length);
		assertFalse(f.animated());
	}

	@Test
	void rejectsHugeSource() throws Exception {
		byte[] big = png(3000, 10);
		assertThrows(IOException.class, () -> MediaDecoder.decode(big, true, LIMITS));
	}

	@Test
	void rejectsUnsupported() {
		byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
		assertThrows(IOException.class, () -> MediaDecoder.decode(webp, true, LIMITS));
		assertThrows(IOException.class, () -> MediaDecoder.decode(new byte[0], true, LIMITS));
		byte[] riff = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '};
		assertThrows(IOException.class, () -> MediaDecoder.decode(riff, true, LIMITS));
	}

	/** 16x16 lossless: red (50 ms), then a 4x4 blue sub-rect at (8,8) blended over it (70 ms), then green (90 ms). */
	private static byte[] animWebp() throws IOException {
		try (var in = MediaDecoderTest.class.getResourceAsStream("/media/anim.webp")) {
			return in.readAllBytes();
		}
	}

	@Test
	void animatedWebpComposited() throws Exception {
		Frames f = MediaDecoder.decode(animWebp(), true, LIMITS);
		assertEquals(16, f.width());
		assertEquals(16, f.height());
		assertTrue(f.animated());
		assertEquals(3, f.argb().size());
		assertArrayEquals(new int[] {50, 70, 90}, f.delaysMs());
		assertEquals(RED, f.argb().get(0)[9 * 16 + 9]);
		assertEquals(RED, f.argb().get(1)[0]);
		assertEquals(BLUE, f.argb().get(1)[9 * 16 + 9]);
		assertEquals(RED, f.argb().get(1)[12 * 16 + 12]);
		assertEquals(GREEN, f.argb().get(2)[9 * 16 + 9]);
	}

	@Test
	void animatedWebpFirstFrameWhenStatic() throws Exception {
		Frames f = MediaDecoder.decode(animWebp(), false, LIMITS);
		assertFalse(f.animated());
		assertEquals(1, f.argb().size());
		assertEquals(RED, f.argb().get(0)[0]);
	}
}
