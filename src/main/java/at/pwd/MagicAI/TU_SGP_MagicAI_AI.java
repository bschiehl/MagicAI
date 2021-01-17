package at.pwd.MagicAI;
import at.pwd.boardgame.game.base.WinState;
import at.pwd.boardgame.game.mancala.MancalaGame;
import at.pwd.boardgame.game.mancala.MancalaState;
import at.pwd.boardgame.game.mancala.agent.MancalaAgent;
import at.pwd.boardgame.game.mancala.agent.MancalaAgentAction;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * Class for the MagicAI Agent
 *
 * created by bschiehl 2021/01/17
 */
public class TU_SGP_MagicAI_AI implements MancalaAgent {
    private Random r = new Random();
    private MancalaState originalState;
    private static final double C = 1.0f/Math.sqrt(2.0f);
    private static Connection connection;
    private static PreparedStatement selectBoardstate;
    private static PreparedStatement selectSlots;

    // Engine implementation of a MCTS node
    private class MCTSTree {
        private int visitCount;
        private int winCount;

        // seed initialized in main method with results from greedyHeuristic and database book moves
        private double seed;

        private MancalaGame game;
        private WinState winState;
        private MCTSTree parent;
        private List<MCTSTree> children;
        String action;

        MCTSTree(MancalaGame game, double seed) {
            this.game = game;
            this.children = new ArrayList<>();
            this.winState = game.checkIfPlayerWins();
            this.seed = seed;
        }

        boolean isNonTerminal() {
            return winState.getState() == WinState.States.NOBODY;
        }

        MCTSTree getBestNode() {
            MCTSTree best = null;
            double value = 0;
            for (MCTSTree m : children) {
                double wC = m.winCount;
                double vC = m.visitCount;
                double currentValue;
                // since the main method initializes moves with a seed, this method can be called on trees with 0 visits
                if (visitCount != 0 && m.visitCount != 0) {
                    currentValue =  wC/vC + C*Math.sqrt(2*Math.log(visitCount) / vC) + m.seed; //UCB1
                } else {
                    currentValue = m.seed;
                }

                if (best == null || currentValue > value) {
                    value = currentValue;
                    best = m;
                }
            }

            return best;
        }

        boolean isFullyExpanded() {
            return children.size() == game.getSelectableSlots().size();
        }

        MCTSTree move(String action, double seed) {
            MancalaGame newGame = new MancalaGame(this.game);
            if (!newGame.selectSlot(action)) {
                newGame.nextPlayer();
            }

            MCTSTree tree = new MCTSTree(newGame, seed);
            tree.action = action;
            tree.parent = this;

            this.children.add(tree);

            return tree;
        }
    }

