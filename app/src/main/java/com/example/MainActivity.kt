package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.db.AppDatabase
import com.example.data.repository.SermonRepository
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RecordingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SermonViewModel
import com.example.ui.viewmodel.SermonViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: SermonViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize local SQLite Database & Repository orchestration
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = SermonRepository(database.sermonDao())

        // 2. Provision ViewModel via lightweight Constructor Injection Factory
        val factory = SermonViewModelFactory(applicationContext, repository)
        viewModel = ViewModelProvider(this, factory)[SermonViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainAppHost(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppHost(viewModel: SermonViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Request active audio capture permissions dynamically on entry
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                context,
                "Microphone recording permission is required to capture sermons locally.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Capture shared flow view-events (like toasts, status success updates) and fire them to UI
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize()
        ) {
            // Screen 1: Dashboard Home
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = { jobId ->
                        navController.navigate("detail/$jobId")
                    },
                    onNavigateToRecord = {
                        // Check microphone recording permissions before entering capturing studio
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasMic) {
                            navController.navigate("record")
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }

            // Screen 2: Voice Capture Recording
            composable("record") {
                RecordingScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            // Screen 3: Work Desk & Outline Editor
            composable(
                route = "detail/{jobId}",
                arguments = listOf(navArgument("jobId") { type = NavType.StringType })
            ) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId")
                LaunchedEffect(jobId) {
                    viewModel.selectSermon(jobId)
                }
                DetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
