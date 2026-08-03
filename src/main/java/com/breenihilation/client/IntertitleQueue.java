package com.breenihilation.client;

// Batches nearby player messages into a small intertitle queue.
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class IntertitleQueue {
	private static final int MAX_QUEUE_SIZE = 3;
	private static final int DUPLICATE_WINDOW_TICKS = 20;
	private static final int CHAT_BATCH_WINDOW_TICKS = 40;
	private static final int CHAT_CARD_COOLDOWN_TICKS = 50;
	private static final int CHAT_MIN_DURATION_TICKS = 26;
	private static final int CHAT_MAX_DURATION_TICKS = 80;

	private final Deque<IntertitleEntry> queued = new ArrayDeque<>();
	private final Map<UUID, ChatSenderState> chatSenders = new HashMap<>();
	private IntertitleEntry active;
	private int activeTicks;
	private int activeDurationTicks;
	private long tick;
	private String lastFingerprint;
	private long lastEnqueueTick = Long.MIN_VALUE;

	public void tick(int durationTicks) {
		tick++;
		if (active != null) {
			activeTicks--;
			if (activeTicks <= 0) {
				active = null;
			}
		}

		flushChatBatches(durationTicks);
		if (active == null && !queued.isEmpty()) {
			active = queued.removeFirst();
			activeDurationTicks = Math.max(1, active.durationTicks() > 0 ? active.durationTicks() : durationTicks);
			activeTicks = activeDurationTicks;
		}
	}

	public boolean enqueue(String heading, String body, int durationTicks) {
		if (heading == null || heading.isBlank() || body == null || body.isBlank()) {
			return false;
		}

		String fingerprint = heading + "\u0000" + body;
		if (fingerprint.equals(lastFingerprint) && tick - lastEnqueueTick < DUPLICATE_WINDOW_TICKS) {
			return false;
		}
		if (queued.size() >= MAX_QUEUE_SIZE) {
			return false;
		}

		lastFingerprint = fingerprint;
		lastEnqueueTick = tick;
		queued.addLast(new IntertitleEntry(heading, body, null, durationTicks));
		return true;
	}

	public boolean enqueueChat(UUID senderId, String sender, String message) {
		if (senderId == null || sender == null || sender.isBlank() || message == null || message.isBlank()) {
			return false;
		}

		String normalized = normalizeChatMessage(message);
		if (normalized.isBlank()) {
			return false;
		}

		ChatSenderState state = chatSenders.computeIfAbsent(senderId, ignored -> new ChatSenderState(sender));
		state.sender = sender;
		if (normalized.equals(state.lastMessage) && tick - state.lastMessageTick < DUPLICATE_WINDOW_TICKS) {
			return false;
		}
		state.lastMessage = normalized;
		state.lastMessageTick = tick;

		// Multiple messages arriving in the same client tick belong to the same
		// immediate card rather than becoming duplicate takeovers.
		if (state.lastCardTick == tick && appendToQueuedChat(senderId, normalized)) {
			return true;
		}

		if (state.pendingBody != null) {
			state.pendingBody = combineChatMessages(state.pendingBody, normalized);
			return true;
		}

		if (state.lastCardTick != Long.MIN_VALUE
				&& tick - state.lastCardTick < CHAT_CARD_COOLDOWN_TICKS) {
			state.pendingBody = normalized;
			return true;
		}

		if (enqueueChatCard(senderId, sender, normalized)) {
			state.lastCardTick = tick;
			return true;
		}
		return false;
	}

	public static int chatDurationTicks(String message) {
		int length = message == null ? 0 : message.length();
		return Math.min(CHAT_MAX_DURATION_TICKS,
				Math.max(CHAT_MIN_DURATION_TICKS, CHAT_MIN_DURATION_TICKS + (int) Math.ceil(length * 0.5f)));
	}

	public IntertitleEntry active() {
		return active;
	}

	public int queuedCount() {
		return queued.size();
	}

	public float activeProgress() {
		if (active == null || activeDurationTicks <= 0) {
			return 1.0f;
		}

		return 1.0f - (activeTicks / (float) activeDurationTicks);
	}

	public int activeRemainingTicks() {
		return activeTicks;
	}

	public void clear() {
		queued.clear();
		active = null;
		activeTicks = 0;
		activeDurationTicks = 0;
		lastFingerprint = null;
		lastEnqueueTick = Long.MIN_VALUE;
		chatSenders.clear();
	}

	private void flushChatBatches(int durationTicks) {
		for (Map.Entry<UUID, ChatSenderState> entry : chatSenders.entrySet()) {
			ChatSenderState state = entry.getValue();
			if (state.pendingBody == null
					|| tick - state.lastMessageTick < CHAT_BATCH_WINDOW_TICKS
					|| tick - state.lastCardTick < CHAT_CARD_COOLDOWN_TICKS) {
				continue;
			}

			if (enqueueChatCard(entry.getKey(), state.sender, state.pendingBody)) {
				state.lastCardTick = tick;
				state.pendingBody = null;
			}
		}
	}

	private boolean enqueueChatCard(UUID senderId, String sender, String message) {
		if (queued.size() >= MAX_QUEUE_SIZE) {
			// Chat is the only card source for now. Drop the oldest queued card
			// rather than allowing a distant conversation to build a backlog.
			queued.removeFirst();
		}
		queued.addLast(new IntertitleEntry(sender, message, senderId));
		return true;
	}

	private boolean appendToQueuedChat(UUID senderId, String message) {
		IntertitleEntry[] entries = queued.toArray(new IntertitleEntry[0]);
		for (int index = entries.length - 1; index >= 0; index--) {
			IntertitleEntry entry = entries[index];
			if (senderId.equals(entry.senderId())) {
				String combined = combineChatMessages(entry.body(), message);
				entries[index] = new IntertitleEntry(entry.heading(), combined, senderId, chatDurationTicks(combined));
				queued.clear();
				for (IntertitleEntry replacement : entries) {
					queued.addLast(replacement);
				}
				return true;
			}
		}
		return false;
	}

	private static String normalizeChatMessage(String message) {
		return message.replaceAll("\\s+", " ").trim();
	}

	private static String combineChatMessages(String first, String second) {
		return normalizeChatMessage(first + " " + second);
	}

	private static final class ChatSenderState {
		private String sender;
		private String lastMessage;
		private String pendingBody;
		private long lastMessageTick = Long.MIN_VALUE;
		private long lastCardTick = Long.MIN_VALUE;

		private ChatSenderState(String sender) {
			this.sender = sender;
		}
	}

	public record IntertitleEntry(String heading, String body, UUID senderId, int durationTicks) {
		public IntertitleEntry(String heading, String body) {
			this(heading, body, null, 0);
		}

		public IntertitleEntry(String heading, String body, UUID senderId) {
			this(heading, body, senderId, 0);
		}
	}
}
