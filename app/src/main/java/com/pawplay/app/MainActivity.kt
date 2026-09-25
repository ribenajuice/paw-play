package com.pawplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pawplay.app.data.BestScores
import com.pawplay.app.games.GameCatalog
import com.pawplay.app.home.HomeScreen
import com.pawplay.app.ui.theme.PawPlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BestScores.warmUp(this) // only starts the platform's background read of the small best-score file
        setContent {
            PawPlayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PawPlayApp()
                }
            }
        }
    }
}

/**
 * The whole app is this one swap: home screen, or whichever [MiniGame] is
 * active. Each game is responsible for its own internal navigation; this
 * level only knows "which tile did they tap" and "did they ask to leave".
 */
@Composable
private fun PawPlayApp() {
    var activeGameId by remember { mutableStateOf<String?>(null) }
    val activeGame = GameCatalog.games.find { it.id == activeGameId }

    if (activeGame == null) {
        HomeScreen(
            games = GameCatalog.games,
            onGameSelected = { activeGameId = it.id },
        )
    } else {
        activeGame.content { activeGameId = null }
    }
}
