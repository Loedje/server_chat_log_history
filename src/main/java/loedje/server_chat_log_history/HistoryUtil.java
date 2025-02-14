package loedje.server_chat_log_history;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;



/**
 * Utility class for handling player message log history in a Minecraft server.
 * Very likely not perfect.
 */
public class HistoryUtil {
	private static final ArrayDeque<String> previousHistory = new ArrayDeque<>();
	private static final ArrayDeque<Text> latestHistory = new ArrayDeque<>();

	private static final String[] death_message_list = {
			" was ", // saves me time but might cause problems?
			" drowned",
			" died",
			" experienced kinetic energy",
			" blew up",
			" hit the ground too hard",
			" fell",
			" went up in flames",
			" walked ",
			" burned ",
			" went off with a bang",
			" tried to swim in lava",
			" to death",
			" suffocated in a wall",
			" left the confines of this world",
			" didn't want to live in the same world as ",
			" withered "
	};
	private static final String[] blacklist = {
			", message:",
			"lost connection: ",
			"Exception",
			"<--[HERE]" // invalid command
	};
	private static final String SERVER_INFO = "[Server thread/INFO]";

	private HistoryUtil() {
		throw new IllegalStateException("Utility class");
	}

	private static final Set<ServerPlayerEntity> players = new HashSet<>();

	/**
	 * Death messages, join/leave messages, and advancement messages from current session
	 * @param message message to be saved
	 */
	public static void handleGameMessage(Text message) {
		latestHistory.push(message);
	}

	/**
	 * Chat messages from player or command messages like /me and /say, from the current session
	 * @param message message to be saved
	 * @param params message type
	 */
	public static void handleChatMessage(SignedMessage message, MessageType.Parameters params) {
		latestHistory.push(params.applyChatDecoration(message.getContent()));
	}
	/**
	 * Saves the chat log history stored in gz files. previousHistory can be cloned and used every
	 * time these logs are needed.
	 */
	public static void setPreviousHistory() {
		File logs = new File(FabricLoader.getInstance().getGameDir().toFile(),
				ServerChatLogHistory.getConfig().getLogFolder());

		File[] files = logs.listFiles((dir, name) -> name.toLowerCase().endsWith(".gz")
				&& !name.startsWith("debug"));
		// Sort files by date
		if (files == null) return;
		Arrays.sort(files);

		for (File file : files) {
			readGzFile(file);
		}
	}
	/**
	 * Checks for new players in the world and triggers the history retrieval for any new players.
	 *
	 * @param world The server world.
	 */
	public static void tick(ServerWorld world) {
		world.getPlayers().stream().filter(
				p -> !players.contains(p)).findAny().ifPresent(HistoryUtil::handlePlayerJoin);
		players.addAll(world.getPlayers());
	}
	/**
	 * Retrieves and prints the player's history upon joining the server, if they are operator.
	 *
	 * @param player The player who joined.
	 */
	private static void handlePlayerJoin(ServerPlayerEntity player) {
		if (!player.hasPermissionLevel(2) && ServerChatLogHistory.getConfig().isOperatorRequired())
			return;

		Deque<String> history = previousHistory.clone();

		printHistory(player, history);
	}

	/**
	 * Queues chat log history from gz files.
	 *
	 * @param gzFile The gz file containing the log
	 */
	private static void readGzFile(File gzFile) {
		try (FileInputStream fileInputStream = new FileInputStream(gzFile);
			 GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
			 BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gzipInputStream))) {

			readLines(bufferedReader);
		} catch (IOException e) {
			ServerChatLogHistory.LOGGER.error("Error reading file: " + e.getMessage(), e);
		}
	}

	/**
	 * Read all lines in file and add to stack.
	 * Change lines containing (Minecraft)
	 *
	 * @param br BufferedReader
	 * @throws IOException when line can not be read.
	 */
	private static void readLines(BufferedReader br) throws IOException {
		String line;
		while ((line = br.readLine()) != null) {
			line = line.replace(" (Minecraft) ",": ");
			if (!inList(line, blacklist) && line.contains(SERVER_INFO)) {
				((Deque<String>) HistoryUtil.previousHistory).push(line);
			}

		}
	}
	/**
	 * Prints the player's history messages to the player's client.
	 * If maxMessages is >= 0 it only prints that amount of most recent message history.
	 *
	 * @param player  The player whose history is being printed.
	 * @param history The deque containing the player's history messages.
	 */
	private static void printHistory(ServerPlayerEntity player, Deque<String> history) {
		player.sendMessageToClient(Text.literal("End of this server's messages.")
				.formatted(Formatting.GRAY, Formatting.ITALIC), false);
		int maxMessages = ServerChatLogHistory.getConfig().getMaxMessages();
		if (maxMessages > 0) {
			Deque<String> historyLimited = new ArrayDeque<>();
			for (int i = 0; i < maxMessages; i++) {
				historyLimited.addLast(history.removeFirst());
				if (history.isEmpty()) break;
			}
			history = historyLimited;
		}
		while (!history.isEmpty()) {
			String message = history.removeLast();
			sendMessage(player, message);
		}

		while (!latestHistory.isEmpty()) {
			player.sendMessageToClient(latestHistory.removeLast(), false);
		}
	}
	/**
	 * Send (styled) message to player if conditions apply
	 * @param player Player who logged on
	 * @param message message to be checked for conditions
	 */
	private static void sendMessage(ServerPlayerEntity player, String message) {
		if (message.contains("[Server thread/INFO]: <")
				|| message.contains("[Server thread/INFO] [Not Secure]: <")) { // need to test this
			// Normal message
			player.sendMessageToClient(Text.literal(message.substring(message.indexOf('<'))),
					false);
		} else if (message.contains("[Server thread/INFO]: * ")
				|| message.contains("[Server thread/INFO] [Not Secure]: * ")) {
			// Message from /me command
			player.sendMessageToClient(Text.literal(message.substring(message.indexOf('*'))),
					false);
		} else {
			sendAnnouncementMessage(player, message);
		}
	}

	/**
	 * Send messages such as players joining, advancements and challenges and death messages
	 * @param player Player who logged on
	 * @param message message to be checked for conditions
	 */
	private static void sendAnnouncementMessage(ServerPlayerEntity player, String message) {
		message = message.substring(message.indexOf(": ") + 2);
		if ((message.contains(" joined the game") || message.contains(" left the game"))
				&& !message.contains("<")
				&& !message.contains("*")) {
			// Player joined the game message
			player.sendMessageToClient(Text.literal(
							message)
					.formatted(Formatting.YELLOW), false);
		} else if (message.contains(" has completed the challenge ")) {
			// Advancements and challenges
			String[] split = message.split("\\[");
			player.sendMessageToClient(Text.literal(split[split.length - 2])
					.append(Text.literal("[" + split[split.length - 1])
							.formatted(Formatting.DARK_PURPLE)), false);
		} else if (message.contains(" has made the advancement ")) {
			String[] split = message.split("\\[");
			player.sendMessageToClient(Text.literal(split[split.length - 2])
					.append(Text.literal("[" + split[split.length - 1])
							.formatted(Formatting.GREEN)), false);
		} else if (inList(message, death_message_list)
				&& !message.contains(", message: ")) {
			// Death messages
			player.sendMessageToClient(Text.literal(message),
			false);
		}
	}

	/**
	 * To check if a message is in white or blacklist
	 * @param message Message to check
	 * @param list Whitelist or blacklist
	 * @return true if it is in there, false if it is not
	 */
	private static boolean inList(String message, String[] list) {
		for (String entry : list) {
			if (message.contains(entry)) return true;
		}
		return false;
	}
}
