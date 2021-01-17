# Strategy Game Programming - MagicAI Agent

## Introduction
This protocol is a documentation of the project work, a summary of selected and implemented methods and the reasoning behind the implementation.
[Click here for a longer version including mathematical background.](https://hackmd.io/T9SSf5P8RFO-38MDFC_6qA?view)

## Motivation
For the implementation of the agent several approaches were considered to improve its performance versus the already provided standardized MCTS agent. The ideas for the alteration of the move selection algorithms were inspired by:

- lecture notes
- self-experiments and game observation
- data collection and statistical analysis
- literature review

where additional information on each of these topics is provided in the following dedicated subsections.


### Lecture Notes

The majority of the lecture consisted of a presentation of key algorithms and most notable heuristics to increase their performance. Here we relied on the the lecture video and the summaries of the team members for a first evaluation.

Besides the content provided in the main sections of the course, especially part 4 "related topics" promised additional insights as broader questions about potential approaches could be asked. Especially *game and decision theory* were taken into account to conceptualize the game situation and the move selection problem for each agent. A model was developed which can be used for simulation and evaluation of the game dynamics. Moreover, some additional ideas were included which based on the comments about *military strategy and tactics*. Both methods combined allowed us to treat the problem form a strategic game perspective and reason about particular situations which can occur during the game.

### Self-experiments and Game Observation

A key part of the problem analysis consisted of actually playing the game manually to
- learn and internalize the rules
- get an intuition for the game dynamics
- identify critical game situations
- evaluate move possibilities
- understand the reasoning for the MCTS move selection and its potential weaknesses

This observation phase of the project was very helpful to assess which of the ideas generated in the previous phase were most promising. It also provided insights to which extent the MCTS was limited in its capabilities for the particularities of the Mancala game.

### Data collection and Statistical analysis

In order to make the theories of the previous phase deducted from intuition more robust a procedure was developed which allowed us to test our hypothesis by quantification of data. A database was programmed to incorporate a history of particular game states and the game result for a selected move in these situations. These game statistics were collected by implementing a game simulation which automatically provided data into the database by letting the programmed agents compete against each other. For a large enough size of simulations this resulted in the creation of an opening book and an endgame book as these game situations occurred most often and therefore the statistics were significant.

### Literature Review

The literature review was based on the references provided in the lecture and additional sources included therein, most notably:
- Browne at al: A Survey of Monte Carlo Tree Search Methods (2012)
- Ramanujan and Selman: Trade-Offs in Sampling-Based Adversarial Planning (2011)
- Lanctot et al: Monte Carlo Tree Search with Heuristic Evaluations using Implicit Minmax Backups (2014)
- Gelly and Silver: Monte Carlo Tree Search and Rapid Action Value Estimation in Computer Go

This literature review confirmed the decisions made about possible heuristic implementations but also provided additional insights into the limitations of the MCTS which further supported the development of the State Model of the game for future analysis

## Agent requirements

The literature review shows, that the performance of the MCTS can be improved significantly with the mere implementation of a few simple heuristics. However it becomes apparent from the game observation that primitive "human" moves which have huge gains and can be seen easily are often overlooked by the MCTS agent.

Therefore the agent should perform an improved MCTS search to find its next move, but it should also be equipped with an evaluation function as introduced above to counteract its weaknesses.

Moreover, additional improvements can be made when an opening and endgame book is used for standardized game situations.


- During the opening: statistically best moves can be used or partially included in the move selection
- During the endgame: analogous to the opening
- In a game situation where capturing or repeating is possible the value function should be used or partially included in the move selection
- After each move to mitigate the possibility of attractive moves for the opponent the extended value function should be used or partially included in the move selection.

### Move requirements
We arrive at the following **requirements** for the move selection

- The agent should be able to recognize obvious good moves:
  - by taking them from the books
  - by trying to repeat moves as much as possible
  - by trying to capture opponent's stones
- The agent should be able to prevent leaving situations for his opponent where he can
  - repeat moves
  - capture stones
- The agent can incorporate decisions made based on value functions and the improved MCTS


### Move selection algorithm

- Calculate the evaluation of own move
- Calculate the evaluation of the opponent's move
- Calculate the total move valuation
- Compare the game situation with the database
- Incorporate above decisions into the MCTS



## Implementation

The above approach was implemented into the search seeding phase of the MCTS. This seed affects the selection phase of the algorithm and manages to give more attention to the moves which are evaluated as best for further analysis. In total the improved MCTS consists of 2 components:

- A greedy heuristic
- Database Moves

### Greedy Heuristic

The greedy heuristic implements the value-function based approach described above. It is a recursive method that takes into account
- how many repetitions a player can play
- his maximum gain in the total move and
- the 'loss' associated with the subsequent move by the opponent

This method uses a copy of the current game state to iteratively compare all possible moves and follow all repetitions to arrive at potential gains for each combination of moves. For each possible move, it returns a value indicating how valuable the move is. This method does also consider the move situation after the player's turn as it calls itself from the perspective of the opponent to deduct his potential maximal gain for each game state left behind.


### Database Moves

The database includes winrates for potential moves in particular board states which have been collected by simulation of the game beforehand. If a board situation with available data occurs during the game it is considered only if this board situation has at least 20 entries in the database. In this case the move (out of the possible six) with the highest historical winrate is selected and provided as an output.

As the database is relevant in the early and late stages of the game it is considered according to a weight W that is derived from the total cumulative number of stones left in the game.

The resulting curve is a parabolic cylinder that has it's minimum approximately at 33 and will yield higher weights for significantly higher and lower stone numbers. This makes sense as for a starting value of 6 * 12 = 72 we get lower significance for the book moves in the middle game.

### Incorporation into MCTS

Both components (greedy heuristic and book moves) are incorporated in the Monte Carlo Tree Search which was already provided in the following manner:
- with a probability of W the best book move is searched for and saved locally
- the greedy heuristic is used to derive the move with the highest gain
- the seeds for the MCTS are calculated from the book move (if availavle) and the heuristic move


The seeds are set to

s = 1 - 0.1 * d

for d being the difference of the considered move to the best move provided by the greedy heuristic and additionally

s = s + 0.5


if the considered move equals the book move.



## Agent evaluation



To be able to assess the quality of the implemented agent the same procedure was applied which was already used collect data for the game database. The implemented agent played a total of 34 one minute turn games against the default MCTS agent, 6 of them as player 1 and 28 as player 2. Of the 6 games as player 1, the implemented agent won all of them. Of the 28 games as player 2, the implemented agent won 21.
