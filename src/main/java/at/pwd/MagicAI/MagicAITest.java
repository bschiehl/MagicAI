package at.pwd.MagicAI;
import at.pwd.MagicAI.mancala.MyMancalaGame;
import at.pwd.MagicAI.mancala.MyMancalaState;
import at.pwd.MagicAI.mancala.agent.MyMancalaAgentAction;
import at.pwd.boardgame.game.base.WinState;
import at.pwd.boardgame.game.mancala.MancalaGame;
import at.pwd.boardgame.game.mancala.MancalaState;
import at.pwd.boardgame.game.mancala.agent.MancalaAgent;
import at.pwd.boardgame.game.mancala.agent.MancalaAgentAction;

import java.sql.*;
import java.util.*;

public class MagicAITest implements MancalaAgent {
    private Random r = new Random();
    private MancalaState originalState;
    private static final double C = 1.0f/Math.sqrt(2.0f);
    private static Connection connection;
    private static PreparedStatement selectBoardstate;
    private static PreparedStatement updateBoardstateTimesSeen;
    private static PreparedStatement insertBoardstate;
    private static PreparedStatement selectChosenSlots;
    private static PreparedStatement insertChosenSlots;
    private static PreparedStatement updateChosenSlotsTimesWon;
    private static PreparedStatement updateChosenSlotsTimesLost;
    private static PreparedStatement selectSlots;

    private class MCTSTree {
        private int visitCount;
        private int winCount;
        private double seed;

        private MyMancalaGame game;
        private WinState winState;
        private MCTSTree parent;
        private List<MCTSTree> children;
        String action;

        MCTSTree(MyMancalaGame game, double seed) {
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
            MyMancalaGame newGame = new MyMancalaGame(this.game);
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

    public MagicAITest () {
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
//            updateBoardstateTimesSeen = connection.prepareStatement("UPDATE boardstate SET " +
//                    "times_seen = ? WHERE id = ?");
//            insertBoardstate = connection.prepareStatement("INSERT INTO boardstate VALUES " +
//                    "(null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)");
//            selectChosenSlots = connection.prepareStatement("SELECT id, times_won, times_lost FROM" +
//                    " chosen_slots WHERE boardstate_id = ? AND chosen_slot = ?");
//            insertChosenSlots = connection.prepareStatement("INSERT INTO chosen_slots VALUES " +
//                    "(null, ?, ?, ?, ?)");
//            updateChosenSlotsTimesWon = connection.prepareStatement("UPDATE chosen_slots SET " +
//                    "times_won = ? WHERE id = ?");
//            updateChosenSlotsTimesLost = connection.prepareStatement("UPDATE chosen_slots SET " +
//                    "times_lost = ? WHERE id = ?");
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
        int chosen_slot;
        int current_player;

        public Boardstate(int slot1, int slot2, int slot3, int slot4, int slot5, int slot6, int opponent_slot1, int opponent_slot2, int opponent_slot3, int opponent_slot4, int opponent_slot5, int opponent_slot6, int chosen_slot, int current_player) {
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
            this.chosen_slot = chosen_slot;
            this.current_player = current_player;
        }
    }

    private List<Boardstate> saveCurrentBoardstate(List<Boardstate> boardstates, MancalaGame game, String chosenSlot) {
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
                getSlotNumber(chosenSlot),
                currentPlayer
        );
        boardstates.add(boardstate);
        return boardstates;
    }

