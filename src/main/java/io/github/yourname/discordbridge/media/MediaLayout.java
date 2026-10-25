package io.github.yourname.discordbridge.media;

public final class MediaLayout {
	private static final int DEFAULT_DELAY_MS = 100;

	private MediaLayout() {}

	/** Rows of rowHeight px needed to cover boxHeight px; boxHeight<1 counts as 1, rowHeight<1 as 1. Result >= 1. */
	public static int rows(int boxHeight, int rowHeight) {
		return Math.ceilDiv(Math.max(1, boxHeight), Math.max(1, rowHeight));
	}

	/** Chat row height in GUI px for the chatLineSpacing option value. */
	public static int entryHeight(double lineSpacing) {
		return (int) (9 * (lineSpacing + 1));
	}

	/** Top y of the chat row whose text is drawn at textTop. */
	public static int rowTop(int textTop, double lineSpacing) {
		return textTop + (int) Math.round(8.0 * (lineSpacing + 1) - 4.0 * lineSpacing) - entryHeight(lineSpacing);
	}

	/** destHeight <= 0 means nothing to draw for this row. */
	public record Slice(int destHeight, int srcTop, int srcHeight) {}

	/** Slice of a boxHeight-tall image shown on row `row` (rowHeight px each), sampled from a frameHeight-tall frame. Never throws. */
	public static Slice slice(int row, int rowHeight, int boxHeight, int frameHeight) {
		int destHeight = Math.min(rowHeight, boxHeight - row * rowHeight);
		if (destHeight <= 0 || boxHeight <= 0 || frameHeight <= 0) {
			return new Slice(0, 0, 0);
		}
		int srcTop = (int) Math.round((double) row * rowHeight * frameHeight / boxHeight);
		int srcBottom = (int) Math.round((double) Math.min((row + 1) * rowHeight, boxHeight) * frameHeight / boxHeight);
		return new Slice(destHeight, srcTop, Math.max(1, srcBottom - srcTop));
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
