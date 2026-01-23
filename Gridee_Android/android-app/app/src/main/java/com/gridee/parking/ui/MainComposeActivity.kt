package com.gridee.parking.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.utils.InAppUpdateController
import com.google.accompanist.systemuicontroller.rememberSystemUiController

/**
 * New Compose-based MainActivity that fixes the gray gaps around home gesture pill
 * by properly implementing edge-to-edge drawing and window insets handling.
 */
class MainComposeActivity : ComponentActivity() {

    private var inAppUpdateController: InAppUpdateController? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Critical: Enable edge-to-edge drawing
        enableEdgeToEdge()
        
        // Additional configuration for proper edge-to-edge support
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            GrideeAppTheme {
                MainAppContent()
            }
        }

        val snackbarAnchorView = findViewById<android.view.View>(android.R.id.content)
        inAppUpdateController = InAppUpdateController(
            activity = this,
            snackbarAnchorView = snackbarAnchorView,
        ).also { it.checkForUpdates() }
    }

    override fun onResume() {
        super.onResume()
        inAppUpdateController?.onResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        inAppUpdateController?.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        inAppUpdateController?.onDestroy()
        inAppUpdateController = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent() {
    val context = LocalContext.current
    val systemUiController = rememberSystemUiController()
    
    // Configure system UI to prevent gray areas
    LaunchedEffect(Unit) {
        systemUiController.setNavigationBarColor(
            color = Color.Transparent, // Transparent navigation bar
            darkIcons = true,
            navigationBarContrastEnforced = false // KEY: Disable contrast enforcement
        )
        systemUiController.setStatusBarColor(
            color = Color.Transparent,
            darkIcons = true
        )
    }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val navigationItems = listOf(
        NavigationItem("Home", Icons.Filled.Home, Icons.Outlined.Home),
        NavigationItem("Bookings", Icons.Filled.DateRange, Icons.Outlined.DateRange),
        NavigationItem("Wallet", Icons.Filled.Star, Icons.Outlined.Star),
        NavigationItem("Profile", Icons.Filled.Person, Icons.Outlined.Person)
    )
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // This NavigationBar will extend into the system navigation area
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                // CRITICAL: Use navigation bar window insets to handle system navigation area
                windowInsets = WindowInsets.navigationBars
            ) {
                navigationItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        selected = selectedTab == index,
                        onClick = { 
                            selectedTab = index
                            handleTabNavigation(context, index)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Navigate to parking booking
                    try {
                        val intent = Intent(context, Class.forName("com.gridee.parking.ui.booking.ParkingLotSelectionActivity"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback navigation
                        try {
                            val fallbackIntent = Intent(context, Class.forName("com.gridee.parking.ui.discovery.ParkingDiscoveryActivity"))
                            context.startActivity(fallbackIntent)
                        } catch (fallbackException: Exception) {
                            // Handle error
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Book Parking"
                )
            }
        }
    ) { paddingValues ->
        // Main content with proper padding
        MainContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            selectedTab = selectedTab
        )
    }
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    selectedTab: Int
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        
        item {
            RecentBookingsSection()
        }
        
        // Demo content to show scrolling behavior
        items(8) { index ->
            ParkingSpotCard(index = index)
        }
    }
}



@Composable
fun RecentBookingsSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Bookings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { /* View all bookings */ }) {
                    Text("View All")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No recent bookings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ParkingSpotCard(index: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Parking Spot ${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${100 + index * 23} Main Street",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Available",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Available",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "$${(8..18).random()}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "per hour",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = { /* Book this spot */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Book Now")
            }
        }
    }
}

private fun handleTabNavigation(context: android.content.Context, tabIndex: Int) {
    when (tabIndex) {
        0 -> { /* Already on Home */ }
        1 -> {
            val intent = Intent(context, MainContainerActivity::class.java)
            intent.putExtra(MainContainerActivity.EXTRA_TARGET_TAB, tabIndex)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        2 -> {
            val intent = Intent(context, MainContainerActivity::class.java)
            intent.putExtra(MainContainerActivity.EXTRA_TARGET_TAB, tabIndex)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        3 -> {
            val intent = Intent(context, MainContainerActivity::class.java)
            intent.putExtra(MainContainerActivity.EXTRA_TARGET_TAB, tabIndex)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

data class NavigationItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun GrideeAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1976D2),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE3F2FD),
            onPrimaryContainer = Color(0xFF0D47A1),
            secondary = Color(0xFF526350),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD4E8D1),
            onSecondaryContainer = Color(0xFF0F1F0F),
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFF5F5F5),
            onSurfaceVariant = Color(0xFF666666),
            background = Color.White,
            onBackground = Color.Black,
            error = Color(0xFFF44336),
            onError = Color.White
        ),
        content = content
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainAppContentPreview() {
    GrideeAppTheme {
        MainAppContent()
    }
}
