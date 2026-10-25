package io.github.yourname.discordbridge.client.media;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.yourname.discordbridge.client.ClientSettings;
import io.github.yourname.discordbridge.config.ClientMediaConfig;
import io.github.yourname.discordbridge.config.MediaMode;
import io.github.yourname.discordbridge.media.DiskCache;
import io.github.yourname.discordbridge.media.LruBudget;
import io.github.yourname.discordbridge.media.MediaDownloader;
import io.github.yourname.discordbridge.media.MediaFetchPlan;
import io.github.yourname.discordbridge.media.MediaGeometry;
import io.github.yourname.discordbridge.media.MediaLayout;
import io.github.yourname.discordbridge.media.MediaLoader;
import io.github.yourname.discordbridge.media.MediaRef;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Public methods: render thread only. */
public final class ClientMediaManager {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");
	private static final long PROTECT_MS = 2000;

	public enum State { LOADING, READY, FAILED }

	public record Drawable(Identifier texture, MediaGeometry.Sheet sheet, int[] delaysMs, long startMs) {
		/** Frame index to draw at nowMs (same clock as ClientMediaManager.nowMs()). */
		public int frame(long nowMs) {
			return MediaLayout.frameAt(delaysMs, nowMs - startMs);
		}
	}

	private static final class Entry {
		State state = State.LOADING;
		@Nullable Drawable drawable;
	}

	private static @Nullable ClientMediaManager instance;

	private final Minecraft mc;
	private final AtomicInteger threadSeq = new AtomicInteger();
	private final Map<MediaRef, Entry> entries = new HashMap<>();
	private ClientMediaConfig cfg;
	private @Nullable ThreadPoolExecutor exec;
	private MediaLoader loader;
	private LruBudget<MediaRef> budget;
	private int generation;
	private int textureSeq;

	private ClientMediaManager(Minecraft mc, ClientMediaConfig cfg) {
		this.mc = mc;
		this.cfg = cfg;
		this.budget = new LruBudget<>(cfg.memoryBudgetBytes());
		this.loader = new MediaLoader(url -> {
			throw new java.io.IOException("not configured");
		}, null, cfg.limits());
	}

	/** Once, from onInitializeClient. */
	public static void init() {
		ClientMediaManager m = new ClientMediaManager(Minecraft.getInstance(), ClientSettings.get());
		instance = m;
		m.configure(ClientSettings.get());
		ClientSettings.addListener(c -> Minecraft.getInstance().execute(() -> m.configure(c)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(m::clear));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			m.clear();
			ThreadPoolExecutor e = m.exec;
			if (e != null) {
				e.shutdownNow();
			}
		});
	}

	public static ClientMediaManager get() {
		ClientMediaManager m = instance;
		if (m == null) {
			throw new IllegalStateException("ClientMediaManager.init() not called");
		}
		return m;
	}

	public static long nowMs() {
		return System.nanoTime() / 1_000_000L;
	}

	private void configure(ClientMediaConfig c) {
		clear();
		cfg = c;
		budget = new LruBudget<>(c.memoryBudgetBytes());
		if (exec != null) {
			exec.shutdownNow();
		}
		ThreadPoolExecutor pool = new ThreadPoolExecutor(c.threads(), c.threads(), 30, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(), r -> {
					Thread t = new Thread(r, "discordbridge-media-" + threadSeq.incrementAndGet());
					t.setDaemon(true);
					return t;
				});
		pool.allowCoreThreadTimeOut(true);
		exec = pool;
		DiskCache disk = c.diskCacheBytes() > 0
				? new DiskCache(FabricLoader.getInstance().getGameDir().resolve("discordbridge-cache"), c.diskCacheBytes())
				: null;
		loader = new MediaLoader(new MediaDownloader(c.policy(), Duration.ofSeconds(10))::fetch, disk, c.limits());
	}

	/**
	 * null unless READY. Absent entry + mode != OFF → starts loading (or FAILED at once if sources(ref) is empty).
	 * READY → budget.touch(ref, nowMs()). mode OFF → null, no entry created.
	 */
	public @Nullable Drawable request(MediaRef ref) {
		if (cfg.mode() == MediaMode.OFF) {
			return null;
		}
		Entry e = entries.get(ref);
		if (e == null) {
			e = new Entry();
			entries.put(ref, e);
			List<MediaFetchPlan.Source> src = cfg.sources(ref);
			if (src.isEmpty()) {
				e.state = State.FAILED;
				return null;
			}
			int gen = generation;
			MediaLoader l = loader;
			ThreadPoolExecutor pool = exec;
			if (pool == null) {
				e.state = State.FAILED;
				return null;
			}
			pool.execute(() -> work(ref, src, gen, l));
			return null;
		}
		if (e.state == State.READY && e.drawable != null) {
			budget.touch(ref, nowMs());
			return e.drawable;
		}
		return null;
	}

	/** null when never requested, evicted, cleared, or mode OFF. */
	public @Nullable State state(MediaRef ref) {
		if (cfg.mode() == MediaMode.OFF) {
			return null;
		}
		Entry e = entries.get(ref);
		return e == null ? null : e.state;
	}

	/** Releases all textures, drops entries; results of in-flight loads are discarded. */
	public void clear() {
		generation++;
		for (Entry e : entries.values()) {
			if (e.drawable != null) {
				mc.getTextureManager().release(e.drawable.texture());
			}
		}
		entries.clear();
		budget = new LruBudget<>(cfg.memoryBudgetBytes());
	}

	private void work(MediaRef ref, List<MediaFetchPlan.Source> src, int gen, MediaLoader l) {
		try {
			MediaLoader.Result r = l.load(src);
			NativeImage img = null;
			if (r != null) {
				int w = r.sheet().width();
				int h = r.sheet().height();
				img = new NativeImage(w, h, false);
				try {
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							img.setPixel(x, y, r.argb()[y * w + x]);
						}
					}
				} catch (RuntimeException e) {
					img.close();
					throw e;
				}
			}
			NativeImage fimg = img;
			mc.execute(() -> finish(ref, gen, r, fimg));
		} catch (InterruptedIOException e) {
			// executor shut down; nothing to report
		} catch (RuntimeException e) {
			LOG.debug("media work failed for {}: {}", ref.url(), e.toString());
			mc.execute(() -> finish(ref, gen, null, null));
		}
	}

	private void finish(MediaRef ref, int gen, MediaLoader.@Nullable Result r, @Nullable NativeImage img) {
		Entry e = entries.get(ref);
		if (gen != generation || e == null || e.state != State.LOADING) {
			if (img != null) {
				img.close();
			}
			return;
		}
		if (r == null || img == null) {
			if (img != null) {
				img.close();
			}
			e.state = State.FAILED;
			return;
		}
		Identifier id = Identifier.fromNamespaceAndPath("discordbridge", "media/" + textureSeq++);
		mc.getTextureManager().register(id, new DynamicTexture(() -> "discordbridge media " + id, img));
		e.drawable = new Drawable(id, r.sheet(), r.delaysMs(), nowMs());
		e.state = State.READY;
		budget.put(ref, 4L * r.sheet().width() * r.sheet().height(), nowMs());
		for (MediaRef old : budget.evict(nowMs(), PROTECT_MS)) {
			release(old);
		}
	}

	private void release(MediaRef ref) {
		Entry e = entries.remove(ref);
		if (e != null && e.drawable != null) {
			mc.getTextureManager().release(e.drawable.texture());
		}
	}
}
