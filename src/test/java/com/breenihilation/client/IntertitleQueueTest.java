package com.breenihilation.client;

// Verifies intertitle batching, queue limits, and expiry.
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IntertitleQueueTest {
	@Test
	void promotesQueuedCardsAndExpiresActiveCard() {
		IntertitleQueue queue = new IntertitleQueue();
		assertTrue(queue.enqueue("A", "First", 2));
		queue.tick(2);
		assertEquals("A", queue.active().heading());
		queue.tick(2);
		queue.tick(2);
		assertNull(queue.active());
	}

	@Test
	void suppressesImmediateDuplicatesAndCapsQueue() {
		IntertitleQueue queue = new IntertitleQueue();
		assertTrue(queue.enqueue("A", "Same", 20));
		assertFalse(queue.enqueue("A", "Same", 20));
		queue.tick(20);
		for (int i = 0; i < 3; i++) {
			assertTrue(queue.enqueue("H" + i, "Body", 20));
		}
		assertFalse(queue.enqueue("Overflow", "Body", 20));
		assertEquals(3, queue.queuedCount());
	}

	@Test
	void batchesChatMessagesAndUsesCharacterDuration() {
		IntertitleQueue queue = new IntertitleQueue();
		UUID sender = UUID.randomUUID();
		assertTrue(queue.enqueueChat(sender, "Breen", "Hello"));
		queue.tick(80);
		assertEquals("Hello", queue.active().body());

		assertTrue(queue.enqueueChat(sender, "Breen", "Come here"));
		assertTrue(queue.enqueueChat(sender, "Breen", "I found something"));
		for (int tick = 0; tick < 80; tick++) {
			queue.tick(80);
		}

		assertEquals("Come here I found something", queue.active().body());
		assertEquals(0, queue.queuedCount());
		assertEquals(80, IntertitleQueue.chatDurationTicks("a".repeat(200)));
	}

	@Test
	void clearRemovesActiveAndQueuedCards() {
		IntertitleQueue queue = new IntertitleQueue();
		queue.enqueue("A", "First", 20);
		queue.tick(20);
		queue.enqueue("B", "Second", 20);
		queue.clear();
		assertNull(queue.active());
		assertEquals(0, queue.queuedCount());
	}

	@Test
	void preservesCompleteLongAndCombinedChatMessages() {
		IntertitleQueue queue = new IntertitleQueue();
		UUID sender = UUID.randomUUID();
		String first = "a".repeat(256);
		String second = "b".repeat(256);

		assertTrue(queue.enqueueChat(sender, "Breen", first));
		assertTrue(queue.enqueueChat(sender, "Breen", second));
		queue.tick(80);

		assertEquals(first + " " + second, queue.active().body());
		assertFalse(queue.active().body().endsWith("..."));
	}
}
