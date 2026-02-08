package com.example.personalassistant

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personalassistant.ui.theme.PersonalAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PersonalAssistantTheme {
                HODDashboard()
            }
        }
    }
}

@Composable
fun HODDashboard(viewModel: HODViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // Permission Handler
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) viewModel.startListening()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Make screen scrollable
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎓 Personal Assistant", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))

        Spacer(modifier = Modifier.height(24.dp))

        // 1. STATUS INDICATOR
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isListening) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (uiState.isListening) "👂 LISTENING ACTIVE" else "⏸️ PAUSED",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isListening) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(uiState.status)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. CONTROL BUTTONS
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    if (uiState.isListening) viewModel.stopListening()
                    else launcher.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text(if (uiState.isListening) "STOP" else "START DAY")
            }

            // Only show Summary button if not listening (end of day)
            Button(
                onClick = { viewModel.generateDailySummary() },
                enabled = !uiState.isListening && !uiState.isLoadingAI,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                if (uiState.isLoadingAI) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("SUMMARIZE")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. AI SUMMARY DISPLAY
        if (uiState.summary.isNotEmpty()) {
            Text("📝 Daily Summary", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Text(
                    text = uiState.summary,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp
                )
            }
        } else {
            // Show raw log if no summary yet
            Text("📜 Raw Transcript (Preview)", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Text(
                text = uiState.dailyLog.takeLast(200) + "...", // Show only last bit
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}