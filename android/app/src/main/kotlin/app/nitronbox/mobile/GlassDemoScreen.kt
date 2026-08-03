package app.nitronbox.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

private data class FeedItem(val title: String, val caption: String, val colors: List<Color>)

@Composable
fun NitronGlassDemo() {
    val hazeState = remember { HazeState() }
    val items = remember {
        listOf(
            FeedItem("Reasoning workspace", "A calm place for complex prompts", listOf(Color(0xFF157EAB), Color(0xFF2D255F))),
            FeedItem("Creative studio", "Draft, rewrite and explore ideas", listOf(Color(0xFFB54772), Color(0xFF422657))),
            FeedItem("Code companion", "Review architecture and implementation", listOf(Color(0xFF147E6D), Color(0xFF16394F))),
            FeedItem("Research mode", "Compare sources and build summaries", listOf(Color(0xFFB66A2C), Color(0xFF493149))),
        )
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = Color(0xFF071018),
            surface = Color(0xFF101923),
            onBackground = Color(0xFFF2F7FA),
            onSurface = Color(0xFFF2F7FA),
            primary = Color(0xFF8ADFFF),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF071018)),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .haze(
                        state = hazeState,
                        style = HazeDefaults.style(
                            backgroundColor = Color(0xFF071018),
                            blurRadius = 28.dp,
                            noiseFactor = 0.08f,
                        ),
                    ),
                contentPadding = PaddingValues(bottom = 224.dp),
            ) {
                item {
                    HeroImage()
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp)) {
                        Text(
                            text = "Your intelligence,\nbeautifully focused.",
                            fontSize = 36.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1.6).sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Scroll the content. The card below samples this moving layer in real time.",
                            color = Color(0xFFA8B5BF),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
                items(items) { item ->
                    FeedCard(item)
                }
            }

            LiquidGlassCard(
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 18.dp),
            ) {
                GlassComposer()
            }
        }
    }
}

@Composable
private fun HeroImage() {
    Box(modifier = Modifier.fillMaxWidth().height(390.dp)) {
        AsyncImage(
            model = "https://images.unsplash.com/photo-1519608487953-e999c86e7455?auto=format&fit=crop&w=1400&q=90",
            contentDescription = "Night sky over mountains",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x22071018), Color(0xFF071018)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.14f),
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(11.dp), tint = Color.White)
            }
            Text(
                text = "NitronBox",
                modifier = Modifier.padding(start = 10.dp).weight(1f),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Icon(Icons.Rounded.MoreHoriz, null, tint = Color.White)
        }
    }
}

@Composable
private fun FeedCard(item: FeedItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(item.colors))
            .padding(20.dp),
    ) {
        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(item.caption, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
    }
}

@Composable
private fun GlassComposer() {
    Column(modifier = Modifier.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ask NitronBox", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "How can I help you today?",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = Color(0xFFE9F8FF),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = "Send",
                    modifier = Modifier.padding(12.dp),
                    tint = Color(0xFF071018),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Claude")
            GlassChip("GPT")
            GlassChip("Gemini")
        }
    }
}

@Composable
private fun GlassChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.08f),
        contentColor = Color.White.copy(alpha = 0.74f),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp), fontSize = 10.sp)
    }
}
