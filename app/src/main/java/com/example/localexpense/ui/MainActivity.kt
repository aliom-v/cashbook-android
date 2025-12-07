package com.example.localexpense.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localexpense.ui.theme.LocalExpenseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: MainViewModel = viewModel(
                factory = MainViewModel.factory(applicationContext)
            )
            val state by vm.state.collectAsState()

            LocalExpenseTheme {
                MainScreen(
                    state = state,
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
