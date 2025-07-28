package com.frank.weartimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.content.ComponentName
import androidx.wear.tiles.TileService

class TicTacToeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if we should reset the game (from tile tap)
        val prefs = getSharedPreferences("tictactoe_scores", MODE_PRIVATE)
        val shouldResetGame = prefs.getBoolean("should_reset_game", false)
        
        // Clear the flag after reading it
        if (shouldResetGame) {
            prefs.edit().putBoolean("should_reset_game", false).apply()
        }
        
        setContent {
            MaterialTheme {
                TicTacToePager(shouldResetGame)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TicTacToePager(shouldResetGame: Boolean) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("tictactoe_scores", Context.MODE_PRIVATE)
    
    // Load scores from SharedPreferences to preserve them across activity restarts
    var xScore by rememberSaveable { mutableStateOf(prefs.getInt("xScore", 0)) }
    var oScore by rememberSaveable { mutableStateOf(prefs.getInt("oScore", 0)) }
    var startingPlayer by rememberSaveable { mutableStateOf("X") }

    // Save scores to SharedPreferences whenever they change
    LaunchedEffect(xScore, oScore) {
        prefs.edit().putInt("xScore", xScore).putInt("oScore", oScore).commit()

        // Request tile update immediately
        TileService.getUpdater(context).requestUpdate(ScoreboardTileService::class.java)
    }

    // Game state is now managed here and passed down
    var board by rememberSaveable { mutableStateOf(List(3) { MutableList(3) { "" } }) }
    var currentPlayer by rememberSaveable { mutableStateOf(startingPlayer) }
    var winner by rememberSaveable { mutableStateOf<String?>(null) }
    var isDraw by rememberSaveable { mutableStateOf(false) }
    var winningCells by rememberSaveable { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }

    // Also update tile when game state changes (for immediate feedback)
    LaunchedEffect(winner) {
        if (winner != null) {
            // Force immediate tile update when someone wins
            TileService.getUpdater(context).requestUpdate(ScoreboardTileService::class.java)
        }
    }

    // Reset game if coming from tile tap
    LaunchedEffect(shouldResetGame) {
        if (shouldResetGame) {
            // Reset game state
            board = List(3) { MutableList(3) { "" } }
            currentPlayer = startingPlayer
            winner = null
            isDraw = false
            winningCells = emptyList()
            // Navigate to game page
            pagerState.animateScrollToPage(0)
        }
    }

    fun checkWinner(board: List<List<String>>): Pair<String?, List<Pair<Int, Int>>> {
        // Rows and columns
        for (i in 0..2) {
            if (board[i][0] != "" && board[i][0] == board[i][1] && board[i][1] == board[i][2]) {
                return board[i][0] to listOf(Pair(i, 0), Pair(i, 1), Pair(i, 2))
            }
            if (board[0][i] != "" && board[0][i] == board[1][i] && board[1][i] == board[2][i]) {
                return board[0][i] to listOf(Pair(0, i), Pair(1, i), Pair(2, i))
            }
        }
        // Diagonals
        if (board[0][0] != "" && board[0][0] == board[1][1] && board[1][1] == board[2][2]) {
            return board[0][0] to listOf(Pair(0, 0), Pair(1, 1), Pair(2, 2))
        }
        if (board[0][2] != "" && board[0][2] == board[1][1] && board[1][1] == board[2][0]) {
            return board[0][2] to listOf(Pair(0, 2), Pair(1, 1), Pair(2, 0))
        }
        return null to emptyList()
    }

    fun checkDraw(board: List<List<String>>): Boolean {
        return board.all { row -> row.all { it != "" } } && winner == null
    }

    fun handleCellClick(row: Int, col: Int) {
        if (board[row][col] == "" && winner == null && !isDraw) {
            val newBoard = board.map { it.toMutableList() }.toMutableList()
            newBoard[row][col] = currentPlayer
            board = newBoard
            val (win, winCells) = checkWinner(newBoard)
            winner = win
            winningCells = winCells
            isDraw = checkDraw(newBoard)
            if (winner == null && !isDraw) {
                currentPlayer = if (currentPlayer == "X") "O" else "X"
            }
            if (winner != null) {
                if (winner == "X") xScore++
                if (winner == "O") oScore++
                // Force immediate tile update when someone wins
                TileService.getUpdater(context).requestUpdate(ScoreboardTileService::class.java)
            }
        }
    }

    fun restartGame() {
        // Alternate starting player after each game restart
        startingPlayer = if (startingPlayer == "X") "O" else "X"
        board = List(3) { MutableList(3) { "" } }
        currentPlayer = startingPlayer
        winner = null
        isDraw = false
        winningCells = emptyList()
    }

    fun resetScores() {
        xScore = 0
        oScore = 0
        startingPlayer = "X"
        board = List(3) { MutableList(3) { "" } }
        currentPlayer = startingPlayer
        winner = null
        isDraw = false
        winningCells = emptyList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState) { page ->
            when (page) {
                0 -> TicTacToeScreen(
                    board = board,
                    currentPlayer = currentPlayer,
                    winner = winner,
                    isDraw = isDraw,
                    winningCells = winningCells,
                    onCellClick = ::handleCellClick,
                    onRestart = ::restartGame
                )
                1 -> ScoreboardScreen(
                    xScore = xScore,
                    oScore = oScore,
                    onResetScores = ::resetScores
                )
            }
        }
        SimplePagerIndicator(
            currentPage = pagerState.currentPage,
            pageCount = 2,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
fun ScoreboardScreen(xScore: Int, oScore: Int, onResetScores: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Scoreboard", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        Row(
            modifier = Modifier.padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Player X", fontWeight = FontWeight.Bold)
                Text("$xScore", fontSize = 24.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(32.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Player O", fontWeight = FontWeight.Bold)
                Text("$oScore", fontSize = 24.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
            }
        }
        androidx.wear.compose.material.Button(
            onClick = onResetScores,
            modifier = Modifier
                .height(24.dp)
                .width(100.dp),
            shape = RoundedCornerShape(50)
        ) {
            Text("Reset Scores", fontSize = 12.sp)
        }
    }
}

@Composable
fun TicTacToeScreen(
    board: List<List<String>>,
    currentPlayer: String,
    winner: String?,
    isDraw: Boolean,
    winningCells: List<Pair<Int, Int>>,
    onCellClick: (Int, Int) -> Unit,
    onRestart: () -> Unit
) {
    val winColor = Color(0xFF90CAF9) // Light blue
    val winTextColor = Color(0xFF0D47A1) // Dark blue

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                winner != null -> Text(
                    text = "Player $winner wins!",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = winColor
                )
                isDraw -> Text(
                    text = "It's a draw!",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = Color.Yellow
                )
                else -> Text(
                    text = "Player $currentPlayer's turn",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            for (row in 0..2) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (col in 0..2) {
                        val isWinningCell = winningCells.contains(Pair(row, col))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isWinningCell) winColor else Color.DarkGray)
                                .clickable(enabled = board[row][col] == "" && winner == null && !isDraw) {
                                    onCellClick(row, col)
                                }
                        ) {
                            Text(
                                text = board[row][col],
                                fontSize = 20.sp,
                                color = if (isWinningCell) winTextColor else Color.White,
                                fontWeight = if (isWinningCell) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        androidx.wear.compose.material.Button(
            onClick = onRestart,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .height(24.dp)
                .width(100.dp),
            shape = RoundedCornerShape(50)
        ) {
            Text("Restart", fontSize = 12.sp)
        }
    }
}

@Composable
fun SimplePagerIndicator(currentPage: Int, pageCount: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .padding(2.dp)
                    .background(
                        color = if (index == currentPage) Color(0xFF90CAF9) else Color.LightGray,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TicTacToeScreenPreview() {
    MaterialTheme {
        TicTacToeScreen(
            board = List(3) { MutableList(3) { "" } },
            currentPlayer = "X",
            winner = null,
            isDraw = false,
            winningCells = emptyList(),
            onCellClick = { _, _ -> },
            onRestart = {}
        )
    }
} 