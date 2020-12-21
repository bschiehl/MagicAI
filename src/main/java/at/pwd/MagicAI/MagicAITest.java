package at.pwd.MagicAI;
import at.pwd.MagicAI.mancala.MyMancalaGame;
import at.pwd.MagicAI.mancala.agent.MyMancalaAgentAction;
import at.pwd.boardgame.game.base.WinState;
import at.pwd.boardgame.game.mancala.MancalaGame;
import at.pwd.boardgame.game.mancala.MancalaState;
import at.pwd.boardgame.game.mancala.agent.MancalaAgent;
import at.pwd.boardgame.game.mancala.agent.MancalaAgentAction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MagicAITest implements MancalaAgent {
    private Random r = new Random();
    private MancalaState originalState;
    private static final double C = 1.0f/Math.sqrt(2.0f);
    private static Connection connection;

    private class MCTSTree {
        private int visitCount;
        private int winCount;

        private MyMancalaGame game;
        private WinState winState;
        private MCTSTree parent;
        private List<MCTSTree> children;
        String action;

        MCTSTree(MyMancalaGame game) {
            this.game = game;
            this.children = new ArrayList<>();
            this.winState = game.checkIfPlayerWins();
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
                double currentValue =  wC/vC + C*Math.sqrt(2*Math.log(visitCount) / vC);


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

        MCTSTree move(String action) {
            MyMancalaGame newGame = new MyMancalaGame(this.game);
            if (!newGame.selectSlot(action)) {
                newGame.nextPlayer();
            }

            MCTSTree tree = new MCTSTree(newGame);
            tree.action = action;
            tree.parent = this;

            this.children.add(tree);

            return tree;
        }
    }

    // Player ids: 0 for player1, 1 for player2
    // Depot for player1 has id 8
    // Depot for player2 has id 1
    // Player 1 selectable slots: 9, 10, 11, 12, 13, 14
    // Player 2 selectable slots: 2, 3, 4, 5, 6, 7

    public MagicAITest () {
        try {
            Class driver = Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
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
        PreparedStatement selectBoardstate = null;
        PreparedStatement updateBoardstateTimesSeen = null;
        PreparedStatement insertBoardstate = null;
        PreparedStatement selectChosenSlots = null;
        PreparedStatement insertChosenSlots = null;
        PreparedStatement updateChosenSlotsTimesWon = null;
        PreparedStatement updateChosenSlotsTimesLost = null;
        for (Boardstate boardstate: statesAndSlots) {
            try {
                selectBoardstate = connection.prepareStatement("SELECT id, times_seen FROM boardstate WHERE slot1 = ?" +
                        "AND slot2 = ? AND slot3 = ? AND slot4 = ? AND slot5 = ? AND " +
                        "slot6 = ? AND opponent_slot1 = ? AND opponent_slot2 = ? AND opponent_slot3 = ? AND " +
                        "opponent_slot4 = ? AND opponent_slot5 = ? AND opponent_slot6 = ?");
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
                    updateBoardstateTimesSeen = connection.prepareStatement("UPDATE boardstate SET " +
                            "times_seen = ? WHERE id = ?");
                    updateBoardstateTimesSeen.setInt(1, timesSeen + 1);
                    updateBoardstateTimesSeen.setLong(2, boardstateId);
                    updateBoardstateTimesSeen.executeUpdate();
                } else {
                    insertBoardstate = connection.prepareStatement("INSERT INTO boardstate VALUES " +
                            "(null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)");
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

                selectChosenSlots = connection.prepareStatement("SELECT id, times_won, times_lost FROM" +
                        " chosen_slots WHERE boardstate_id = ? AND chosen_slot = ?");
                selectChosenSlots.setLong(1, boardstateId);
                selectChosenSlots.setInt(2, boardstate.chosen_slot);
                resultSet = selectChosenSlots.executeQuery();
                if (resultSet.last()) {
                    int chosenSlotsId = resultSet.getInt("id");
                    if (hasWon) {
                        int timesWon = resultSet.getInt("times_won");
                        updateChosenSlotsTimesWon = connection.prepareStatement("UPDATE chosen_slots SET " +
                                "times_won = ? WHERE id = ?");
                        updateChosenSlotsTimesWon.setInt(1, timesWon + 1);
                        updateChosenSlotsTimesWon.setInt(2, chosenSlotsId);
                        updateChosenSlotsTimesWon.executeUpdate();
                    } else {
                        int timesLost = resultSet.getInt("times_lost");
                        updateChosenSlotsTimesLost = connection.prepareStatement("UPDATE chosen_slots SET " +
                                "times_lost = ? WHERE id = ?");
                        updateChosenSlotsTimesLost.setInt(1, timesLost + 1);
                        updateChosenSlotsTimesLost.setInt(2, chosenSlotsId);
                        updateChosenSlotsTimesLost.executeUpdate();
                    }
                } else {
                    insertChosenSlots = connection.prepareStatement("INSERT INTO chosen_slots VALUES " +
                            "(null, ?, ?, ?, ?)");
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

    // select count(*) from boardstate
    // select * from boardstate
    // select count(*) from chosen_slots
    // select * from chosen_slots
    @Override
    public MancalaAgentAction doTurn(int computationTime, MancalaGame game) {
        long start = System.currentTimeMillis();
        this.originalState = game.getState();

        MCTSTree root = new MCTSTree((MyMancalaGame) game);

        try {
            connection = DriverManager.getConnection("jdbc:h2:./data", "", "");
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        if (connection != null) {
            System.out.println("connection established");
        }

        

        while ((System.currentTimeMillis() - start) < (computationTime*1000 - 100)) {
            MCTSTree best = treePolicy(root);
            WinState winning = defaultPolicy(best.game);
            backup(best, winning);
        }

        MCTSTree selected = root.getBestNode();
        System.out.println("Selected action " + selected.winCount + " / " + selected.visitCount);

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
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

        return best.move(legalMoves.get(r.nextInt(legalMoves.size())));
    }

    private WinState defaultPolicy(MyMancalaGame game) {
        game = new MyMancalaGame(game); // copy original game
        WinState state = game.checkIfPlayerWins();

        List<Boardstate> statesAndSlots = new ArrayList<>();

        while(state.getState() == WinState.States.NOBODY) {
            String play;
            do {
                List<String> legalMoves = game.getSelectableSlots();
                play = legalMoves.get(r.nextInt(legalMoves.size()));

                //saveCurrentBoardstate(statesAndSlots, game, play);

            } while(game.selectSlot(play));
            game.nextPlayer();
            state = game.checkIfPlayerWins();
        }

        if (state.getState() == WinState.States.SOMEONE) {
            //saveToDatabase(statesAndSlots, state);
        }

        return state;
    }

    @Override
    public String toString() {
        return "MagicAI Agent";
    }
}
