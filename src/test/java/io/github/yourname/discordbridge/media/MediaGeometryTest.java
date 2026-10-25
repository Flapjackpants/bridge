package io.github.yourname.discordbridge.media;

import static org.junit.jupiter.api.Assertions.*;

import io.github.yourname.discordbridge.media.MediaGeometry.Box;
import io.github.yourname.discordbridge.media.MediaGeometry.Sheet;
import org.junit.jupiter.api.Test;

class MediaGeometryTest {
	@Test
	void sheetGrid() {
		Sheet s = Sheet.of(256, 256, 64, 4096);
		assertEquals(16, s.cols());
		assertEquals(4, s.rows());
		assertEquals(64, s.frames());
		assertEquals(4096, s.width());
		assertEquals(1024, s.height());
		assertEquals(256, s.u(17));
		assertEquals(256, s.v(17));
	}

	@Test
	void sheetCapsFrames() {
		Sheet s = Sheet.of(100, 100, 1000, 1000);
		assertEquals(100, s.frames());
	}

	@Test
	void sheetRejectsOversizeFrame() {
		assertThrows(IllegalArgumentException.class, () -> Sheet.of(5000, 10, 1, 4096));
		assertThrows(IllegalArgumentException.class, () -> Sheet.of(10, 5000, 1, 4096));
		assertThrows(IllegalArgumentException.class, () -> Sheet.of(0, 10, 1, 4096));
		assertThrows(IllegalArgumentException.class, () -> Sheet.of(10, 10, 0, 4096));
	}

	@Test
	void boxFit() {
		assertEquals(new Box(160, 80), Box.fit(1024, 512, 160, 90));
		assertEquals(new Box(100, 50), Box.fit(100, 50, 160, 90));
		assertEquals(new Box(45, 45), Box.fit(0, 0, 160, 90));
		assertEquals(new Box(1, 90), Box.fit(1, 1000, 160, 90));
	}
}
