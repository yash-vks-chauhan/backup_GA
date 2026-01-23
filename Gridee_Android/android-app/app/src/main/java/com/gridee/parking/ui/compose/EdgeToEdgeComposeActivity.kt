package com.gridee.parking.ui.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.gridee.parking.R

/**
 * Edge-to-edge Compose Activity demonstrating how to fix the gray gaps
 * around the home gesture pill by properly handling window insets.
 */
class EdgeToEdgeComposeActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge drawing
        enableEdgeToEdge()
        
        // Additional window configuration for better edge-to-edge support
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            GrideeTheme {
                EdgeToEdgeApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeToEdgeApp() {
    val systemUiController = rememberSystemUiController()
    val navigationBarColor = MaterialTheme.colorScheme.surface
    
    // Configure system UI colors for edge-to-edge
    LaunchedEffect(navigationBarColor) {
        systemUiController.setNavigationBarColor(
            color = Color.Transparent,
            darkIcons = true,
            navigationBarContrastEnforced = false // This is key to prevent gray areas
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
            EdgeToEdgeNavigationBar(
                selectedTab = selectedTab,
                navigationItems = navigationItems,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        // Main content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                WelcomeHeader()
            }
            
            items(10) { index ->
                DemoContentCard(index = index)
            }
        }
    }
}

@Composable
fun EdgeToEdgeNavigationBar(
    selectedTab: Int,
    navigationItems: List<NavigationItem>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // Key: Set window insets to handle the navigation area properly
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
                onClick = { onTabSelected(index) },
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
}

@Composable
fun WelcomeHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Welcome back!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Find and book parking spots with ease",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun DemoContentCard(index: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Parking Spot ${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "123 Main Street • Available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$${(5..15).random()}/hour",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { /* Book this spot */ }) {
                    Text("Book Now")
                }
            }
        }
    }
}

data class NavigationItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun GrideeTheme(content: @Composable () -> Unit) {
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
            onBackground = Color.Black
        ),
        content = content
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun EdgeToEdgeAppPreview() {
    GrideeTheme {
        EdgeToEdgeApp()
    }
}