    /**
     * Constructor to connect to the database
     */
    public TU_SGP_MagicAI_AI() {
        try {
            Class driver = Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        String path = TU_SGP_MagicAI_AI.class.getClassLoader().getResource("data.mv.db").getPath();
        String domain = "jdbc:h2:";
        try {
            path = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {

        }
        if (path.endsWith(".mv.db")) {
            path = path.substring(0, path.length() - 6);
        }
        if (path.startsWith("file:")) {
            path = path.substring(5);
            domain = domain + "zip:";
        }
        String url = domain + path + ";IFEXISTS=TRUE;TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";
        System.out.println(url);
        try {
            connection = DriverManager.getConnection(url, "", "");
            selectBoardstate = connection.prepareStatement("SELECT id, times_seen FROM boardstate WHERE slot1 = ?" +
                    "AND slot2 = ? AND slot3 = ? AND slot4 = ? AND slot5 = ? AND " +
                    "slot6 = ? AND opponent_slot1 = ? AND opponent_slot2 = ? AND opponent_slot3 = ? AND " +
                    "opponent_slot4 = ? AND opponent_slot5 = ? AND opponent_slot6 = ?");
            selectSlots = connection.prepareStatement("SELECT * FROM chosen_slots WHERE " +
                    "boardstate_id = ?");
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        if (connection != null) {
            System.out.println("connection established");
        }
    }

    /**
     * Helper method that converts a number from 1 to 6 to the slot id as used by the engine
     * @param number Slot number
     * @param currentPlayer Player id
     * @return The slot id corresponding to the given number and player
     */
    private String getSlotId(int number, int currentPlayer) {
        if (currentPlayer == 0) {
            return "" + (15 - number);
        } else {
            return "" + (1 + number);
        }
    }

    /**
     * Helper data class to save boardstates
     */
    private class Boardstate {
        int slot1;
        int slot2;
        int slot3;
        int slot4;
        int slot5;
        int slot6;
        int opponent_slot1;
        int opponent_slot2;
        int opponent_slot3;
        int opponent_slot4;
        int opponent_slot5;
        int opponent_slot6;
        int current_player;

        public Boardstate(int slot1, int slot2, int slot3, int slot4, int slot5, int slot6, int opponent_slot1,
                          int opponent_slot2, int opponent_slot3, int opponent_slot4, int opponent_slot5,
                          int opponent_slot6, int current_player) {
            this.slot1 = slot1;
            this.slot2 = slot2;
            this.slot3 = slot3;
            this.slot4 = slot4;
            this.slot5 = slot5;
            this.slot6 = slot6;
            this.opponent_slot1 = opponent_slot1;
            this.opponent_slot2 = opponent_slot2;
            this.opponent_slot3 = opponent_slot3;
            this.opponent_slot4 = opponent_slot4;
            this.opponent_slot5 = opponent_slot5;
            this.opponent_slot6 = opponent_slot6;
            this.current_player = current_player;
        }
    }

    /**
     * Parabolic function returning the probability that the agent searches for a book move in the database.
     * The probability is high at the beginning of the game, is lowest in the mid game and gets higher again in the end game
     * As a measure of the progress of the game the number of stones remaining in the game is used.
     *
     * @param numberOfStones Number of stones remaining in the game
     * @return Probability of searching for a book move
     */
    private double getBookWeight(int numberOfStones) {
        return (7.0/12960) * (numberOfStones * numberOfStones) - (13.0/360) * numberOfStones + (4.0/5);
    }

    /**
     * Method that looks for a book move in the database for a given boardstate
     * @param boardstate The boardstate for which to look for a book move
     * @return A number from 1 to 6 representing the slot that the database considers a book move
     */
    private int getBookMove(Boardstate boardstate) {
        long boardstateId = 0;
        int bestSlot = -1;
        try {
            selectBoardstate.setInt(1, boardstate.slot1);
            selectBoardstate.setInt(2, boardstate.slot2);
            selectBoardstate.setInt(3, boardstate.slot3);
            selectBoardstate.setInt(4, boardstate.slot4);
            selectBoardstate.setInt(5, boardstate.slot5);
            selectBoardstate.setInt(6, boardstate.slot6);
            selectBoardstate.setInt(7, boardstate.opponent_slot1);
            selectBoardstate.setInt(8, boardstate.opponent_slot2);
            selectBoardstate.setInt(9, boardstate.opponent_slot3);
            selectBoardstate.setInt(10, boardstate.opponent_slot4);
            selectBoardstate.setInt(11, boardstate.opponent_slot5);
            selectBoardstate.setInt(12, boardstate.opponent_slot6);
            ResultSet resultSet = selectBoardstate.executeQuery();
            if (resultSet.last()) {
                // Moves only get considered if the database has seen this boardstate 20 or more times
                if (resultSet.getInt("times_seen") >= 20) {
                    boardstateId = resultSet.getLong("id");
                    selectSlots.setLong(1, boardstateId);
                    resultSet = selectSlots.executeQuery();
                    double winLossRatio = 0.0;
                    double bestRatio = -1.0;
                    // find the move with the best winrate
                    while (resultSet.next()) {
                        winLossRatio = resultSet.getInt("times_won") * 1.0/resultSet.getInt("times_lost");
                        if (winLossRatio > bestRatio) {
                            bestRatio = winLossRatio;
                            bestSlot = resultSet.getInt("chosen_slot");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return bestSlot;
    }

    /**
     * Helper method that returns a Boardstate object from an instance of MancalaGame, representing the current boardstate
     * @param game The current game
     * @return The Boardstate of the current game
     */
    private Boardstate saveCurrentBoardstate(MancalaGame game) {
        MancalaState mancalaState = game.getState();
        int currentPlayer = mancalaState.getCurrentPlayer();
        int currentOpponent = currentPlayer ^ 1;
        Boardstate boardstate = new Boardstate(
                mancalaState.stonesIn(getSlotId(1, currentPlayer)),
                mancalaState.stonesIn(getSlotId(2, currentPlayer)),
                mancalaState.stonesIn(getSlotId(3, currentPlayer)),
                mancalaState.stonesIn(getSlotId(4, currentPlayer)),
                mancalaState.stonesIn(getSlotId(5, currentPlayer)),
                mancalaState.stonesIn(getSlotId(6, currentPlayer)),
                mancalaState.stonesIn(getSlotId(1, currentOpponent)),
                mancalaState.stonesIn(getSlotId(2, currentOpponent)),
                mancalaState.stonesIn(getSlotId(3, currentOpponent)),
                mancalaState.stonesIn(getSlotId(4, currentOpponent)),
                mancalaState.stonesIn(getSlotId(5, currentOpponent)),
                mancalaState.stonesIn(getSlotId(6, currentOpponent)),
                currentPlayer
        );
        return boardstate;
    }

    /**
     * Helper recursive method for the greedy heuristic to account for the agent playing multiple consecutive moves
     * @param originalGame The current game
     * @param originalPlayer The current player
     * @return The maximum number of stones the agent can gain by repeating moves minus the maximum number of stones the
     * opponent can gain with their next move
     */
    private int recursiveGreedy(MancalaGame originalGame, int originalPlayer) {
        MancalaGame gameCopy;
        MancalaState originalState = originalGame.getState();
        int currentPlayer = originalState.getCurrentPlayer();
        int originalStonesInDepot = originalState.stonesIn(originalGame.getBoard().getDepotOfPlayer(currentPlayer));
        int maxGain = Integer.MIN_VALUE;
        for (String slotId : originalGame.getSelectableSlots()) {
            gameCopy = new MancalaGame(originalGame);
            boolean anotherTurn = gameCopy.selectSlot(slotId);
            int stoneGain = gameCopy.getState().stonesIn(gameCopy.getBoard().getDepotOfPlayer(currentPlayer)) - originalStonesInDepot;
            if (anotherTurn) {
                stoneGain += recursiveGreedy(gameCopy, originalPlayer);
            } else if (currentPlayer == originalPlayer) {
                gameCopy.getState().setCurrentPlayer(currentPlayer ^ 1);
                stoneGain -= recursiveGreedy(gameCopy, originalPlayer);
            }
            if (stoneGain > maxGain) {
                maxGain = stoneGain;
            }
        }
        return maxGain;
    }

    /**
     * Method to be called for the greedy heuristic. The heuristic is inspired by the minimax algorithm.
     * For a given move, the method gives the difference between the maximum number of stones this agent can gain with this move and
     * the maximum number of stones the opponent can then get with their next move
     * @param game The current game
     * @param move A possible move the agent can make
     * @return The maximum number of stones this agent can gain by playing the move minus the maximum number of stones the
     * opponent can gain with their next move
     */
    private int greedyHeuristic(MancalaGame game, String move) {
        int originalPlayer = game.getState().getCurrentPlayer();
        int originalStonesInDepot = game.getState().stonesIn(game.getBoard().getDepotOfPlayer(originalPlayer));
        MancalaGame copy = new MancalaGame(game);
        boolean anotherTurn = copy.selectSlot(move);
        int gain = copy.getState().stonesIn(copy.getBoard().getDepotOfPlayer(originalPlayer)) - originalStonesInDepot;
        if (anotherTurn) {
            return gain + recursiveGreedy(copy, originalPlayer);
        } else {
            copy.nextPlayer();
            return gain - recursiveGreedy(copy, originalPlayer);
        }
    }

    /**
     * Main method that gets called by the engine and returns the chosen move
     * @param computationTime the computation time in seconds
     * @param game instance of the game for which a move should be returned
     * @return The action chosen by the agent
     */

    @Override
    public MancalaAgentAction doTurn(int computationTime, MancalaGame game) {
        long start = System.currentTimeMillis();
        this.originalState = game.getState();

        MCTSTree root = new MCTSTree(game, 0.0);

        // save current boardstate in an object to query the database
        String bookMove = null;
        Boardstate boardstate = saveCurrentBoardstate(game);
        int numberofStones = boardstate.slot1 + boardstate.slot2 + boardstate.slot3 + boardstate.slot4 + boardstate.slot5
                + boardstate.slot6 + boardstate.opponent_slot1 + boardstate.opponent_slot2 + boardstate.opponent_slot3 +
                boardstate.opponent_slot4 + boardstate.opponent_slot5 + boardstate.opponent_slot6;

        double bookWeight = getBookWeight(numberofStones);

        // search for a book move with a probability of bookWeight and save it if found
        System.out.println("Chance to search for a book move: " + bookWeight);
        if (r.nextInt(1000000) <= 1000000.0 * bookWeight) {
            int bookSlot = getBookMove(boardstate);
            if (bookSlot > 0) {
                bookMove = getSlotId(bookSlot, game.getState().getCurrentPlayer());
                System.out.println("Book move found: " + bookMove);
            } else {
                System.out.println("No book move found.");
            }
        } else {
            System.out.println("Not searching for a book move.");
        }

        // Save values returned by greedy heuristic in a map with the respective moves
        // Find maximum heuristic value
        Map<String, Integer> heuristics = new HashMap<>();
        int maxHeuristic = Integer.MIN_VALUE;
        int val;
        for (String move : game.getSelectableSlots()) {
            val = greedyHeuristic(root.game, move);
            maxHeuristic = Math.max(val, maxHeuristic);
            heuristics.put(move, val);
        }

        // Calculate seeds from greedy heuristic values and book move
        // Expand child nodes of root and initialize their seeds
        int diffFromBest;
        double seed;
        for (String move : game.getSelectableSlots()) {
            val = heuristics.get(move);
            diffFromBest = maxHeuristic - val;
            seed = 1.0 - 0.1 * diffFromBest;
            if (move.equals(bookMove)) {
                seed += 0.5;
            }
            System.out.println("Move: " + move + ", Heuristic: " + heuristics.get(move) + ", Seed: " + seed);
            root.move(move, seed);
        }

        // run Monte Carlo Tree Search
        while ((System.currentTimeMillis() - start) < (computationTime*1000 - 500)) {
            MCTSTree best = treePolicy(root);
            WinState winning = defaultPolicy(best.game);
            backup(best, winning);
            System.gc();
        }

        MCTSTree selected = root.getBestNode();
        System.out.println("Selected action " + selected.winCount + " / " + selected.visitCount);

        return new MancalaAgentAction(selected.action);
    }

    //Backpropagation
    private void backup(MCTSTree current, WinState winState) {
        boolean hasWon = winState.getState() == WinState.States.SOMEONE && winState.getPlayerId() == originalState.getCurrentPlayer();

        while (current != null) {
            // always increase visit count
            current.visitCount++;

            // if it ended in a win => increase the win count
            current.winCount += hasWon ? 1 : 0;

            current = current.parent;
        }
    }

    // Selection
    private MCTSTree treePolicy(MCTSTree current) {
        while (current.isNonTerminal()) {
            if (!current.isFullyExpanded()) {
                return expand(current);
            } else {
                current = current.getBestNode();
            }
        }
        return current;
    }

    // Expansion
    private MCTSTree expand(MCTSTree best) {
        List<String> legalMoves = best.game.getSelectableSlots();

        //remove already expanded moves
        for(MCTSTree move : best.children)
            legalMoves.remove(move.action);

        return best.move(legalMoves.get(r.nextInt(legalMoves.size())), 0.0);
    }

    // Simulation
    private WinState defaultPolicy(MancalaGame game) {
        game = new MancalaGame(game); // copy original game
        WinState state = game.checkIfPlayerWins();

        while(state.getState() == WinState.States.NOBODY) {
            String play;
            do {
                List<String> legalMoves = game.getSelectableSlots();
                play = legalMoves.get(r.nextInt(legalMoves.size()));

            } while(game.selectSlot(play));
            game.nextPlayer();
            state = game.checkIfPlayerWins();
        }

        return state;
    }

    @Override
    public String toString() {
        return "MagicAI Agent";
    }
}
