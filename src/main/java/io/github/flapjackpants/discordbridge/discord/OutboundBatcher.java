package io.github.flapjackpants.discordbridge.discord;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OutboundBatcher {
	private static final Logger LOG = LoggerFactory.getLogger("discordbridge");
	private static final int MAX_CONTENT = 2000;

	/** authorName null = send as the bot. */
	public record Outbound(@Nullable String authorName, @Nullable String avatarUrl, String content) {}

	private final int maxPending;
	private final ArrayDeque<Outbound> pending = new ArrayDeque<>();
	private long dropped;

	public OutboundBatcher(int maxPending) {
		this.maxPending = maxPending;
	}

	public void enqueue(Outbound o) {
		synchronized (pending) {
			if (pending.size() >= maxPending) {
				pending.pollFirst();
				dropped++;
			}
			pending.addLast(o);
		}
	}

	List<Outbound> drainMerged() {
		List<Outbound> drained;
		synchronized (pending) {
			drained = new ArrayList<>(pending);
			pending.clear();
		}
		List<Outbound> out = new ArrayList<>();
		Outbound cur = null;
		for (Outbound next : drained) {
			if (cur != null
					&& Objects.equals(cur.authorName(), next.authorName())
					&& Objects.equals(cur.avatarUrl(), next.avatarUrl())
					&& cur.content().length() + 1 + next.content().length() <= MAX_CONTENT) {
				cur = new Outbound(cur.authorName(), cur.avatarUrl(), cur.content() + "\n" + next.content());
			} else {
				if (cur != null) {
					out.add(cur);
				}
				cur = next;
			}
		}
		if (cur != null) {
			out.add(cur);
		}
		return out;
	}

	public void start(ScheduledExecutorService ses, Duration interval, Consumer<Outbound> sink) {
		ses.scheduleWithFixedDelay(() -> {
			try {
				deliver(drainMerged(), sink);
			} catch (Throwable t) {
				LOG.warn("Outbound batch failed", t);
			}
		}, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
	}

	public void flushNow(Consumer<Outbound> sink) {
		deliver(drainMerged(), sink);
	}

	public long droppedCount() {
		synchronized (pending) {
			return dropped;
		}
	}

	private static void deliver(List<Outbound> items, Consumer<Outbound> sink) {
		for (Outbound o : items) {
			try {
				sink.accept(o);
			} catch (Exception e) {
				LOG.warn("Failed to deliver outbound message", e);
			}
		}
	}
}
