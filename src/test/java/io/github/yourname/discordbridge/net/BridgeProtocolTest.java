package io.github.yourname.discordbridge.net;

import static org.junit.jupiter.api.Assertions.*;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class BridgeProtocolTest {
	@Test
	void negotiate() {
		assertEquals(OptionalInt.empty(), BridgeProtocol.negotiate(-1));
		assertEquals(OptionalInt.empty(), BridgeProtocol.negotiate(0));
		assertEquals(1, BridgeProtocol.negotiate(1).getAsInt());
		assertEquals(1, BridgeProtocol.negotiate(99).getAsInt());
	}
}
