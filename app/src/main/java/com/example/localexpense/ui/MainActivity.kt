package com.example.localexpense.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localexpense.ui.theme.LocalExpenseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: MainViewModel = hiltViewModel()
            // v1.9.5 优化：使用 collectAsStateWithLifecycle 替代 collectAsState
            // 在 Activity 进入后台时自动停止收集，节省系统资源
            val state by vm.state.collectAsStateWithLifecycle()

            LocalExpenseTheme {
                MainScreen(
                    state = state,
                    onIntent = { vm.handleIntent(it) },
                    onAddExpense = { vm.handleIntent(UserIntent.AddExpense(it)) },
                    onDeleteExpense = { vm.handleIntent(UserIntent.DeleteExpense(it)) },
                    onSearch = { vm.handleIntent(UserIntent.Search(it)) },
                    onSelectCalendarDate = { vm.handleIntent(UserIntent.SelectCalendarDate(it)) },
                    onCalendarMonthChange = { vm.handleIntent(UserIntent.SetCalendarMonth(it.timeInMillis)) },
                    onStatsPeriodChange = { vm.handleIntent(UserIntent.SetStatsPeriod(it)) },
                    onStatsDateChange = { vm.handleIntent(UserIntent.SetStatsDate(it.timeInMillis)) },
                    onSaveBudget = { vm.handleIntent(UserIntent.SaveBudget(it)) },
                    onAddCategory = { vm.handleIntent(UserIntent.AddCategory(it)) },
                    onDeleteCategory = { vm.handleIntent(UserIntent.DeleteCategory(it)) },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onImportData = { uri -> vm.handleIntent(UserIntent.ImportData(uri)) },
                    onClearAllData = { vm.handleIntent(UserIntent.ClearAllData) },
                    onClearError = { vm.handleIntent(UserIntent.ClearError) }
                )
            }
        }
    }
}
