package net.ultradev.dominion.game; 

import net.sf.json.JSONObject;
import net.ultradev.dominion.game.card.Card;
import net.ultradev.dominion.game.card.Card.CardType;
import net.ultradev.dominion.game.card.CardManager;
import net.ultradev.dominion.game.card.action.Action;
import net.ultradev.dominion.game.card.action.ActionResult;
import net.ultradev.dominion.game.player.Player;
import net.ultradev.dominion.game.player.Player.Pile; 

public class Turn {

	public enum CardDestination { TOP_DECK, DECK, DISCARD, HAND }
	public enum Phase { ACTION, BUY, CLEANUP };
	private enum BuyResponse { CANTAFFORD, BOUGHT };
	
	private Game game;
	private Player player;
	private int buycount;
	private int actioncount;
	private int buypower;
	private int buypowerMultiplier;
	private Phase phase;
	
	Card activeCard;
	Action activeAction;
	Action triggerAction; // For actions like multiple actions that need an overhead over all other actions

	/**
	 * Represents a turn for a player in a game
	 * @param game The game
	 * @param player The player who's turn it is
	 */
	public Turn(Game game, Player player) {
		this.game = game;
		this.player = player;
		this.buycount = 1;
		this.actioncount = 1;
		this.buypower = 0;
		this.buypowerMultiplier = 1;
		this.phase = Phase.ACTION;
	}
	
	/**
	 * @return The game the turn is in
	 */
	public Game getGame() {
		return game;
	}
	
	/**
	 * @return The player who's turn it is
	 */
	public Player getPlayer() {
		return player;
	}
	
	/**
	 * @return The phase that the turn is in right now
	 */
	public Phase getPhase() {
		return phase;
	}
	
	/**
	 * Sets the turn's phase
	 * @param phase The phase the turn should go to
	 */
	public void setPhase(Phase phase) {
		this.phase = phase;
		if(phase.equals(Phase.CLEANUP)) {
			getGame().getBoard().cleanup(getPlayer());
		}
	}
	
	/**
	 * Ends a phase and moves to the next one
	 */
	public void endPhase() {
		switch(getPhase()) {
			case ACTION:
				stopAction();
				setPhase(Phase.BUY);
				break;
			case BUY:
				end();
				setPhase(Phase.CLEANUP);
				break;
			case CLEANUP:
			default:
				break;
		}
	}
	
	/**
	 * Adds to the amount of cards a player can buy this turn
	 * @param amount
	 */
	public void addBuys(int amount) {
		this.buycount += amount;
	}
	
	/**
	 * Removes a buy from the amount of cards a player can buy this round
	 */
	public void removeBuy() {
		this.buycount--;
	}
	
	/**
	 * Removes an action from the amount of cards a player can play this round
	 */
	public void removeAction() {
		this.actioncount--;
	}
	
	/**
	 * Adds to the amount of cards a player can play this turn
	 * @param amount
	 */
	public void addActions(int amount) {
		this.actioncount += amount;
	}
	
	/**
	 * Adds to the amount of coins a player has to buy cards with
	 * @param amount
	 */
	public void addBuypower(int amount) {
		this.buypower += amount;
	}
	
	/**
	 * Multiplies the amount of coins a player has to buy cards with
	 * @param amount
	 */
	public void addMultiplierBuypower(int amount) {
		this.buypowerMultiplier += amount;
	}
	
	/**
	 * Removes coins from the turn
	 * @param amount
	 */
	public void removeBuypower(int amount) {
		this.buypower -= amount;
	}
	
	/**
	 * @return How many coins a player can spend at this moment
	 */
	public int getBuypower() {
		return buypower * buypowerMultiplier;
	}
	
	/**
	 * @return The amount of cards a player can buy this turn
	 */
	public int getBuys() {
		return buycount;
	}
	
	/**
	 * @return The amount of actions a player has left this turn
	 */
	public int getActions() {
		return actioncount;
	}
	
	/**
	 * Gets the next player clockwise.
	 * Checks every player for the player we want the next from, and returns the one after that.
	 * If none is returned, then it means the next is the first player in the array.
	 * @param p The player we want the next from
	 * @return The next player
	 */
	public Player getNextPlayer() {
		boolean found = false;
		for(Player pl : getGame().getPlayers()) {
			if(found) {
				return pl;
			} else if(getPlayer().equals(pl)) {
				found = true;
			}
		}
		return getGame().getPlayers().get(0);
	}
	
	/**
	 * @return The next turn for this game
	 */
	public Turn getNextTurn() {
		return new Turn(getGame(), getNextPlayer());
	}

