package io.github.flapjackpants.discordbridge.discord;

import static org.junit.jupiter.api.Assertions.*;

import io.github.flapjackpants.discordbridge.discord.OutboundBatcher.Outbound;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboundBatcherTest {
	private static Outbound o(String a, String c) {
		return new Outbound(a, "av", c);
	}

	@Test
	void mergesConsecutive() {
		OutboundBatcher b = new OutboundBatcher(200);
		b.enqueue(o("A", "one"));
		b.enqueue(o("A", "two"));
		List<Outbound> out = b.drainMerged();
		assertEquals(1, out.size());
		assertEquals("one\ntwo", out.get(0).content());
		assertTrue(b.drainMerged().isEmpty());
	}

	@Test
	void keepsOrder() {
		OutboundBatcher b = new OutboundBatcher(200);
		b.enqueue(o("A", "1"));
		b.enqueue(o("B", "2"));
		b.enqueue(o("A", "3"));
		assertEquals(3, b.drainMerged().size());
	}

	@Test
	void respects2000Limit() {
		OutboundBatcher b = new OutboundBatcher(200);
		b.enqueue(o("A", "a".repeat(1500)));
		b.enqueue(o("A", "b".repeat(1000)));
		List<Outbound> out = b.drainMerged();
		assertEquals(2, out.size());
		assertTrue(out.stream().allMatch(x -> x.content().length() <= 2000));
	}

	@Test
	void dropsOldest() {
		OutboundBatcher b = new OutboundBatcher(2);
		b.enqueue(o("A", "1"));
		b.enqueue(o("B", "2"));
		b.enqueue(o("C", "3"));
		assertEquals(1, b.droppedCount());
		List<Outbound> out = b.drainMerged();
		assertEquals(List.of("2", "3"), out.stream().map(Outbound::content).toList());
	}

	@Test
	void flushNowDeliversOnCallingThread() {
		OutboundBatcher b = new OutboundBatcher(10);
		b.enqueue(new Outbound(null, null, "x"));
		StringBuilder sb = new StringBuilder();
		b.flushNow(x -> sb.append(x.content()));
		assertEquals("x", sb.toString());
	}
}
