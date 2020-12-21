package at.pwd.MagicAI.mancala;

import at.pwd.boardgame.game.base.WinState;
import at.pwd.boardgame.game.mancala.MancalaGame;
import at.pwd.boardgame.game.mancala.agent.MancalaHumanAgent;
import at.pwd.boardgame.services.GameFactory;
import at.pwd.boardgame.services.XSLTService;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;

/**
 * MancalaGame is an implementation of the Game interface for Mancala.
 *
 * Keep in mind that MancalaGame does not check for "valid" moves. Every move that is done is
 * immediately executed.
 *
 * MancalaGames state is in the MancalaState object and the board configuration in the MancalaBoard.
 *
 */
public class MyMancalaGame extends MancalaGame {
    /**
     * The transformer to create the view FXML
     */
    public static final String GAME_BOARD_TRANFORMER = "/mancala_board_transformer.xsl";

    /**
     * The name of this game
     */
    public static final String GAME_NAME = "normal_mancala";

    private MyMancalaBoard board;
    private MyMancalaState state;
    private String boardXml;

    /**
     * Registers MancalaGame to the GameFactory and initializes MancalaHumanAgent
     */
    public static void init() {
        GameFactory.getInstance().register(GAME_NAME, MyMancalaGame.class);
        MancalaHumanAgent.init();
    }

    @Override
    public InputStream getViewXml() {
        Map<String, String> params = new HashMap<>();
        params.put("stones-per-slot", String.valueOf(board.getStonesPerSlot()));
        return XSLTService.getInstance().execute(
                GAME_BOARD_TRANFORMER,
                new StreamSource(new StringReader(boardXml)),
                params
        );
    }

