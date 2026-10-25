package io.github.yourname.discordbridge.media;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MediaLoader {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");

	@FunctionalInterface
	public interface Fetcher {
		byte[] fetch(String url) throws IOException;
	}

	/** delaysMs.length == sheet.frames(); argb.length == sheet.width()*sheet.height(). */
	public record Result(MediaGeometry.Sheet sheet, int[] argb, int[] delaysMs) {}

	public record Outcome(@Nullable Result result, boolean tooLarge) {}

	private final Fetcher fetcher;
	private final @Nullable DiskCache disk;
	private final MediaDecoder.Limits limits;

	public MediaLoader(Fetcher fetcher, @Nullable DiskCache disk, MediaDecoder.Limits limits) {
		this.fetcher = fetcher;
		this.disk = disk;
		this.limits = limits;
	}

	/**
	 * Tries sources in order; first one that fetches and decodes wins. Returns null if all fail.
	 * Throws InterruptedIOException only when the fetcher does (stop immediately). Never throws anything else.
	 */
	public @Nullable Result load(List<MediaFetchPlan.Source> sources) throws InterruptedIOException {
		return loadOutcome(sources).result();
	}

	/** Like load, but also reports whether a failure was because the media exceeded a size limit. */
	public Outcome loadOutcome(List<MediaFetchPlan.Source> sources) throws InterruptedIOException {
		boolean tooLarge = false;
		for (MediaFetchPlan.Source s : sources) {
			try {
				Result r = loadOne(s);
				if (r != null) {
					return new Outcome(r, false);
				}
			} catch (InterruptedIOException e) {
				throw e;
			} catch (MediaTooLargeException e) {
				tooLarge = true;
			} catch (RuntimeException e) {
				LOG.debug("media load failed for {}: {}", s.url(), e.toString());
			}
		}
		return new Outcome(null, tooLarge);
	}

	private @Nullable Result loadOne(MediaFetchPlan.Source s) throws InterruptedIOException, MediaTooLargeException {
		byte[] data = disk == null ? null : disk.get(s.cacheKey());
		boolean fromDisk = data != null;
		if (data == null) {
			data = fetchAndStore(s);
			if (data == null) {
				return null;
			}
		}
		MediaDecoder.Frames frames;
		try {
			frames = MediaDecoder.decode(data, s.animated(), limits);
		} catch (IOException e) {
			if (e instanceof MediaTooLargeException tl) {
				throw tl;
			}
			if (!fromDisk) {
				LOG.debug("media decode failed for {}: {}", s.url(), e.getMessage());
				return null;
			}
			data = fetchAndStore(s);
			if (data == null) {
				return null;
			}
			try {
				frames = MediaDecoder.decode(data, s.animated(), limits);
			} catch (IOException e2) {
				if (e2 instanceof MediaTooLargeException tl) {
					throw tl;
				}
				LOG.debug("media decode failed for {}: {}", s.url(), e2.getMessage());
				return null;
			}
		}
		MediaGeometry.Sheet sheet;
		try {
			sheet = MediaGeometry.Sheet.of(frames.width(), frames.height(), frames.argb().size(), limits.maxSheetSide());
		} catch (IllegalArgumentException e) {
			LOG.debug("media sheet rejected for {}: {}", s.url(), e.getMessage());
			return null;
		}
		return new Result(sheet, MediaLayout.packSheet(frames, sheet), Arrays.copyOf(frames.delaysMs(), sheet.frames()));
	}

	private byte @Nullable [] fetchAndStore(MediaFetchPlan.Source s)
			throws InterruptedIOException, MediaTooLargeException {
		byte[] data;
		try {
			data = fetcher.fetch(s.url());
		} catch (InterruptedIOException e) {
			throw e;
		} catch (MediaTooLargeException e) {
			LOG.debug("media fetch failed for {}: {}", s.url(), e.getMessage());
			throw e;
		} catch (IOException | RuntimeException e) {
			LOG.debug("media fetch failed for {}: {}", s.url(), e.getMessage());
			return null;
		}
		if (disk != null) {
			disk.put(s.cacheKey(), data);
		}
		return data;
	}
}