	/**
	 * Ends this player's turn this round
	 * discarding their hand and drawing 5 new cards from their deck
	 */
	public void end() {
		Player p = getPlayer();
		p.discardHand();
		for(int i = 0; i < 5; i++) {
			p.drawCardFromDeck();
		}
	}
	
	/**
	 * @param cardid Buy a card this turn
	 * @return The response
	 */
	public JSONObject buyCard(String cardid) {
		return buyCard(cardid, false);
	}
	
	/**
	 * @param cardid Buy a card this turn
	 * @param free If the card is free to purchase
	 * @return The response
	 */
	public JSONObject buyCard(String cardid, boolean free) {
		return buyCard(cardid, free, CardDestination.DISCARD);
	}
	
	public void gainCard(Player player, Card card, CardDestination destination) {
		Board board = getGame().getBoard();
		if(board.getSupply(board.getSupplyTypeForCard(card)).getCards().get(card) <= 0) {
			return;
		}
		switch(destination) {
			case TOP_DECK:
				// Shifts all other elements to the right
				player.getPile(Pile.DECK).add(0, card);
				break;
			case DECK:
				player.getPile(Pile.DECK).add(card);
				break;
			case HAND:
				player.getPile(Pile.HAND).add(card);
				break;
			case DISCARD:
			default:
				player.getPile(Pile.DISCARD).add(card);
				break;
		}
		board.getSupply(board.getSupplyTypeForCard(card)).removeOne(card);
	}
	
	/**
	 * Buys a card for a player
	 * @param cardid
	 * @param boolean if the card is free
	 * @return The response
	 */
	public JSONObject buyCard(String cardid, boolean free, CardDestination destination) {
		if(!free) {
			if(!phase.equals(Phase.BUY) || !isValidCard(cardid)) {
				return getGame().getGameServer().getGameManager()
						.getInvalid("Unable to perform purchase. (Not in the right phase ("+phase.toString()+") or card '"+cardid+"' is invalid");
			}
		}
		
		CardManager cm = getGame().getGameServer().getCardManager();
		Card card = cm.get(cardid);
		Board board = getGame().getBoard();
		
		if(board.getSupply(board.getSupplyTypeForCard(card)).getCards().get(card) <= 0) {
			return getGame().getGameServer().getGameManager()
					.getInvalid("There are no such cards left");
		}

		JSONObject response = new JSONObject().accumulate("response", "OK");
		getGame().getGameServer().getUtils().debug(getPlayer().getDisplayname() + " bought card: " + card.getName());
		
		if((getBuypower() >= card.getCost() && getBuys() > 0) || free) {
			if(destination == null) {
				destination = CardDestination.DECK;
			}
			switch(destination) {
				case TOP_DECK:
					// Shifts all other elements to the right
					getPlayer().getPile(Pile.DECK).add(0, card);
					break;
				case DECK:
					getPlayer().getPile(Pile.DECK).add(card);
					break;
				default:
					getPlayer().getPile(Pile.DISCARD).add(card);
					break;
			}
			if(!free) {
				removeBuy();
				removeBuypower(card.getCost());
			}
			board.getSupply(board.getSupplyTypeForCard(card)).removeOne(card);
			return response.accumulate("result", BuyResponse.BOUGHT);
		}
		// In other cases
		return response.accumulate("result", BuyResponse.CANTAFFORD);
	}
	
	public JSONObject playCard(String cardid) {
		return playCard(cardid, false);
	}
	
	/**
	 * Plays a card for a player
	 * @param cardid
	 * @return The response
	 */
	public JSONObject playCard(String cardid, boolean free) {
		GameManager gm = getGame().getGameServer().getGameManager();
		if(!canPlay(getPhase(), cardid)) {
			return gm.getInvalid("Unable to perform action. (Not in the right phase ("+phase.toString()+") or card '"+cardid+"' is invalid)");
		}
		
		Card card = getGame().getGameServer().getCardManager().get(cardid);
		if(getActions() <= 0 && card.getType().equals(CardType.ACTION) && !free) {
			return gm.getInvalid("No actions left");
		} else if(!getPlayer().getPile(Pile.HAND).contains(card) && !free) {
			return gm.getInvalid("Player doesn't have the selected card in their hand");
		}

		getGame().getGameServer().getUtils().debug(getPlayer().getDisplayname() + " played card: " + card.getName());
		if(!free) {
			getPlayer().getPile(Pile.HAND).remove(card);
			getGame().getBoard().addPlayedCard(card);
		}
		
		JSONObject response = playActions(card);
		
		if(getPhase().equals(Phase.ACTION)) {
			removeAction();
		}
		return response;
	}
	
	/**
	 * @param cardid
	 * @return Whether the card id exists in the card manager
	 */
	private boolean isValidCard(String cardid) {
		return getGame().getGameServer().getCardManager().exists(cardid);
	}
	
