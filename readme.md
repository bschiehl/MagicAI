# MagicAI Agent - ReadMe

## Introduction
This protocol is a documentation of the project work, a summary of selected and implemented methods and the reasoning behind the implementation.

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

## State Model of the Game

### Game State Space

We assume a space $X$ of all possible states for which $x \in X$ is a realized state of the game. In the case of Mankala the state space $X \in \mathbb{N}^{14}$ results from the unification of the spaces $X_b$ with $D$ and describes the quantities of the stones in the respective houses. Here $X_b \in \mathbb{N}^{12}$ describes the board and $D \in \mathbb{N}^{2}$ the depots of each player. We can denote:

$$
x   = \begin{pmatrix}
x_1 \\
x_2 \\
\vdots \\
x_{12} \\
d_1 \\
d_2
\end{pmatrix} \in \mathbb{N}^{14}
$$

Visualized from the perspective of player one, the game state $x$ can be written as:

$$
x   = \begin{pmatrix}
& x_{7} & x_{8} & x_{9} & x_{10} & x_{11} & x_{12} \\
d_2 &&&&&&& d_1\\
& x_{6} & x_{5} & x_{4} & x_{3} & x_{2} & x_{1}\\
\end{pmatrix}
$$

Further we assume time $t \in \mathbb{N}$ to be discrete and to describe the number of moves which have been played where the players' turns are at even and odd numbers for player one and two respectively. Hence $x^t$ denotes the state of the game at time $t$.

### Game Moves

We define a move as an action $u_a \in U (x)$ taken by the player $a \in \{1,2\}$ which is selected from the space of all admissible actions $\mathcal{A}$. This action is defined as the selection of the house which stones shall be distributed according to the rules of the game:

$$
u_a =
\begin{cases}
X_b \to \{1,2,3,4,5,6 \}   \\
x_i \mapsto i \mod 6
\end{cases}
$$


We further define a mapping $f: X \cup U \to X^+$ which encodes a state transition induced by the action $u_a$ of player $a$:

$$
f(u_a,x) = \begin{cases}
X \cup U \to X^+   \\
(x,u_a) \mapsto x^+
\end{cases}
$$

for which the stones of the selected house are distributed individually among subsequent houses resp. depots in a counterclockwise fashion until no more stones are left. The future state for *regular* moves is defined as:
$$
x_{i-1}^+ =x_{i-1}+1 \hspace{1cm} \text{for houses} \\
d_{a}^+ =d_{a}+1 \hspace{1.7cm} \text{for depots}
$$

### Special moves

A **move repetition** is possible if the last stone of each distribution is laid into the player's own depot. This is always the case when $x_i=i$ for $i \in \{1,..,6 \}$ $\mod 2$. In the case of a move repetition the players depot count is increased by one and it is his move again.

A **capture** is possible if the last stone of each distribution is laid into one of the player's houses which is empty (i.e $x_i=0$).
This can be the case when:
- for a $x$ there is an $x_i=0$ and an $x_j=n$ with $j>i$ such that $n=j-i$.
- for a $x$ there is an $x_i=n$ and an $x_j=0$ with $j>i$ such that $12-j+i = n$.

In the case of a capture the player's depot count is increased by one *plus* the number of the stones in the opponents opposite house which are captured. Herein two houses are considered to be *opposite* if their index numbers sum up to 13.  Afterwards it is his opponent's turn to play.


### Move evaluation

An evaluation function is a mapping $v: X \cup U \to \mathbb{N}$ that returns for each state of the game and the respective action the maximal number of stones that could be added to the own depot.

$$
v(u_a,x) = \begin{cases}
X \cup U \to \mathbb{N}   \\
(x,u_a) \mapsto \max_{x^+} d_a(x^+) - d_a(x)
\end{cases}
$$

and with $x^+ = f(x,u_a)$ and $i$ as the selected slot this yields:

$$
v(u_a,x) = \begin{cases}
X \cup U \to \mathbb{N}   \\
(x,u_a) \mapsto \max_{i} d_a(f_i) - d_a(x)
\end{cases}
$$

Note that this evaluation can be performed for each *sub-move* of the move (including all repetitions) such as the value of the move $V$ can be defined as

$$
V = \sum_k v_k \hspace{1cm} \text{where }k \text{ is the length of the move}
$$

To get the *maximal length* of the move an iteration over all possible repetition combinations has to be performed. However it is not the case that the move with the highest length also secures the highest gain in value since a capture is potentially more worth than many move repetitions.

### Move selection

If we interpret the game as a path selection problem, we know from dynamical programming that each sub-path of the optimal path is in itself optimal. Therefore we can say, that for any random game situation $x$ the best move is the one that maximizes the current increase of the depot for each player taking as given the resulting opponent's path-optimization problem thereafter. If this happens, there is no other path that will yield a better total outcome for this player. From this it follows, that the goal for each round of the game is to select the move (with all including sub-moves) that will yield the maximum possible gain for the player in turn taking into consideration the game situation that follows from chosen move.

