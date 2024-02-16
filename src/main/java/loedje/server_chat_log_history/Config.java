package loedje.server_chat_log_history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Modifier;

public class Config {
	public static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("server_chat_log_history.json").toFile();
	private int maxMessages = -1;

	public int getMaxMessages() {
		return maxMessages;
	}

	public void init() {
		try {
			if (FILE.createNewFile()) {
				write();
				return;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		read();
	}

	private void write() {
		try(FileWriter fileWriter = new FileWriter(FILE)) {
			new GsonBuilder()
					.excludeFieldsWithModifiers(Modifier.STATIC)
					.setPrettyPrinting()
					.create()
					.toJson(ServerChatLogHistory.getConfig(),
							ServerChatLogHistory.getConfig().getClass(),
							fileWriter);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void read() {
		try (FileReader fileReader = new FileReader(FILE)) {
			ServerChatLogHistory.setConfig(new Gson().fromJson(
					fileReader,
					ServerChatLogHistory.getConfig().getClass()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
