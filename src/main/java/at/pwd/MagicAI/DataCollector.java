package at.pwd.MagicAI;

import at.pwd.MagicAI.mancala.MyMancalaBoard;
import at.pwd.MagicAI.mancala.MyMancalaGame;
import at.pwd.MagicAI.mancala.MyMancalaState;
import at.pwd.MagicAI.mancala.agent.MyMancalaAgentAction;
import at.pwd.boardgame.game.agent.AgentAction;
import at.pwd.boardgame.game.base.WinState;
import at.pwd.boardgame.game.mancala.MancalaGame;
import at.pwd.boardgame.game.mancala.MancalaState;
import at.pwd.boardgame.game.mancala.agent.MancalaAgent;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DataCollector {
    private static Connection connection;

    private static String getSlotId(int number, int currentPlayer) {
        if (currentPlayer == 0) {
            return "" + (15 - number);
        } else {
            return "" + (1 + number);
        }
    }

    private static int getSlotNumber(String id) {
        int slotId = Integer.parseInt(id);
        if (slotId < 8) {
            return slotId -1;
        } else {
            return 15 - slotId;
        }
    }

    private static class Boardstate {
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

    private static List<Boardstate> saveCurrentBoardstate(List<Boardstate> boardstates, MancalaGame game, String chosenSlot) {
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

    private static void saveToDatabase(List<Boardstate> statesAndSlots, WinState state) {
        System.out.println("Saving " + (statesAndSlots.size()) + " boardstates to database");
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

    public static void main(String[] args) {
        try {
            Class driver = Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            connection = DriverManager.getConnection("jdbc:h2:./data", "", "");
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        if (connection != null) {
            System.out.println("connection established");
        }

        MyMancalaBoard myMancalaBoard = new MyMancalaBoard();
        myMancalaBoard.setStonesPerSlot(6);

        List<MyMancalaBoard.Slot> slots = new ArrayList<>();
        List<MyMancalaBoard.PlayerDepot> depots = new ArrayList<>();

        MyMancalaBoard.PlayerDepot depot1 = new MyMancalaBoard.PlayerDepot();
        depot1.id = "8";
        depot1.next = "7";
        depot1.player = 0;
        depots.add(depot1);

        MyMancalaBoard.PlayerDepot depot2 = new MyMancalaBoard.PlayerDepot();
        depot2.id = "1";
        depot2.next = "14";
        depot2.player = 1;
        depots.add(depot2);
        myMancalaBoard.depots = depots;

        MyMancalaBoard.Slot slot2 = new MyMancalaBoard.Slot();
        slot2.id = "2";
        slot2.next = "1";
        slot2.belongs = 1;
        slot2.enemy = "14";
        slots.add(slot2);

        MyMancalaBoard.Slot slot3 = new MyMancalaBoard.Slot();
        slot3.id = "3";
        slot3.next = "2";
        slot3.belongs = 1;
        slot3.enemy = "13";
        slots.add(slot3);

        MyMancalaBoard.Slot slot4 = new MyMancalaBoard.Slot();
        slot4.id = "4";
        slot4.next = "3";
        slot4.belongs = 1;
        slot4.enemy = "12";
        slots.add(slot4);

        MyMancalaBoard.Slot slot5 = new MyMancalaBoard.Slot();
        slot5.id = "5";
        slot5.next = "4";
        slot5.belongs = 1;
        slot5.enemy = "11";
        slots.add(slot5);

        MyMancalaBoard.Slot slot6 = new MyMancalaBoard.Slot();
        slot6.id = "6";
        slot6.next = "5";
        slot6.belongs = 1;
        slot6.enemy = "10";
        slots.add(slot6);

        MyMancalaBoard.Slot slot7 = new MyMancalaBoard.Slot();
        slot7.id = "7";
        slot7.next = "6";
        slot7.belongs = 1;
        slot7.enemy = "9";
        slots.add(slot7);

        MyMancalaBoard.Slot slot9 = new MyMancalaBoard.Slot();
        slot9.id = "9";
        slot9.next = "8";
        slot9.belongs = 0;
        slot9.enemy = "7";
        slots.add(slot9);

        MyMancalaBoard.Slot slot10 = new MyMancalaBoard.Slot();
        slot10.id = "10";
        slot10.next = "9";
        slot10.belongs = 0;
        slot10.enemy = "6";
        slots.add(slot10);

        MyMancalaBoard.Slot slot11 = new MyMancalaBoard.Slot();
        slot11.id = "11";
        slot11.next = "10";
        slot11.belongs = 0;
        slot11.enemy = "5";
        slots.add(slot11);

        MyMancalaBoard.Slot slot12 = new MyMancalaBoard.Slot();
        slot12.id = "12";
        slot12.next = "11";
        slot12.belongs = 0;
        slot12.enemy = "4";
        slots.add(slot12);

        MyMancalaBoard.Slot slot13 = new MyMancalaBoard.Slot();
        slot13.id = "13";
        slot13.next = "12";
        slot13.belongs = 0;
        slot13.enemy = "3";
        slots.add(slot13);

        MyMancalaBoard.Slot slot14 = new MyMancalaBoard.Slot();
        slot14.id = "14";
        slot14.next = "13";
        slot14.belongs = 0;
        slot14.enemy = "2";
        slots.add(slot14);
        myMancalaBoard.slots = slots;

        MyMancalaState myMancalaState = new MyMancalaState(myMancalaBoard);
        myMancalaState.setCurrentPlayer(0);
        final MyMancalaGame originalGame = new MyMancalaGame(myMancalaState, myMancalaBoard);

        MancalaAgent player1 = new MagicAITest();
        MancalaAgent player2 = new MagicAITest();
        MancalaAgent currentPlayer = player1;
        MyMancalaGame game;
        List<Boardstate> boardstates = new ArrayList<>();

        int gamesToPlay = 150;
        int player1WonGames = 0;
        int player2WonGames = 0;

        // initial boardstate id = 1715520
        for (int i = 0; i < gamesToPlay; i++) {
            System.out.println("Game number " + (i+1));
            game = new MyMancalaGame(originalGame);

            while (game.checkIfPlayerWins().getState() == WinState.States.NOBODY) {
                System.out.println("Turn of player " + (game.getState().getCurrentPlayer() +1));
                MyMancalaAgentAction action = (MyMancalaAgentAction) currentPlayer.doTurn(10, game);
                System.out.println("Player " + (game.getState().getCurrentPlayer() +1) + " chose slot with id " + action.getId());
                boardstates = saveCurrentBoardstate(boardstates, game, action.getId());
                AgentAction.NextAction nextAction = action.applyAction(game);
                if (nextAction == AgentAction.NextAction.NEXT_PLAYER) {
                    game.nextPlayer();
                    if (currentPlayer.equals(player1)) {
                        currentPlayer = player2;
                    } else {
                        currentPlayer = player1;
                    }
                }
            }

            if (game.checkIfPlayerWins().getState() == WinState.States.SOMEONE) {
                saveToDatabase(boardstates, game.checkIfPlayerWins());
                System.out.println("Player " + (game.checkIfPlayerWins().getPlayerId() + 1) + " won the game!");
                if (game.checkIfPlayerWins().getPlayerId() == 0) {
                    player1WonGames++;
                } else {
                    player2WonGames++;
                }
                System.out.println();
            }
            boardstates.clear();
        }

        System.out.println("Player1 (" + player1.toString() + ") won " + player1WonGames + " of " + gamesToPlay + " total Games");
        System.out.println("Player2 (" + player2.toString() + ") won " + player2WonGames + " of " + gamesToPlay + " total Games");

        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }
}
