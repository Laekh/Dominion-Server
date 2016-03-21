package net.ultradev.dominion.game;

import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONObject;
import net.ultradev.dominion.game.utils.Utils;

public class GameConfig {
	
	public enum Option { PLAYERS, ADDCARD, REMOVECARD };
	
	int players;
	List<String> actionCardTypes;
	
	public GameConfig() {
		//Default values
		this.players = 2;
		this.actionCardTypes = new ArrayList<>();
	}
	
	public void handle(String key, String value) {
		Option option = Option.valueOf(key.toUpperCase());
		switch(option) {
			case PLAYERS:
				try {
					setPlayerCount(Integer.parseInt(value));
				} catch(Exception ignored) {}
				break;
			case ADDCARD:
				addActionCard(value);
				break;
			case REMOVECARD:
				removeActionCard(value);
				break;
			default:
				break;
		}
	}
	
	public void setPlayerCount(int players) {
		this.players = players;
		Utils.debug("Local game has set playercount to " + players);
	}
	
	public void addActionCard(String actionCard) {
		if(actionCardTypes.size() <= 10)
			actionCardTypes.add(actionCard);
	}
	
	public void removeActionCard(String actionCard) {
		if(actionCardTypes.contains(actionCard))
			actionCardTypes.remove(actionCard);
	}
	
	public void assignRandomActionCards() {
		//TODO Geef 10 random kaart types vanuit db
	}
	
	public int getPlayerCount() {
		return players;
	}
	
	public List<String> getActionCards() {
		return actionCardTypes;
	}
	
	public JSONObject getAsJson() {
		return new JSONObject()
				.accumulate("players", getPlayerCount())
				.accumulate("actionCards", getActionCards());
	}

}