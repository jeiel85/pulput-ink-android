package io.pulpit.ink

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import io.pulpit.ink.data.db.AppDatabase
import io.pulpit.ink.data.repository.SermonRepository
import io.pulpit.ink.ui.screens.DetailScreen
import io.pulpit.ink.ui.screens.HomeScreen
import io.pulpit.ink.ui.screens.OnboardingScreen
import io.pulpit.ink.ui.screens.RecordingScreen
import io.pulpit.ink.ui.theme.MyApplicationTheme
import io.pulpit.ink.ui.viewmodel.SermonViewModel
import io.pulpit.ink.ui.viewmodel.SermonViewModelFactory

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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotification = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotification) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Request active audio capture & notification permissions dynamically on entry
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionMap ->
        val recordGranted = permissionMap[Manifest.permission.RECORD_AUDIO] ?: false
        if (recordGranted) {
            navController.navigate("record")
        } else {
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

    // Auto-transition to DetailScreen upon successful sermon recording completion
    LaunchedEffect(Unit) {
        viewModel.newJobFlow.collect { jobId ->
            navController.navigate("detail/$jobId") {
                popUpTo("home")
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val startDestination = if (viewModel.onboardingCompleted.value) "home" else "onboarding"
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            // Screen 0: First-launch onboarding
            composable("onboarding") {
                OnboardingScreen(
                    viewModel = viewModel,
                    onFinish = {
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            // Screen 1: Dashboard Home
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = { jobId ->
                        navController.navigate("detail/$jobId")
                    },
                    onNavigateToRecord = {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }

                        if (hasMic && hasNotification) {
                            navController.navigate("record")
                        } else {
                            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionsLauncher.launch(permissions.toTypedArray())
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
