package io.github.yourname.discordbridge.media;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MediaLayoutTest {
	private static MediaDecoder.Frames frames(int w, int h, List<int[]> argb) {
		return new MediaDecoder.Frames(w, h, argb, new int[argb.size()], argb.size() > 1);
	}

	@Test
	void rowsRoundUp() {
		assertEquals(10, MediaLayout.rows(90, 9));
		assertEquals(11, MediaLayout.rows(91, 9));
	}

	@Test
	void rowsClampInputs() {
		assertEquals(1, MediaLayout.rows(0, 9));
		assertEquals(10, MediaLayout.rows(10, 0));
	}

	@Test
	void frameAtLoops() {
		int[] d = {100, 200, 300};
		long[] t = {0, 99, 100, 299, 300, 599, 600};
		int[] want = {0, 0, 1, 1, 2, 2, 0};
		for (int i = 0; i < t.length; i++) {
			assertEquals(want[i], MediaLayout.frameAt(d, t[i]), "t=" + t[i]);
		}
	}

	@Test
	void frameAtDegenerate() {
		assertEquals(0, MediaLayout.frameAt(new int[] {100, 200}, -5));
		assertEquals(0, MediaLayout.frameAt(new int[] {50}, 12345));
		assertEquals(0, MediaLayout.frameAt(new int[] {}, 12345));
	}

	@Test
	void frameAtZeroDelay() {
		assertEquals(1, MediaLayout.frameAt(new int[] {0, 0}, 150));
	}

	@Test
	void packSheetSingleRow() {
		MediaDecoder.Frames f = frames(2, 1, List.of(new int[] {1, 2}, new int[] {3, 4}));
		int[] out = MediaLayout.packSheet(f, MediaGeometry.Sheet.of(2, 1, 2, 4));
		assertArrayEquals(new int[] {1, 2, 3, 4}, out);
	}

	@Test
	void packSheetWraps() {
		MediaDecoder.Frames f = frames(2, 2,
				List.of(new int[] {1, 1, 1, 1}, new int[] {2, 2, 2, 2}, new int[] {3, 3, 3, 3}));
		MediaGeometry.Sheet s = MediaGeometry.Sheet.of(2, 2, 3, 4);
		assertEquals(0, s.u(2));
		assertEquals(2, s.v(2));
		int[] out = MediaLayout.packSheet(f, s);
		assertEquals(16, out.length);
		assertEquals(3, out[2 * 4 + 0]);
		assertEquals(3, out[3 * 4 + 1]);
		assertEquals(0, out[2 * 4 + 2]);
		assertEquals(0, out[3 * 4 + 3]);
	}

	@Test
	void packSheetRejectsMismatch() {
		MediaDecoder.Frames f = frames(3, 1, List.of(new int[] {1, 2, 3}));
		MediaGeometry.Sheet s = MediaGeometry.Sheet.of(2, 1, 1, 4);
		assertThrows(IllegalArgumentException.class, () -> MediaLayout.packSheet(f, s));
	}

	@Test
	void entryHeightMatchesVanilla() {
		assertEquals(9, MediaLayout.entryHeight(0.0));
		assertEquals(13, MediaLayout.entryHeight(0.5));
		assertEquals(18, MediaLayout.entryHeight(1.0));
	}

	@Test
	void rowTopMatchesVanilla() {
		assertEquals(99, MediaLayout.rowTop(100, 0.0));
		assertEquals(97, MediaLayout.rowTop(100, 0.5));
		assertEquals(94, MediaLayout.rowTop(100, 1.0));
	}

	@Test
	void sliceScalesSource() {
		assertEquals(new MediaLayout.Slice(9, 0, 18), MediaLayout.slice(0, 9, 90, 180));
		assertEquals(new MediaLayout.Slice(9, 162, 18), MediaLayout.slice(9, 9, 90, 180));
	}

	@Test
	void slicePartialLastRow() {
		assertEquals(new MediaLayout.Slice(2, 18, 2), MediaLayout.slice(2, 9, 20, 20));
	}

	@Test
	void sliceBeyondBox() {
		assertEquals(new MediaLayout.Slice(0, 0, 0), MediaLayout.slice(10, 9, 90, 180));
	}

	@Test
	void sliceDegenerate() {
		assertEquals(new MediaLayout.Slice(0, 0, 0), MediaLayout.slice(0, 9, 0, 10));
		assertEquals(new MediaLayout.Slice(0, 0, 0), MediaLayout.slice(0, 9, 10, 0));
	}
}
