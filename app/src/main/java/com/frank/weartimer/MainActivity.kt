package com.frank.weartimer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StartTimerScreen { timerLength ->
                val intent = Intent(this, CountdownActivity::class.java)
                intent.putExtra("timer_length", timerLength)
                startActivity(intent)
            }
        }
    }
}

@Composable
fun StartTimerScreen(onStart: (Int) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = { onStart(5) }, // 5 seconds for now
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(0.8f)
                .height(56.dp)
        ) {
            Text(
                "Start Timer",
                fontSize = 22.sp
            )
        }
    }
}