package com.zigor.discountcard.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zigor.discountcard.data.repo.CardDraft
import com.zigor.discountcard.ui.bank.BankCardDetailScreen
import com.zigor.discountcard.ui.bank.BankCardEditScreen
import com.zigor.discountcard.ui.bank.BankCardsScreen
import com.zigor.discountcard.ui.card.CardDetailScreen
import com.zigor.discountcard.ui.card.CardEditScreen
import com.zigor.discountcard.ui.home.HomeScreen
import com.zigor.discountcard.ui.imagescan.ImageScanScreen
import com.zigor.discountcard.ui.nfc.NfcScanScreen
import com.zigor.discountcard.ui.photo.PhotoCaptureScreen
import com.zigor.discountcard.ui.help.HelpScreen
import com.zigor.discountcard.ui.scan.ScanScreen
import com.zigor.discountcard.ui.transfer.ImportScreen

object Route {
    const val HOME = "home"
    const val SCAN = "scan"
    const val IMAGE_SCAN = "image_scan"
    const val NFC = "nfc"
    const val EDIT = "edit"
    const val DETAIL = "detail"
    const val PHOTO = "photo"
    const val PHOTO_RESULT = "photo_result"
    const val IMPORT = "import"
    const val HELP = "help"
    const val BANK_LIST = "bank_cards"
    const val BANK_EDIT = "bank_edit"
    const val BANK_DETAIL = "bank_detail"
}

/** Файл с картами, присланный из мессенджера: ждёт, пока его откроет экран импорта. */
object IncomingFile {
    var pending by mutableStateOf<Uri?>(null)

    fun take(): Uri? {
        val value = pending
        pending = null
        return value
    }
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
    // Пришёл файл с картами — сразу открываем экран импорта
    val incoming = IncomingFile.pending
    LaunchedEffect(incoming) {
        if (incoming != null) navController.navigate(Route.IMPORT)
    }

    NavHost(navController = navController, startDestination = Route.HOME) {

        composable(Route.HOME) {
            HomeScreen(
                onOpenCard = { id -> navController.navigate("${Route.DETAIL}/$id") },
                onScanBarcode = { navController.navigate(Route.SCAN) },
                onScanNfc = { navController.navigate(Route.NFC) },
                onScanImage = { navController.navigate(Route.IMAGE_SCAN) },
                onImportCards = { navController.navigate(Route.IMPORT) },
                onOpenHelp = { navController.navigate(Route.HELP) },
                onOpenBankCards = { navController.navigate(Route.BANK_LIST) },
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
                onScanImage = {
                    navController.navigate(Route.IMAGE_SCAN) {
                        popUpTo(Route.SCAN) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.IMAGE_SCAN) {
            ImageScanScreen(
                onBack = {
                    if (!navController.popBackStack()) navController.navigate(Route.HOME)
                },
                onDraftReady = { draft ->
                    DraftHolder.pending = draft
                    navController.navigate("${Route.EDIT}?cardId=0") {
                        popUpTo(Route.IMAGE_SCAN) { inclusive = true }
                    }
                },
                onDuplicate = { id ->
                    navController.navigate("${Route.DETAIL}/$id") {
                        popUpTo(Route.IMAGE_SCAN) { inclusive = true }
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

        composable(Route.HELP) {
            HelpScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.IMPORT) {
            ImportScreen(
                onBack = {
                    IncomingFile.pending = null
                    if (!navController.popBackStack()) navController.navigate(Route.HOME)
                },
                onFinished = {
                    IncomingFile.pending = null
                    navController.navigate(Route.HOME) {
                        popUpTo(Route.HOME) { inclusive = true }
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

        composable(Route.BANK_LIST) {
            BankCardsScreen(
                onBack = {
                    if (!navController.popBackStack()) navController.navigate(Route.HOME)
                },
                onAdd = { navController.navigate("${Route.BANK_EDIT}?cardId=0") },
                onOpen = { id -> navController.navigate("${Route.BANK_DETAIL}/$id") },
            )
        }

        composable(
            route = "${Route.BANK_EDIT}?cardId={cardId}",
            arguments = listOf(
                navArgument("cardId") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            val cardId = entry.arguments?.getLong("cardId") ?: 0L
            BankCardEditScreen(
                cardId = cardId,
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    if (cardId == 0L) {
                        navController.navigate("${Route.BANK_DETAIL}/$id") {
                            popUpTo(Route.BANK_LIST)
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(
            route = "${Route.BANK_DETAIL}/{cardId}",
            arguments = listOf(navArgument("cardId") { type = NavType.LongType }),
        ) { entry ->
            val cardId = entry.arguments?.getLong("cardId") ?: 0L
            BankCardDetailScreen(
                cardId = cardId,
                onBack = {
                    if (!navController.popBackStack()) navController.navigate(Route.BANK_LIST)
                },
                onEdit = { id -> navController.navigate("${Route.BANK_EDIT}?cardId=$id") },
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
