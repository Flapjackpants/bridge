package io.github.yourname.discordbridge.media;

public final class MediaGeometry {
	private MediaGeometry() {}

	public record Sheet(int frameWidth, int frameHeight, int cols, int rows, int frames) {
		public int width() {
			return cols * frameWidth;
		}

		public int height() {
			return rows * frameHeight;
		}

		public int u(int frame) {
			return (frame % cols) * frameWidth;
		}

		public int v(int frame) {
			return (frame / cols) * frameHeight;
		}

		public static Sheet of(int fw, int fh, int frames, int maxSide) {
			if (fw < 1 || fh < 1 || fw > maxSide || fh > maxSide || frames < 1) {
				throw new IllegalArgumentException("bad sheet " + fw + "x" + fh + " frames=" + frames);
			}
			int cols = Math.min(frames, Math.max(1, maxSide / fw));
			int rows = Math.ceilDiv(frames, cols);
			if (rows * fh > maxSide) {
				rows = Math.max(1, maxSide / fh);
				frames = Math.min(frames, cols * rows);
			}
			return new Sheet(fw, fh, cols, rows, frames);
		}
	}

	public record Box(int width, int height) {
		public static Box fit(int srcW, int srcH, int maxW, int maxH) {
			if (srcW <= 0 || srcH <= 0) {
				int side = Math.max(1, Math.min(maxW, maxH) / 2);
				return new Box(side, side);
			}
			double scale = Math.min(1.0, Math.min(maxW / (double) srcW, maxH / (double) srcH));
			return new Box(Math.max(1, (int) Math.round(srcW * scale)), Math.max(1, (int) Math.round(srcH * scale)));
		}
	}
}
