package com.zigor.discountcard.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zigor.discountcard.data.repo.CardDraft
import com.zigor.discountcard.ui.card.CardDetailScreen
import com.zigor.discountcard.ui.card.CardEditScreen
import com.zigor.discountcard.ui.home.HomeScreen
import com.zigor.discountcard.ui.nfc.NfcScanScreen
import com.zigor.discountcard.ui.photo.PhotoCaptureScreen
import com.zigor.discountcard.ui.scan.ScanScreen

object Route {
    const val HOME = "home"
    const val SCAN = "scan"
    const val NFC = "nfc"
    const val EDIT = "edit"
    const val DETAIL = "detail"
    const val PHOTO = "photo"
    const val PHOTO_RESULT = "photo_result"
}

/** Черновик считанной карты передаётся между экранами в памяти процесса. */
object DraftHolder {
    @Volatile
    var pending: CardDraft? = null

    fun take(): CardDraft? {
        val value = pending
        pending = null
        return value
    }
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Route.HOME) {

        composable(Route.HOME) {
            HomeScreen(
                onOpenCard = { id -> navController.navigate("${Route.DETAIL}/$id") },
                onScanBarcode = { navController.navigate(Route.SCAN) },
                onScanNfc = { navController.navigate(Route.NFC) },
                onAddManual = {
                    DraftHolder.pending = null
                    navController.navigate("${Route.EDIT}?cardId=0")
                },
            )
        }

        composable(Route.SCAN) {
            ScanScreen(
                onBack = { navController.popBackStack() },
                onDraftReady = { draft ->
                    DraftHolder.pending = draft
                    navController.navigate("${Route.EDIT}?cardId=0") {
                        popUpTo(Route.SCAN) { inclusive = true }
                    }
                },
                onDuplicate = { id ->
                    navController.navigate("${Route.DETAIL}/$id") {
                        popUpTo(Route.SCAN) { inclusive = true }
                    }
                },
                onManualInput = {
                    DraftHolder.pending = null
                    navController.navigate("${Route.EDIT}?cardId=0") {
                        popUpTo(Route.SCAN) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.NFC) {
            NfcScanScreen(
                onBack = { navController.popBackStack() },
                onDraftReady = { draft ->
                    DraftHolder.pending = draft
                    navController.navigate("${Route.EDIT}?cardId=0") {
                        popUpTo(Route.NFC) { inclusive = true }
                    }
                },
                onDuplicate = { id ->
                    navController.navigate("${Route.DETAIL}/$id") {
                        popUpTo(Route.NFC) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = "${Route.EDIT}?cardId={cardId}",
            arguments = listOf(
                navArgument("cardId") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            val cardId = entry.arguments?.getLong("cardId") ?: 0L
            val initialDraft = remember(cardId) { if (cardId == 0L) DraftHolder.take() else null }
            val photoResult by entry.savedStateHandle
                .getStateFlow<String?>(Route.PHOTO_RESULT, null)
                .collectAsStateWithLifecycle()

            CardEditScreen(
                cardId = cardId,
                initialDraft = initialDraft,
                photoResult = photoResult,
                onPhotoConsumed = { entry.savedStateHandle[Route.PHOTO_RESULT] = null },
                onTakePhoto = { side -> navController.navigate("${Route.PHOTO}/$side") },
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    if (cardId == 0L) {
                        navController.navigate("${Route.DETAIL}/$id") {
                            popUpTo(Route.HOME)
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(
            route = "${Route.DETAIL}/{cardId}",
            arguments = listOf(navArgument("cardId") { type = NavType.LongType }),
        ) { entry ->
            val cardId = entry.arguments?.getLong("cardId") ?: 0L
            CardDetailScreen(
                cardId = cardId,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Route.HOME)
                    }
                },
                onEdit = { id -> navController.navigate("${Route.EDIT}?cardId=$id") },
            )
        }

        composable(
            route = "${Route.PHOTO}/{side}",
            arguments = listOf(navArgument("side") { type = NavType.StringType }),
        ) { entry ->
            val side = entry.arguments?.getString("side") ?: "front"
            PhotoCaptureScreen(
                side = side,
                onCaptured = { path ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Route.PHOTO_RESULT, "$side|$path")
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
