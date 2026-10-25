package io.github.yourname.discordbridge.media;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Not thread-safe: render thread only. */
public final class LruBudget<K> {
	private final long budget;
	private final LinkedHashMap<K, long[]> entries = new LinkedHashMap<>(16, 0.75f, true);
	private long used;

	public LruBudget(long budgetBytes) {
		this.budget = budgetBytes;
	}

	public void put(K key, long bytes, long nowMs) {
		long[] old = entries.put(key, new long[] {bytes, nowMs});
		used += bytes - (old == null ? 0 : old[0]);
	}

	public void touch(K key, long nowMs) {
		long[] e = entries.get(key);
		if (e != null) {
			e[1] = nowMs;
		}
	}

	public void remove(K key) {
		long[] old = entries.remove(key);
		if (old != null) {
			used -= old[0];
		}
	}

	public long used() {
		return used;
	}

	/** Removes and returns least-recently-touched keys until used() <= budget, skipping recently touched keys. */
	public List<K> evict(long nowMs, long protectMs) {
		List<K> out = new ArrayList<>();
		Iterator<Map.Entry<K, long[]>> it = entries.entrySet().iterator();
		while (used > budget && it.hasNext()) {
			Map.Entry<K, long[]> e = it.next();
			if (e.getValue()[1] >= nowMs - protectMs) {
				continue;
			}
			used -= e.getValue()[0];
			out.add(e.getKey());
			it.remove();
		}
		return out;
	}
}
