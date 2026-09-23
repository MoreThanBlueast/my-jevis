package com.jevis.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jevis.mobile.ui.TaskViewModel
import com.jevis.mobile.ui.JevisBackground
import com.jevis.mobile.ui.JevisBlue
import com.jevis.mobile.ui.screens.CreateTaskScreen
import com.jevis.mobile.ui.screens.SettingsScreen
import com.jevis.mobile.ui.screens.TaskDetailScreen
import com.jevis.mobile.ui.screens.TaskListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startRoute = intent.getStringExtra("start_route") ?: "tasks"
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = JevisBlue, background = JevisBackground, surface = JevisBackground)) {
                Surface(color = JevisBackground) {
                val nav = rememberNavController()
                val vm: TaskViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val entry by nav.currentBackStackEntryAsState()
                val current = entry?.destination?.route.orEmpty()
                Scaffold(containerColor = JevisBackground,
                    bottomBar = {
                        if (!current.startsWith("detail")) {
                            NavigationBar {
                                NavigationBarItem(current == "tasks", { nav.navigate("tasks") }, { Icon(Icons.Outlined.List, null) }, label = { Text("任务") })
                                NavigationBarItem(current == "create", { nav.navigate("create") }, { Icon(Icons.Outlined.AddCircle, null) }, label = { Text("新建") })
                                NavigationBarItem(current == "settings", { nav.navigate("settings") }, { Icon(Icons.Outlined.Settings, null) }, label = { Text("设置") })
                            }
                        }
                    }
                ) { padding ->
                    NavHost(nav, startDestination = startRoute, modifier = Modifier.padding(padding)) {
                        composable("tasks") {
                            TaskListScreen(
                                state = state,
                                refresh = vm::refresh,
                                create = { nav.navigate("create") },
                                open = { nav.navigate("detail/$it") },
                            )
                        }
                        composable("create") {
                            CreateTaskScreen(state.loading, state.error) { instruction, app, confirm ->
                                vm.create(instruction, app, confirm) { nav.navigate("detail/$it") }
                            }
                        }
                        composable("settings") { SettingsScreen() }
                        composable("detail/{id}") { backStack ->
                            val id = backStack.arguments?.getString("id").orEmpty()
                            TaskDetailScreen(id, state.selected, vm::load, vm::cancel, vm::resolveApproval) { nav.popBackStack() }
                        }
                    }
                }
                }
            }
        }
    }
}
