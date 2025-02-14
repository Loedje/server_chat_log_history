package loedje.server_chat_log_history;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerChatLogHistory implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("server_chat_log_history");

	private static Config config = new Config();

	@Override
	public void onInitialize() {
		config.init();
		HistoryUtil.setPreviousHistory();
		ServerTickEvents.END_WORLD_TICK.register(HistoryUtil::tick);
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
				HistoryUtil.handleChatMessage(message, params));
		ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) ->
				HistoryUtil.handleChatMessage(message, params));
		ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) ->
				HistoryUtil.handleGameMessage(message));
	}

	public static Config getConfig() {
		return config;
	}

	public static void setConfig(Config config) {
		ServerChatLogHistory.config = config;
	}


}