package io.github.flapjackpants.discordbridge.media;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiskCache {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");
	private static final Pattern KEY = Pattern.compile("[a-z0-9-]{1,80}");

	private final Path dir;
	private final long maxBytes;

	public DiskCache(Path dir, long maxBytes) {
		this.dir = dir;
		this.maxBytes = maxBytes;
	}

	private static void checkKey(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			throw new IllegalArgumentException("bad cache key");
		}
	}

	public synchronized @Nullable byte[] get(String key) {
		checkKey(key);
		if (maxBytes <= 0) {
			return null;
		}
		Path file = dir.resolve(key + ".bin");
		try {
			if (!Files.isRegularFile(file)) {
				return null;
			}
			byte[] data = Files.readAllBytes(file);
			Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
			return data;
		} catch (IOException e) {
			LOG.debug("disk cache read failed for {}: {}", key, e.getMessage());
			return null;
		}
	}

	public synchronized void put(String key, byte[] data) {
		checkKey(key);
		if (maxBytes <= 0) {
			return;
		}
		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve(key + ".tmp");
			Path file = dir.resolve(key + ".bin");
			Files.write(tmp, data);
			try {
				Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			LOG.debug("disk cache write failed for {}: {}", key, e.getMessage());
			return;
		}
		trim();
	}

	public synchronized void trim() {
		if (maxBytes <= 0 || !Files.isDirectory(dir)) {
			return;
		}
		try {
			List<Path> bins = new ArrayList<>();
			try (Stream<Path> files = Files.list(dir)) {
				for (Path p : (Iterable<Path>) files::iterator) {
					String name = p.getFileName().toString();
					if (name.endsWith(".tmp")) {
						Files.deleteIfExists(p);
					} else if (name.endsWith(".bin")) {
						bins.add(p);
					}
				}
			}
			bins.sort(Comparator.comparing((Path p) -> mtime(p)).thenComparing(Path::getFileName));
			long total = 0;
			for (Path p : bins) {
				total += Files.size(p);
			}
			for (Path p : bins) {
				if (total <= maxBytes) {
					break;
				}
				long size = Files.size(p);
				Files.deleteIfExists(p);
				total -= size;
			}
		} catch (IOException e) {
			LOG.debug("disk cache trim failed: {}", e.getMessage());
		}
	}

	private static FileTime mtime(Path p) {
		try {
			return Files.getLastModifiedTime(p);
		} catch (IOException e) {
			return FileTime.fromMillis(0);
		}
	}
}
