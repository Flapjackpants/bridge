package io.github.yourname.discordbridge.media;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class LruBudgetTest {
	@Test
	void evictsLruSkipsProtected() {
		LruBudget<String> b = new LruBudget<>(100);
		b.put("a", 60, 0);
		b.put("b", 60, 10);
		b.touch("a", 20);
		assertEquals(120, b.used());
		assertEquals(List.of("b"), b.evict(1000, 100));
		assertEquals(60, b.used());

		LruBudget<String> p = new LruBudget<>(10);
		p.put("x", 60, 995);
		assertEquals(List.of(), p.evict(1000, 100));
		assertEquals(60, p.used());
	}

	@Test
	void replaceUpdatesUsed() {
		LruBudget<String> b = new LruBudget<>(100);
		b.put("a", 10, 0);
		b.put("a", 30, 1);
		assertEquals(30, b.used());
		b.remove("a");
		assertEquals(0, b.used());
		b.touch("zzz", 5);
	}
}
