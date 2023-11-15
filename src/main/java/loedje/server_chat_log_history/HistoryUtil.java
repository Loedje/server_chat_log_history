package loedje.server_chat_log_history;

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

	private static final String[] whitelist = {
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
			"Exception"
	};
	public static final String SERVER_INFO = "[Server thread/INFO]";

	private HistoryUtil() {
		throw new IllegalStateException("Utility class");
	}

	private static final Set<ServerPlayerEntity> players = new HashSet<>();
	/**
	 * Checks for new players in the world and triggers the history retrieval
	 * for any new players.
	 *
	 * @param world The server world.
	 */
	public static void tick(ServerWorld world) {
		world.getPlayers().stream().filter(
				p -> !players.contains(p)).findAny().ifPresent(player -> handlePlayerJoin(world, player));
		players.addAll(world.getPlayers());
	}
	/**
	 * Retrieves and prints the player's history upon joining the server, if they are operator.
	 *
	 * @param world  The server world.
	 * @param player The player who joined.
	 */
	private static void handlePlayerJoin(ServerWorld world, ServerPlayerEntity player) {
		if (!player.hasPermissionLevel(2)) return;

		File logs = new File(world.getServer().getRunDirectory(), "logs");

		File[] files = logs.listFiles((dir, name) -> name.toLowerCase().endsWith(".gz")
				&& !name.startsWith("debug"));
		// Sort files by date
		if (files == null) return;
		Arrays.sort(files);
		Deque<String> history = new ArrayDeque<>();

		for (File file : files) {
			readGzFile(file, history);
		}

		File latest = new File(world.getServer().getRunDirectory(), "logs/latest.log");
		try (BufferedReader br = new BufferedReader(new FileReader(latest))){
			readLines(history, br);
		} catch (IOException e) {
			ServerChatLogHistory.LOGGER.error("Error reading file: " + e.getMessage(), e);
		}

		printHistory(player, history);
	}

	/**
	 * Queues chat log history from gz files.
	 *
	 * @param gzFile The gz file containing the log
	 * @param history The deque containing the player's history messages.
	 */
	private static void readGzFile(File gzFile, Deque<String> history) {
		try (FileInputStream fileInputStream = new FileInputStream(gzFile);
			 GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
			 BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gzipInputStream))) {

			readLines(history, bufferedReader);
		} catch (IOException e) {
			ServerChatLogHistory.LOGGER.error("Error reading file: " + e.getMessage(), e);
		}
	}

	/**
	 * Read all lines in file and add to stack.
	 * Change lines containing (Minecraft)
	 * @param history The deque containing the player's history messages
	 * @param br BufferedReader
	 * @throws IOException when line can not be read.
	 */
	private static void readLines(Deque<String> history, BufferedReader br) throws IOException {
		String line;
		while ((line = br.readLine()) != null) {
			line = line.replace(" (Minecraft) ",": ");
			history.push(line);
		}
	}
	/**
	 * Prints the player's history messages to the player's client.
	 *
	 * @param player  The player whose history is being printed.
	 * @param history The deque containing the player's history messages.
	 */
	private static void printHistory(ServerPlayerEntity player, Deque<String> history) {
		player.sendMessageToClient(Text.literal("No more logged messages")
				.formatted(Formatting.GRAY, Formatting.ITALIC), false);
		while (!history.isEmpty()) {
			String message = history.removeLast();
			sendMessage(player, message);
		}
	}
	/**
	 * Send (styled) message to player if conditions apply
	 * @param player Player who logged on
	 * @param message message to be checked for conditions
	 */
	private static void sendMessage(ServerPlayerEntity player, String message) {
		if (!inList(message, blacklist) && message.contains(SERVER_INFO)) {
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
			} else if ((message.contains(" joined the game") || message.contains(" left the game"))
					&& !message.contains("<")
					&& !message.contains("*")) {
				// Player joined the game message
				player.sendMessageToClient(Text.literal(
								message.substring(message.indexOf(": ") + 2))
						.formatted(Formatting.YELLOW), false);
			} else if (message.contains(" has completed the challenge ")) {
				// Advancements and challenges
				message = message.substring(message.indexOf(": ") + 2);
				String[] split = message.split("\\[");
				player.sendMessageToClient(Text.literal(split[split.length - 2])
						.append(Text.literal("[" + split[split.length - 1])
								.formatted(Formatting.DARK_PURPLE)), false);
			} else if (message.contains(" has made the advancement ")) {
				message = message.substring(message.indexOf(": ") + 2);
				String[] split = message.split("\\[");
				player.sendMessageToClient(Text.literal(split[split.length - 2])
						.append(Text.literal("[" + split[split.length - 1])
								.formatted(Formatting.GREEN)), false);
			} else if (inList(message, whitelist)
					&& !message.contains(", message: ")) {
				// Death messages
				player.sendMessageToClient(Text.literal(message.substring(message.indexOf(": ") + 2)),
				false);
			}
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
