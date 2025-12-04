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
                    onAddExpense = { vm.addExpense(it) },
                    onDeleteExpense = { vm.deleteExpense(it) },
                    onSearch = { vm.search(it) },
                    onSelectCalendarDate = { vm.selectCalendarDate(it) },
                    onCalendarMonthChange = { vm.setCalendarMonth(it) },
                    onStatsPeriodChange = { vm.setStatsPeriod(it) },
                    onStatsDateChange = { vm.setStatsDate(it) },
                    onSaveBudget = { vm.saveBudget(it) },
                    onAddCategory = { vm.addCategory(it) },
                    onDeleteCategory = { vm.deleteCategory(it) },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onClearError = { vm.clearError() }
                )
            }
        }
    }
}