	/**
	 * @param phase
	 * @param cardid
	 * @return Whether that card can be played for this phase
	 */
	public boolean canPlay(Phase phase, String cardid) {
		CardManager cm = getGame().getGameServer().getCardManager();
		if(cm.exists(cardid)) {
			Card card = cm.get(cardid);
			if(card.getType().equals(CardType.VICTORY)
					|| cm.get(cardid).getType().equals(CardType.CURSE)) {
				return false;
			} else if(card.getType().equals(CardType.TREASURE)) {
				return phase.equals(Phase.BUY);
			} else if(card.getType().equals(CardType.ACTION)) {
				return phase.equals(Phase.ACTION);
			}
		}
		return false;
	}
	
	/**
	 * Plays the actions for this card for the player
	 * @param card
	 * @return The response
	 */
	private JSONObject playActions(Card card) {
		return playActions(card, null);
	}
	
	/**
	 * Plays the actions for this card for the player from a specific action
	 * @param card To execute the actions for
	 * @param from The action to start executing from
	 * @return The response
	 */
	public JSONObject playActions(Card card, Action from) {
		this.activeCard = card;
		// If there's a from action, don't perform actions until it's found
		boolean perform = (from == null);
		for(Action action : card.getActions()) {
			if(!perform) {
				if(action.equals(from)) {
					// Going to fire the next action because the from was found
					perform = true;
				}
			} else {
				JSONObject actionResponse = action.play(this, card);
				ActionResult result = ActionResult.valueOf(actionResponse.get("result").toString().toUpperCase());
				if(!result.equals(ActionResult.DONE)) {
					this.activeAction = action;
					return actionResponse;
				}
			}
		}
		return new JSONObject().accumulate("response", "OK")
							   .accumulate("result", ActionResult.DONE);
	}
	
	/**
	 * Only used when in a sub action where a player has to select a card
	 * @param cardid
	 * @return response
	 */
	public JSONObject selectCard(String cardid) {
		Action action = getActiveAction();
		GameManager gm = getGame().getGameServer().getGameManager();
		if(!phase.equals(Phase.ACTION) || !isValidCard(cardid)) {
			return gm.getInvalid("Unable to perform action. (Not in the right phase or card '"+cardid+"' is invalid)");
		}
		
		Card card = getGame().getGameServer().getCardManager().get(cardid);
		if(action == null) {
			return gm.getInvalid("Unable to select card, no active action");
		}

		getGame().getGameServer().getUtils().debug("Card selected: " + card.getName());
		JSONObject response = action.selectCard(getGame().getTurn(), card);
		return response;
	}
	
	/**
	 * Stops the current action
	 * @return response
	 */
	public JSONObject stopAction() {
		JSONObject response = new JSONObject().accumulate("response", "OK").accumulate("result", ActionResult.DONE);
		Action action = getActiveAction();
		if(action != null) {
			// If the action isn't fully completed, finish it first
			if(!action.isCompleted(this)) {
				response = action.finish(this);
			}
			// Checks if the finish completed the action
			// If multiple people have to do the action
			// then this will be true when the last player completed it
			if(action.isCompleted(this)) {
				response = playActions(getActiveCard(), getActiveAction());
				activeAction = null;
				if(response.get("result").equals(ActionResult.DONE) && getTriggerAction() != null) {
					response = getTriggerAction().finish(this);
					// If the overhead action is done, end it
					if(response.get("result").equals(ActionResult.DONE)) {
						setTriggerAction(null);
					}
				}
			}
		}
		return response; 
	}
	
	/**
	 * @return The card currently active
	 * used when an action requires actions from other players
	 */
	public Card getActiveCard() {
		return activeCard;
	}
	
	/**
	 * @return The action that is currently happening
	 */
	public Action getActiveAction() {
		return activeAction;
	}
	
	public boolean canEndPhase() {
		if(activeAction != null) {
			if(!activeAction.isCompleted(this)) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Action that is currently controlling the turn, like throne room
	 * @param action
	 */
	public void setTriggerAction(Action action) {
		this.triggerAction = action;
	}
	
	public Action getTriggerAction() {
		return this.triggerAction;
	}
	
	/**
	 * @return The turn and its info in JSON format
	 */
	public JSONObject getAsJson() {
		return new JSONObject()
				.accumulate("player", getPlayer().getDisplayname())
				.accumulate("buysleft", getBuys())
				.accumulate("actionsleft", getActions())
				.accumulate("buypower", getBuypower())
				.accumulate("phase", getPhase().toString())
				.accumulate("next_player", getNextPlayer().getDisplayname());
	}
	
}