    private void saveToDatabase(List<Boardstate> statesAndSlots, WinState state) {
        long boardstateId = 0;

        for (Boardstate boardstate: statesAndSlots) {
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
                    boardstateId = resultSet.getLong("id");
                    int timesSeen = resultSet.getInt("times_seen");
                    updateBoardstateTimesSeen.setInt(1, timesSeen + 1);
                    updateBoardstateTimesSeen.setLong(2, boardstateId);
                    updateBoardstateTimesSeen.executeUpdate();
                } else {
                    insertBoardstate.setInt(1, boardstate.slot1);
                    insertBoardstate.setInt(2, boardstate.slot2);
                    insertBoardstate.setInt(3, boardstate.slot3);
                    insertBoardstate.setInt(4, boardstate.slot4);
                    insertBoardstate.setInt(5, boardstate.slot5);
                    insertBoardstate.setInt(6, boardstate.slot6);
                    insertBoardstate.setInt(7, boardstate.opponent_slot1);
                    insertBoardstate.setInt(8, boardstate.opponent_slot2);
                    insertBoardstate.setInt(9, boardstate.opponent_slot3);
                    insertBoardstate.setInt(10, boardstate.opponent_slot4);
                    insertBoardstate.setInt(11, boardstate.opponent_slot5);
                    insertBoardstate.setInt(12, boardstate.opponent_slot6);
                    insertBoardstate.executeUpdate();
                    resultSet = selectBoardstate.executeQuery();
                    if (resultSet.last()) {
                        boardstateId = resultSet.getLong("id");
                    } else {
                        System.err.println("No boardstate inserted");
                    }
                }
                boolean hasWon = state.getPlayerId() == boardstate.current_player;

                selectChosenSlots.setLong(1, boardstateId);
                selectChosenSlots.setInt(2, boardstate.chosen_slot);
                resultSet = selectChosenSlots.executeQuery();
                if (resultSet.last()) {
                    int chosenSlotsId = resultSet.getInt("id");
                    if (hasWon) {
                        int timesWon = resultSet.getInt("times_won");
                        updateChosenSlotsTimesWon.setInt(1, timesWon + 1);
                        updateChosenSlotsTimesWon.setInt(2, chosenSlotsId);
                        updateChosenSlotsTimesWon.executeUpdate();
                    } else {
                        int timesLost = resultSet.getInt("times_lost");
                        updateChosenSlotsTimesLost.setInt(1, timesLost + 1);
                        updateChosenSlotsTimesLost.setInt(2, chosenSlotsId);
                        updateChosenSlotsTimesLost.executeUpdate();
                    }
                } else {
                    insertChosenSlots.setLong(1, boardstateId);
                    insertChosenSlots.setInt(2, boardstate.chosen_slot);
                    if (hasWon) {
                        insertChosenSlots.setInt(3, 1);
                        insertChosenSlots.setInt(4, 0);
                    } else {
                        insertChosenSlots.setInt(3, 0);
                        insertChosenSlots.setInt(4, 1);
                    }
                    insertChosenSlots.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
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

    private int recursiveGreedy(MyMancalaGame originalGame, int originalPlayer) {
        MyMancalaGame gameCopy;
        MyMancalaState originalState = originalGame.getState();
        int currentPlayer = originalState.getCurrentPlayer();
        int originalStonesInDepot = originalState.stonesIn(originalGame.getBoard().getDepotOfPlayer(currentPlayer));
        int maxGain = Integer.MIN_VALUE;
        for (String slotId : originalGame.getSelectableSlots()) {
            gameCopy = new MyMancalaGame(originalGame);
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

    private int greedyHeuristic(MyMancalaGame game, String move) {
        int originalPlayer = game.getState().getCurrentPlayer();
        int originalStonesInDepot = game.getState().stonesIn(game.getBoard().getDepotOfPlayer(originalPlayer));
        MyMancalaGame copy = new MyMancalaGame(game);
        boolean anotherTurn = copy.selectSlot(move);
        int gain = copy.getState().stonesIn(copy.getBoard().getDepotOfPlayer(originalPlayer)) - originalStonesInDepot;
        if (anotherTurn) {
            return gain + recursiveGreedy(copy, originalPlayer);
        } else {
            copy.nextPlayer();
            return gain - recursiveGreedy(copy, originalPlayer);
        }
    }

    // Player ids: 0 for player1, 1 for player2
    // Depot for player1 has id 8
    // Depot for player2 has id 1
    // Player 1 selectable slots: 9, 10, 11, 12, 13, 14
    // Player 2 selectable slots: 2, 3, 4, 5, 6, 7

    // select count(*) from boardstate
    // select * from boardstate
    // select count(*) from chosen_slots
    // select * from chosen_slots
    // select * from chosen_slots where boardstate_id = 1715520


    @Override
    public MancalaAgentAction doTurn(int computationTime, MancalaGame game) {
        long start = System.currentTimeMillis();
        this.originalState = game.getState();

        MCTSTree root = new MCTSTree((MyMancalaGame) game, 0);

        String bookMove = null;
        List<Boardstate> boardstates = new ArrayList<>();
        boardstates = saveCurrentBoardstate(boardstates, game, "-1");
        Boardstate boardstate = boardstates.get(0);
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

        while ((System.currentTimeMillis() - start) < (computationTime*1000 - 100)) {
            MCTSTree best = treePolicy(root);
            WinState winning = defaultPolicy(best.game);
            backup(best, winning);
        }

        MCTSTree selected = root.getBestNode();
        System.out.println("Selected action " + selected.winCount + " / " + selected.visitCount);
        return new MyMancalaAgentAction(selected.action);
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

    private WinState defaultPolicy(MyMancalaGame game) {
        game = new MyMancalaGame(game); // copy original game
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
