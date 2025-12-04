package com.example.localexpense.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object IconUtil {
    private val iconMap = mapOf(
        "Restaurant" to Icons.Default.Restaurant,
        "ShoppingBag" to Icons.Default.ShoppingBag,
        "DirectionsCar" to Icons.Default.DirectionsCar,
        "SportsEsports" to Icons.Default.SportsEsports,
        "Home" to Icons.Default.Home,
        "LocalHospital" to Icons.Default.LocalHospital,
        "School" to Icons.Default.School,
        "MoreHoriz" to Icons.Default.MoreHoriz,
        "AccountBalance" to Icons.Default.AccountBalance,
        "CardGiftcard" to Icons.Default.CardGiftcard,
        "TrendingUp" to Icons.AutoMirrored.Filled.TrendingUp,
        "Work" to Icons.Default.Work,
        "Flight" to Icons.Default.Flight,
        "LocalCafe" to Icons.Default.LocalCafe,
        "Pets" to Icons.Default.Pets,
        "FitnessCenter" to Icons.Default.FitnessCenter,
        "Movie" to Icons.Default.Movie,
        "MusicNote" to Icons.Default.MusicNote,
        "Phone" to Icons.Default.Phone,
        "Wifi" to Icons.Default.Wifi,
        "LocalGasStation" to Icons.Default.LocalGasStation,
        "Build" to Icons.Default.Build,
        "Checkroom" to Icons.Default.Checkroom,
        "ChildCare" to Icons.Default.ChildCare,
        "Favorite" to Icons.Default.Favorite,
        "Star" to Icons.Default.Star,
        "SwapHoriz" to Icons.Default.SwapHoriz,
        "Redeem" to Icons.Default.Redeem,
        "Savings" to Icons.Default.Savings,
        "Payments" to Icons.Default.Payments
    )

    fun getIcon(name: String): ImageVector = iconMap[name] ?: Icons.Default.MoreHoriz

    fun getAllIcons(): Map<String, ImageVector> = iconMap
}
