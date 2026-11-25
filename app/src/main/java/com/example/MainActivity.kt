package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import java.net.URLEncoder
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.DefaultCatalogDefaults
import com.example.data.LocalOrderStore
import com.example.data.PizzaClubRepository
import com.example.notifications.OrderAlertNotifier
import com.example.ui.admin.AdminAuthGate
import com.example.ui.customer.CheckoutScreen
import com.example.ui.customer.CustomerCatalogScreen
import com.example.ui.customer.OrderTrackerScreen
import com.example.ui.customer.SocialAndTreasureBottomSheet
import com.example.ui.theme.PizzaClubTheme
import com.example.viewmodel.CatalogUiState
import com.example.viewmodel.CustomerViewModel

const val ROUTE_CATALOG = "catalog"
const val ROUTE_CHECKOUT = "checkout"
const val ROUTE_ORDERS = "orders"
const val ROUTE_ADMIN = "admin"

private data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : FragmentActivity() {
    private var pendingNotificationOrderId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingNotificationOrderId = extractNotificationOrderTarget(intent)
        setContent {
            PizzaClubTheme {
                PizzaClubApp(
                    notificationOrderId = pendingNotificationOrderId,
                    onConsumedNotificationOrder = { pendingNotificationOrderId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationOrderId = extractNotificationOrderTarget(intent)
    }

    private fun extractNotificationOrderTarget(intent: Intent?): String? {
        if (intent == null) return null
        val openOrders = intent.getBooleanExtra(OrderAlertNotifier.EXTRA_OPEN_ORDERS_TAB, false)
        val orderId = intent.getStringExtra(OrderAlertNotifier.EXTRA_ORDER_ID)
        return when {
            !orderId.isNullOrBlank() -> orderId
            openOrders -> ""
            else -> null
        }
    }
}

@Composable
fun PizzaClubApp(
    notificationOrderId: String? = null,
    onConsumedNotificationOrder: () -> Unit = {}
) {
    val customerViewModel: CustomerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                CustomerViewModel(
                    repository = PizzaClubRepository(app),
                    localOrderStore = LocalOrderStore(app),
                    orderStatusNotifier = OrderAlertNotifier(app)
                )
            }
        }
    )

    val catalogState by customerViewModel.catalogState.collectAsStateWithLifecycle()
    val checkoutForm by customerViewModel.checkoutForm.collectAsStateWithLifecycle()
    val isSubmitting by customerViewModel.isSubmittingOrder.collectAsStateWithLifecycle()
    val submitError by customerViewModel.submitError.collectAsStateWithLifecycle()
    val savedOrders by customerViewModel.savedOrders.collectAsStateWithLifecycle()
    val selectedOrderId by customerViewModel.selectedOrderId.collectAsStateWithLifecycle()
    val liveTrackedStatus by customerViewModel.liveTrackedOrderStatus.collectAsStateWithLifecycle()
    val isRefreshingHistory by customerViewModel.isRefreshingHistory.collectAsStateWithLifecycle()
    val pizzaClubPoints by customerViewModel.pizzaClubPoints.collectAsStateWithLifecycle()
    val loyaltyState by customerViewModel.loyaltyState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_CATALOG

    var showSocialTreasureSheet by remember { mutableStateOf(false) }
    var sheetInitialTab by remember { mutableIntStateOf(0) }
    val activeSettings = (catalogState as? CatalogUiState.Ready)?.settings
        ?: DefaultCatalogDefaults.defaultSettings

    val destinations = listOf(
        TopDestination(ROUTE_CATALOG, "Menú", Icons.Rounded.LocalPizza, Icons.Outlined.LocalPizza),
        TopDestination(ROUTE_ORDERS, "Mis Pedidos", Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
        TopDestination(ROUTE_ADMIN, "Admin", Icons.Rounded.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
    )

    val showChrome = currentRoute != ROUTE_CHECKOUT

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(notificationOrderId) {
        if (notificationOrderId != null) {
            if (notificationOrderId.isNotBlank()) {
                customerViewModel.selectOrder(notificationOrderId)
            }
            navigateTopLevel(ROUTE_ORDERS)
            onConsumedNotificationOrder()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 720.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showChrome && !useRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                modifier = Modifier.testTag("nav_${dest.route}"),
                                icon = {
                                    if (dest.route == ROUTE_ORDERS && savedOrders.isNotEmpty()) {
                                        BadgedBox(badge = { Badge { Text("${savedOrders.size}") } }) {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChrome && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                modifier = Modifier.testTag("rail_${dest.route}"),
                                icon = {
                                    if (dest.route == ROUTE_ORDERS && savedOrders.isNotEmpty()) {
                                        BadgedBox(badge = { Badge { Text("${savedOrders.size}") } }) {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_CATALOG,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_CATALOG) {
                        CustomerCatalogScreen(
                            uiState = catalogState,
                            selectedPreparationMode = checkoutForm.preparationMode,
                            onSelectPreparationMode = { mode ->
                                customerViewModel.updateCheckoutForm { it.copy(preparationMode = mode) }
                            },
                            onIncrement = customerViewModel::incrementQuantity,
                            onDecrement = customerViewModel::decrementQuantity,
                            onProceedToCheckout = {
                                navController.navigate(ROUTE_CHECKOUT)
                            },
                            onOpenSocialQr = {
                                sheetInitialTab = 1
                                showSocialTreasureSheet = true
                            }
                        )
                    }

                    composable(ROUTE_CHECKOUT) {
                        val readyState = catalogState as? CatalogUiState.Ready
                        if (readyState != null) {
                            CheckoutScreen(
                                catalogState = readyState,
                                form = checkoutForm,
                                isSubmitting = isSubmitting,
                                submitError = submitError,
                                onUpdateForm = customerViewModel::updateCheckoutForm,
                                onSubmitOrder = {
                                    customerViewModel.submitPreOrder {
                                        navController.navigate(ROUTE_ORDERS) {
                                            popUpTo(ROUTE_CATALOG)
                                        }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    composable(ROUTE_ORDERS) {
                        OrderTrackerScreen(
                            savedOrders = savedOrders,
                            selectedOrderId = selectedOrderId,
                            liveTracker = liveTrackedStatus,
                            settings = activeSettings,
                            isRefreshingHistory = isRefreshingHistory,
                            pizzaClubPoints = pizzaClubPoints,
                            loyaltyState = loyaltyState,
                            onRedeemLoyaltyReward = {
                                customerViewModel.redeemLoyaltyReward { redeemed ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Cupón Pizza Club", redeemed.code))
                                    Toast.makeText(
                                        context,
                                        "¡Premio canjeado! Cupón ${redeemed.code} copiado.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    val msg = "🎁 *PIZZA CLUB POINTS — CANJE DE PREMIO VIP*\n" +
                                        "Quiero canjear mi premio de ${redeemed.pointsCost} puntos.\n" +
                                        "🎟️ Cupón único: *${redeemed.code}*"
                                    val url = "https://wa.me/${activeSettings.whatsappNumber}?text=${URLEncoder.encode(msg, "UTF-8")}"
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {
                                        // Clipboard already holds the coupon code
                                    }
                                }
                            },
                            onSelectOrder = customerViewModel::selectOrder,
                            onRefreshHistory = { customerViewModel.refreshAllSavedOrdersStatus() },
                            onGoToMenu = { navigateTopLevel(ROUTE_CATALOG) },
                            onOpenTreasureHunt = {
                                sheetInitialTab = 0
                                showSocialTreasureSheet = true
                            },
                            onOpenSocialQr = {
                                sheetInitialTab = 1
                                showSocialTreasureSheet = true
                            }
                        )
                    }

                    composable(ROUTE_ADMIN) {
                        AdminAuthGate()
                    }
                }
            }
        }

        if (showSocialTreasureSheet) {
            SocialAndTreasureBottomSheet(
                settings = activeSettings,
                initialTab = sheetInitialTab,
                onDismiss = { showSocialTreasureSheet = false }
            )
        }
    }
}