However this implies that for each move the resulting state $x^+$ has to be incorporated into the analysis as well since it is important to secure that the opposite player cannot benefit from the current game situation. Therefore the true value function for each move has to be amended to:

$$
V=v(x,u_a) - v(x^+,u_b)
$$

as the game situation is symmetric, both opponents can use the same value function and gains to one player can be interpreted as losses to the other player.


### Edge Case Analysis

Based on the provided state space model some edge cases were analyzed which might occur during the game. These situations are critical for the outcome of the game and needed to be studied more carefully.

**Maximal Repetition case** was analyzed for the following game situation

$$
x   = \begin{pmatrix}
& x_{7} & x_{8} & x_{9} & x_{10} & x_{11} & x_{12} \\
d_2 &&&&&&& d_1\\
& 6 & 5 & 4 & 3 & 2 & 1\\
\end{pmatrix}
$$

This situation shows, that up to 17 repetitions can be played when the moves are selected correctly. In this case the optimal selection requires that a *repetition* is played from right-to-left which means that whenever possible the slot to the right is selected when more than one repetitions are available. By that, future repetition possibilities are never destroyed.

**Capture and Repetition case** was analyzed for the following game situation

$$
x   = \begin{pmatrix}
& x_{7} & x_{8} & x_{9} & x_{10} & x_{11} & x_{12} \\
d_2 &&&&&&& d_1\\
& 1 & 0 & 4 & 3 & 2 & 1\\
\end{pmatrix}
$$

This situation shows that whenever there is a capture *and* repetition possibility it does not matter to play the repetitions first as long as the captures are not destroyed.

**Capture or Repetition case** was analyzed for the following game situation

$$
x   = \begin{pmatrix}
& x_{7} & x_{8} & x_{9} & x_{10} & x_{11} & x_{12} \\
d_2 &&&&&&& d_1\\
& 1 & 5 & 5 & 1 & 0 & 5\\
\end{pmatrix}
$$

This situation shows that when either a capture *or* a repetition can be played, the move selection will be based upon the amount of the captured stones, in this case $x_{11}$.

All in all these considerations led us to define the following agent requirements.

## Agent requirements

The literature review shows, that the performance of the MCTS can be improved significantly with the mere implementation of a few simple heuristics. However it becomes apparent from the game observation that primitive "human" moves which have huge gains and can be seen easily are often overlooked by the MCTS agent.

Therefore the agent should perform an improved MCTS search to find its next move, but it should also be equipped with an evaluation function as introduced above to counteract its weaknesses.

Moreover, additional improvements can be made when an opening and endgame book is used for standardized game situations.


- During the opening: statistically best moves can be used or partially included in the move selection
- During the endgame: analogous to the opening
- In a game situation where it can be captured or repeated the value function should be used or partially included in the move selection
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

- Check whether there is a capture or repeat possibility
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

This method uses a copy of the current game state to iteratively compare all possible moves and follow all repetitions to arrive at potential gains for each combination of moves. The first house of the path with the highest gain is saved and suggested as the optimal move. This method does also consider the move situation after the player's turn as it calls itself from the perspective of the opponent to deduct his potential maximal gain for each game state left behind.


### Database Moves

The database includes winrates for potential moves in particular board states which have been collected by simulation of the game beforehand. If a board situation with available data occurs during the game it is considered only if this board situation has at least 20 entries in the database. In this case the move (out of the possible six) with the highest historical winrate is selected and provided as an output.

As the database is relevant in the early and late stages of the game it is considered according to the following weight $\omega_b$ that is derived from $s$ the total cumulative number of stones left in the game:

$$
\omega_b(s) = \frac{7}{12960}  s^2 - \frac{13}{360} s + \frac{4}{5}
$$

The resulting curve is a parabolic cylinder that has it's minimum at $s \approx 33$ and will yield higher weights for significantly higher and lower stone numbers. This makes sense as for a starting value of $s=6\times12=72$ we get lower significance for the book moves in the middle game.

### Incorporation into MCTS

Both components (greedy heuristic and book moves) are incorporated in the Monte Carlo Tree Search which was already provided by the default agent in the following manner:
- with a probability of $\omega_b(s)$ the best book move is searched for and saved locally
- the greedy heuristic is used to derive the move with the highest gain
- the seeds for the MCTS are calculated from the book move (if availavle) and the heuristic move


Here the seeds are set to

$$
\sigma = 1- 0.1\delta
$$

for $\delta$ being the difference of the considered move to the best move provided by the greedy heuristic and additionally

$$
\sigma = 1- 0.1\delta + 0.5
$$

if the considered move equals the book move.



## Agent evaluation



To be able to assess the quality of the implemented agent the same procedure was applied which was already used collect data for the game database. The implemented agent was scheduled to compete against the provided standard MCTS agent and delivered the following results:
- stat 1
- stat 2