    @Override
    public void loadBoard(InputStream boardStream) {
        this.boardXml = XSLTService.convertStreamToString(boardStream);

        Serializer serializer = new Persister();
        try {
            this.board = serializer.read(MyMancalaBoard.class, boardXml);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.state = new MyMancalaState(this.board);
    }

    /**
     * Default constructor used by GameFactory
     */
    public MyMancalaGame() {}

    /**
     * Creates a semi deep copy of the given MancalaGame (State is copied, board is kept the same)
     * @param game The game that should be copied.
     */
    public MyMancalaGame(MyMancalaGame game) {
        this(game.getState(), game.getBoard());
    }

    /**
     * Creates a new MancalaGame
     * @param state If null the default MancalaState of the board is generated (placing stones_per_slot many
     *              stones on each slot). If not null the given state is used.
     * @param board The board that should be used for this MancalaGame
     */
    public MyMancalaGame(MyMancalaState state, MyMancalaBoard board) {
        this.board = board;
        this.state = state != null ? new MyMancalaState(state) : new MyMancalaState(board);
    }

    /**
     * Selects the slot with the given ID and calculates the turn.
     * If this ends the game. The final stones of the enemys player are placed in his
     * depot too.
     *
     * Throws a RuntimeException if an invalid slot is selected
     *
     * @param id The ID of the slot that has been selected
     * @return true ... the current player can play another turn, false ... the current player has to end his turn
     */
    public boolean selectSlot(String id) {
        int stones = state.stonesIn(id);
        int owner = board.getElement(id).getOwner();

        if (owner != state.getCurrentPlayer() || board.isDepot(id) || stones == 0) {
            throw new RuntimeException("You cannot select this slot: " + id);
        }

        state.removeStones(id);
        String currentId = board.next(id);
        boolean playAnotherTurn = false;
        while (stones > 0) {
            // if this is the player depot of the enemy, do not put it in
            boolean skip = false;
            boolean ownDepot = false;
            if (board.isDepot(currentId)) {
                MyMancalaBoard.PlayerDepot depot = (MyMancalaBoard.PlayerDepot)board.getElement(currentId);
                if (depot.getPlayer() != state.getCurrentPlayer()) {
                    skip = true;
                } else {
                    ownDepot = true;
                }
            }

            if (!skip) {
                state.addStones(currentId, 1);
                stones--;

                boolean isLast = stones == 0;
                boolean landedOnEmpty = state.stonesIn(currentId) == 1;
                boolean landedOnOwn = board.getElement(currentId).getOwner() == state.getCurrentPlayer();
                if (landedOnEmpty && landedOnOwn && isLast && !ownDepot) {
                    // get the stones from enemys slot
                    String enemy = board.getEnemySlotOf(currentId);
                    int enemyStones = state.stonesIn(enemy);
                    state.removeStones(currentId);
                    state.removeStones(enemy);
                    String depot = board.getDepotOf(currentId);
                    state.addStones(depot, enemyStones + 1); // stones from enemy + own stone in slot
                }

                playAnotherTurn = isLast && ownDepot;
            }
            currentId = board.next(currentId);
        }
        if (playAnotherTurn) {
            // finally check if already won => if no, then another round can be played
            playAnotherTurn = checkIfPlayerWins().getState() == WinState.States.NOBODY;
        }
        return playAnotherTurn;
    }

    /**
     * Returns a list of selectable slots for the current game state and current board
     * @return A List containing all selectable slot IDs. If there is nothing selectable the list is empty
     */
    public List<String> getSelectableSlots() {
        List<String> slots = new ArrayList<>();
        for (MyMancalaBoard.Slot slot : board.getSlots()) {
            // slot should belong to the current player and not be empty
            if (slot.belongsToPlayer() == state.getCurrentPlayer() && state.stonesIn(slot.getId()) > 0) {
                slots.add(slot.getId());
            }
        }
        return slots;
    }

    /**
     * Helper class for determining who wins the game
     */
    private class Entry implements Comparable<Entry> {
        int num;
        int playerId;

        Entry(int num, int playerId) {
            this.num = num;
            this.playerId = playerId;
        }

        @Override
        public int compareTo(Entry o) {
            return o.num - this.num;
        }
    }

    /**
     * Checks if a player is winning. If a player is winning the opponent gets the remaining stones into his
     * own depot (so this method also alters the state).
     *
     * @return A WinState describing if and who is winning
     */
    public WinState checkIfPlayerWins() {
        boolean didEnd = false;
        for (MyMancalaBoard.PlayerDepot depot : board.getDepots()) {
            didEnd = true;
            int playerId = depot.getPlayer();
            for (MyMancalaBoard.Slot slot : board.getSlots()) {
                if (slot.belongsToPlayer() == playerId && state.stonesIn(slot.getId())> 0) {
                    didEnd = false;
                    break;
                }
            }
            if (didEnd) {
                break;
            }
        }

        WinState winState;
        if (didEnd) {
            // give all missing stones to enemy
            for (MyMancalaBoard.Slot slot : board.getSlots()) {
                String depot = board.getDepotOf(slot.getId());
                int num = state.stonesIn(slot.getId());
                state.removeStones(slot.getId());
                state.addStones(depot, num);
            }

            // it is designed to support multiple players (but not yet needed)
            // thats the reason why it is so complex
            List<Entry> nums = new ArrayList<>();
            for (MyMancalaBoard.PlayerDepot depot : board.getDepots()) {
                int currentNum = state.stonesIn(depot.getId());
                nums.add(new Entry(currentNum, depot.getPlayer()));
            }
            Collections.sort(nums);
            if (nums.size() == 1) {
                winState = new WinState(WinState.States.SOMEONE, nums.get(0).playerId);
            } else if (nums.get(0).num == nums.get(1).num) { // draw
                winState = new WinState(WinState.States.MULTIPLE, -1);
            } else { // one player has the most
                winState = new WinState(WinState.States.SOMEONE, nums.get(0).playerId);
            }
        } else {
            winState = new WinState(WinState.States.NOBODY, -1);
        }

        return winState;
    }

    @Override
    public MyMancalaBoard getBoard() {
        return board;
    }

    @Override
    public int nextPlayer() {
        int player = (state.getCurrentPlayer() + 1) % getBoard().getPlayers().size();
        state.setCurrentPlayer(player);
        return player;
    }

    @Override
    public MyMancalaState getState() {
        return state;
    }
}
