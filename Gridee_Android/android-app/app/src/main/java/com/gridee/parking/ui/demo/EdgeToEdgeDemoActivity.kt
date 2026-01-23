package com.gridee.parking.ui.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Simple demonstration of edge-to-edge implementation
 * that fixes gray gaps around home gesture pill
 */
class EdgeToEdgeDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Step 1: Enable edge-to-edge
        enableEdgeToEdge()
        
        // Step 2: Configure window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            DemoTheme {
                EdgeToEdgeDemoApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeToEdgeDemoApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val tabs = listOf(
        Tab("Home", Icons.Filled.Home, Icons.Outlined.Home),
        Tab("Search", Icons.Filled.Search, Icons.Outlined.Search),
        Tab("Favorite", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
        Tab("Profile", Icons.Filled.Person, Icons.Outlined.Person)
    )
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // This NavigationBar fixes the gray gap issue
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                // Critical: Use navigation bar window insets
                windowInsets = WindowInsets.navigationBars
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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
                            text = "Edge-to-Edge Demo",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This demonstrates how to fix gray gaps around the home gesture pill.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            
            items(20) { index ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Demo Item ${index + 1}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "This is some demo content to show scrolling behavior.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

data class Tab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun DemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1976D2),
            surface = Color.White,
            background = Color.White
        ),
        content = content
    )
}
