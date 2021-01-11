package at.pwd.MagicAI;
import at.pwd.boardgame.game.base.WinState;
import at.pwd.boardgame.game.mancala.MancalaGame;
import at.pwd.boardgame.game.mancala.MancalaState;
import at.pwd.boardgame.game.mancala.agent.MancalaAgent;
import at.pwd.boardgame.game.mancala.agent.MancalaAgentAction;

import java.sql.*;
import java.util.*;

public class TU_SGP_MagicAI_AI implements MancalaAgent {
    private Random r = new Random();
    private MancalaState originalState;
    private static final double C = 1.0f/Math.sqrt(2.0f);
    private static Connection connection;
    private static PreparedStatement selectBoardstate;
    private static PreparedStatement selectSlots;

    private class MCTSTree {
        private int visitCount;
        private int winCount;
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
                if (visitCount != 0 && m.visitCount != 0) {
                    currentValue =  wC/vC + C*Math.sqrt(2*Math.log(visitCount) / vC) + m.seed;
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

    public TU_SGP_MagicAI_AI() {
        try {
            Class driver = Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        try {
            connection = DriverManager.getConnection("jdbc:h2:./data", "", "");
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

    private String getSlotId(int number, int currentPlayer) {
        if (currentPlayer == 0) {
            return "" + (15 - number);
        } else {
            return "" + (1 + number);
        }
    }

    private int getSlotNumber(String id) {
        int slotId = Integer.parseInt(id);
        if (slotId < 8) {
            return slotId -1;
        } else {
            return 15 - slotId;
        }
    }

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

    private double getBookWeight(int numberOfStones) {
        return (7.0/12960) * (numberOfStones * numberOfStones) - (13.0/360) * numberOfStones + (4.0/5);
    }

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
                if (resultSet.getInt("times_seen") >= 20) { // Konstante hier adjustieren
                    boardstateId = resultSet.getLong("id");
                    selectSlots.setLong(1, boardstateId);
                    resultSet = selectSlots.executeQuery();
                    double winLossRatio = 0.0;
                    double bestRatio = -1.0;
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


    @Override
    public MancalaAgentAction doTurn(int computationTime, MancalaGame game) {
        long start = System.currentTimeMillis();
        this.originalState = game.getState();

        MCTSTree root = new MCTSTree(game, 0.0);

        String bookMove = null;
        Boardstate boardstate = saveCurrentBoardstate(game);
        int numberofStones = boardstate.slot1 + boardstate.slot2 + boardstate.slot3 + boardstate.slot4 + boardstate.slot5
                + boardstate.slot6 + boardstate.opponent_slot1 + boardstate.opponent_slot2 + boardstate.opponent_slot3 +
                boardstate.opponent_slot4 + boardstate.opponent_slot5 + boardstate.opponent_slot6;

        double bookWeight = getBookWeight(numberofStones);

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

        Map<String, Integer> heuristics = new HashMap<>();
        int maxHeuristic = Integer.MIN_VALUE;
        int val;
        for (String move : game.getSelectableSlots()) {
            val = greedyHeuristic(root.game, move);
            maxHeuristic = Math.max(val, maxHeuristic);
            heuristics.put(move, val);
        }

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

        while ((System.currentTimeMillis() - start) < (computationTime*1000 - 1000)) {
            MCTSTree best = treePolicy(root);
            WinState winning = defaultPolicy(best.game);
            backup(best, winning);
            System.gc();
        }

        MCTSTree selected = root.getBestNode();
        System.out.println("Selected action " + selected.winCount + " / " + selected.visitCount);

        return new MancalaAgentAction(selected.action);
    }

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

    private MCTSTree expand(MCTSTree best) {
        List<String> legalMoves = best.game.getSelectableSlots();

        //remove already expanded moves
        for(MCTSTree move : best.children)
            legalMoves.remove(move.action);

        return best.move(legalMoves.get(r.nextInt(legalMoves.size())), 0.0);
    }

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
