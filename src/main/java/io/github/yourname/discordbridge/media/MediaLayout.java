package io.github.yourname.discordbridge.media;

public final class MediaLayout {
	private static final int DEFAULT_DELAY_MS = 100;

	private MediaLayout() {}

	/** Rows of rowHeight px needed to cover boxHeight px; boxHeight<1 counts as 1, rowHeight<1 as 1. Result >= 1. */
	public static int rows(int boxHeight, int rowHeight) {
		return Math.ceilDiv(Math.max(1, boxHeight), Math.max(1, rowHeight));
	}

	/** Looping frame index at elapsedMs. delays <= 0 count as 100 ms. length <= 1 → 0. elapsedMs < 0 → 0. Never throws. */
	public static int frameAt(int[] delaysMs, long elapsedMs) {
		if (delaysMs == null || delaysMs.length <= 1) {
			return 0;
		}
		long total = 0;
		for (int d : delaysMs) {
			total += effective(d);
		}
		long t = Math.max(0, elapsedMs) % total;
		for (int i = 0; i < delaysMs.length; i++) {
			long d = effective(delaysMs[i]);
			if (t < d) {
				return i;
			}
			t -= d;
		}
		return 0;
	}

	private static int effective(int delayMs) {
		return delayMs <= 0 ? DEFAULT_DELAY_MS : delayMs;
	}

	/**
	 * Row-major ARGB of length sheet.width()*sheet.height(); frame i copied to (sheet.u(i), sheet.v(i));
	 * only the first sheet.frames() frames are copied; unused cells stay 0.
	 */
	public static int[] packSheet(MediaDecoder.Frames frames, MediaGeometry.Sheet sheet) {
		int fw = sheet.frameWidth();
		int fh = sheet.frameHeight();
		if (frames.width() != fw || frames.height() != fh || frames.argb().size() < sheet.frames()) {
			throw new IllegalArgumentException("frames do not match sheet");
		}
		int[] out = new int[sheet.width() * sheet.height()];
		for (int f = 0; f < sheet.frames(); f++) {
			int[] src = frames.argb().get(f);
			for (int y = 0; y < fh; y++) {
				System.arraycopy(src, y * fw, out, (sheet.v(f) + y) * sheet.width() + sheet.u(f), fw);
			}
		}
		return out;
	}
}
