package at.pwd.MagicAI.mancala.agent;

import at.pwd.boardgame.game.agent.AgentAction;
import at.pwd.boardgame.game.mancala.MancalaGame;
import at.pwd.boardgame.game.mancala.agent.MancalaAgentAction;

/**
 * Agent action for the game Mancala
 */
public class MyMancalaAgentAction extends MancalaAgentAction {
    private final String id;

    /**
     * Construcor for the Agent Action
     * @param id The slot that is selected. This may only be a slot and not a depot (since it is not allowed
     *           to select a depot)
     */

    public MyMancalaAgentAction(String id) {
        super(id);
        this.id = id;
    }

    public String getId () {
        return id;
    }

}
