package com.pawplay.app.games.pawkitchen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawplay.app.games.MiniGame
import com.pawplay.app.games.pawmatch.HomeGlyphIcon
import com.pawplay.app.ui.theme.PawCoral
import com.pawplay.app.ui.theme.PawLeaf
import com.pawplay.app.ui.theme.PawSky
import com.pawplay.app.ui.theme.PawSunshine
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** How long the happy-customer reaction plays before the next customer walks up (docs/PRD.md story 25). */
private const val SERVE_REACTION_MS = 2000L

// Play-area design grid: 360 x 692dp portrait (docs/DESIGN-SYSTEM.md, "Play screen layout").
// Every size below is written in those design units and multiplied by the scale `s`, so the
// layout is the mockup on a 360dp phone and the same picture, uniformly scaled, on any other.
private const val DESIGN_W = 360f
private const val DESIGN_H = 692f
private const val MAX_SCALE = 1.5f
private const val TOUCH_FLOOR = 48f // dp, never go below for home / serve

private const val EXIT_SIZE = 56f
private const val EXIT_INSET = 20f
private const val BUBBLE_X = 88f
private const val BUBBLE_Y = 14f
private const val BUBBLE_W = 252f
private const val BUBBLE_H = 68f
private const val CUSTOMER_X = 120f
private const val CUSTOMER_Y = 96f
private const val CUSTOMER_SIZE = 120f
private const val COUNTER_Y = 198f
private const val COUNTER_H = 194f
private const val DISH_X = 20f
private const val DISH_Y = 214f
private const val DISH_X_SERVED = 80f // slides to sit under the customer's centre
private const val SERVE_X = 240f
private const val SERVE_Y = 256f
private const val SERVE_SIZE = 88f
private const val TILE = 96f
private const val TILE_GAP = 12f
private const val TRAY_TOP = 404f
private const val TRAY_BOTTOM_MARGIN = 4f

object PawKitchenGame : MiniGame {
    override val id = "paw-kitchen"
    override val icon: @Composable (Dp) -> Unit = { size -> PawKitchenTileIcon(size) }
    override val content: @Composable (onExit: () -> Unit) -> Unit = { onExit -> PawKitchenScreen(onExit) }
}

/**
 * Session-only, in-memory: entering from the home screen always starts at the
 * first, simplest customer (docs/PRD.md story 25). All rules live in
 * PawKitchenLogic.kt; this only turns taps into calls on it and draws the result.
 */
@Composable
private fun PawKitchenScreen(onExit: () -> Unit) {
    var state by remember { mutableStateOf(newGame(KitchenMenu.dishes)) }

    // Every change goes through the current value, so two fingers landing together stay consistent.
    LaunchedEffect(state.phase, state.customersServed) {
        if (state.phase == Phase.SERVING) {
            delay(SERVE_REACTION_MS)
            state = state.nextCustomer()
        }
    }

    KitchenScene(
        state = state,
        onToggle = { state = state.toggle(it) },
        onRemove = { state = state.remove(it) },
        onServe = { state = state.serve() },
        onExit = onExit,
    )
}

@Composable
internal fun KitchenScene(
    state: KitchenState,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit,
    onServe: () -> Unit,
    onExit: () -> Unit,
) {
    val spec = KitchenMenu.specFor(state.order.dish)
    val serving = state.phase == Phase.SERVING

    BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val s = min(maxWidth.value / DESIGN_W, maxHeight.value / DESIGN_H).coerceAtMost(MAX_SCALE)
        val left = (maxWidth.value - DESIGN_W * s) / 2f // the 360-wide design box is centred on wide screens
        fun x(v: Float) = (left + v * s).dp
        fun y(v: Float) = (v * s).dp
        fun len(v: Float) = (v * s).dp

        // Each new customer walks in: slides from the right and fades up.
        val arrive = remember(state.customersServed) { Animatable(0f) }
        LaunchedEffect(state.customersServed) { arrive.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }

        // Order bubble with its two tail dots.
        Box(
            modifier = Modifier
                .offset(x = x(BUBBLE_X), y = y(BUBBLE_Y))
                .size(len(BUBBLE_W), len(BUBBLE_H))
                .graphicsLayer { alpha = arrive.value }
                .drawBehind {
                    drawRoundRect(Color(0x122B2320), Offset(0f, 3f * s * density), size, CornerRadius(26f * s * density))
                }
                .clip(RoundedCornerShape(len(26f)))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            OrderPictures(spec, state, s)
        }
        Box(Modifier.offset(x(216f), y(86f)).size(len(12f)).graphicsLayer { alpha = arrive.value }.clip(CircleShape).background(Color.White))
        Box(Modifier.offset(x(207f), y(99f)).size(len(8f)).graphicsLayer { alpha = arrive.value }.clip(CircleShape).background(Color.White))

        // Customer, then the counter sits over the bottom of their head, then their paws on the edge.
        HappyBounce(active = serving) {
            Box(
                modifier = Modifier
                    .offset(x = x(CUSTOMER_X), y = y(CUSTOMER_Y))
                    .size(len(CUSTOMER_SIZE))
                    .graphicsLayer {
                        translationX = (1f - arrive.value) * 140f * s * density
                        alpha = arrive.value
                    },
            ) {
                CustomerFace(state.customer, happy = serving, modifier = Modifier.size(len(CUSTOMER_SIZE)))
            }
        }
        // Counter: a full-width band with a lighter lip and a darker edge.
        Box(
            modifier = Modifier
                .offset(y = y(COUNTER_Y))
                .fillMaxWidth()
                .height(len(COUNTER_H))
                .drawBehind {
                    drawRect(KitchenCounter)
                    drawRect(KitchenCounterLip, size = Size(size.width, 12f * s * density))
                    drawRect(KitchenCounterEdge, topLeft = Offset(0f, size.height - 8f * s * density), size = Size(size.width, 8f * s * density))
                },
        )

        // Their paws rest on the counter edge.
        Canvas(
            modifier = Modifier
                .offset(x = x(0f), y = y(180f))
                .size(len(DESIGN_W), len(40f))
                .graphicsLayer { alpha = arrive.value },
        ) {
            scale(scale = s * density, pivot = Offset.Zero) {
                val paw = customerPawColor(state.customer)
                for (cx in listOf(128f, 232f)) {
                    drawOval(paw, Offset(cx - 14f, 193f - 180f), Size(28f, 20f))
                    drawOval(RimColor.copy(alpha = 0.22f), Offset(cx - 14f, 193f - 180f), Size(28f, 20f), style = Stroke(1.6f))
                }
            }
        }

        // The dish on the counter; slides toward the customer while they are being served.
        val dishX by animateDpAsState(
            targetValue = len(if (serving) DISH_X_SERVED else DISH_X),
            animationSpec = if (serving) tween(500, easing = FastOutSlowInEasing) else snap(),
            label = "dish-slide",
        )
        DishOnCounter(
            spec = spec,
            onDish = state.onDish,
            onRemove = onRemove,
            modifier = Modifier.offset(x = x(0f) + dishX, y = y(DISH_Y)).size(len(DISH_WIDTH), len(DISH_HEIGHT)),
        )

        // Serve button: asleep until the dish is exactly right, gone during the reaction.
        if (!serving) {
            ServeButton(
                awake = state.canServe,
                s = s,
                onClick = onServe,
                modifier = Modifier.offset(x = x(SERVE_X), y = y(SERVE_Y)),
            )
        }

        // Tray: 96dp tiles, all on screen, centred in the space under the counter.
        val rows = trayRows(state.tray.size)
        val blockH = rows.size * TILE + (rows.size - 1) * TILE_GAP
        val trayTop = TRAY_TOP + ((DESIGN_H - TRAY_BOTTOM_MARGIN) - TRAY_TOP - blockH).coerceAtLeast(0f) / 2f +
            (maxHeight.value / s - DESIGN_H).coerceAtLeast(0f) / 2f
        var index = 0
        rows.forEachIndexed { rowIndex, count ->
            val rowW = count * TILE + (count - 1) * TILE_GAP
            val rowLeft = (DESIGN_W - rowW) / 2f
            repeat(count) { col ->
                val ingredient = state.tray[index++]
                TrayTile(
                    icon = spec.icons.getValue(ingredient),
                    ingredient = ingredient,
                    onDish = ingredient in state.onDish,
                    dimmed = serving,
                    s = s,
                    onClick = { onToggle(ingredient) },
                    modifier = Modifier.offset(
                        x = x(rowLeft + col * (TILE + TILE_GAP)),
                        y = y(trayTop + rowIndex * (TILE + TILE_GAP)),
                    ),
                )
            }
        }

        if (serving) {
            CelebrationEffects(
                modifier = Modifier.offset(x = x(0f), y = y(0f)).size(len(DESIGN_W), len(DESIGN_H)),
                s = s,
            )
        }

        // Home: always on screen, always live, same corner as the other games.
        ExitButton(
            size = max(EXIT_SIZE * s, TOUCH_FLOOR).dp,
            onClick = onExit,
            modifier = Modifier.offset(x = x(EXIT_INSET), y = y(EXIT_INSET)),
        )
    }
}

// ------------------------------------------------------------------ order bubble

@Composable
private fun OrderPictures(spec: DishSpec, state: KitchenState, s: Float) {
    val items = state.order.ingredients
    val box = if (items.size <= 4) 56f else 44f
    Row(horizontalArrangement = Arrangement.spacedBy((5f * s).dp)) {
        items.forEach { ingredient ->
            BubblePicture(
                icon = spec.icons.getValue(ingredient),
                onDish = ingredient in state.onDish,
                box = box,
                s = s,
            )
        }
    }
}

/** A picture in the order bubble: gets a soft leaf-green glow while that ingredient is on the dish. */
@Composable
private fun BubblePicture(icon: IngredientIcon, onDish: Boolean, box: Float, s: Float) {
    val got by animateFloatAsState(if (onDish) 1f else 0f, tween(200), label = "bubble-got")
    val corner = 14f * s
    Box(
        modifier = Modifier
            .size((box * s).dp)
            .graphicsLayer { scaleX = 1f + 0.05f * got; scaleY = 1f + 0.05f * got }
            .softGlow(PawLeaf, spread = (10f * s).dp, corner = corner.dp, strength = got)
            .roundBorder(color = lerp(Color.Transparent, PawLeaf, got), width = (3f * s).dp, corner = corner.dp),
        contentAlignment = Alignment.Center,
    ) {
        IngredientPicture(icon, Modifier.size(((box - 6f) * s).dp))
    }
}

// ------------------------------------------------------------------ dish

@Composable
private fun DishOnCounter(spec: DishSpec, onDish: Set<String>, onRemove: (String) -> Unit, modifier: Modifier) {
    val pieces = remember(spec, onDish) { spec.view.pieces(onDish) }
    val remove by rememberUpdatedState(onRemove)

    // A tiny pop each time something goes on or comes off, so the dish answers the tap.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(onDish) {
        pop.snapTo(0.95f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
    }

    Canvas(
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value; scaleY = pop.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .pointerInput(pieces) {
                detectTapGestures { tap ->
                    val k = size.width / DISH_WIDTH
                    hitTestDish(pieces, Offset(tap.x / k, tap.y / k))?.let { remove(it) }
                }
            },
    ) {
        scale(scale = size.width / DISH_WIDTH, pivot = Offset.Zero) {
            pieces.forEach { it.draw(this) }
        }
    }
}

// ------------------------------------------------------------------ tray

@Composable
private fun TrayTile(
    icon: IngredientIcon,
    ingredient: String,
    onDish: Boolean,
    dimmed: Boolean,
    s: Float,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner = 19f * s
    val border = (3f * s).dp
    val shape = RoundedCornerShape(corner.dp)
    val down = pressed && !dimmed

    Box(
        modifier = modifier
            .size((TILE * s).dp)
            .alpha(if (dimmed) 0.5f else 1f)
            .graphicsLayer { scaleX = if (down) 0.96f else 1f; scaleY = if (down) 0.96f else 1f }
            .softGlow(PawSky, spread = (10f * s).dp, corner = corner.dp, strength = if (down) 1f else 0f)
            .then(
                if (onDish) {
                    // Used slot: transparent, dashed outline, still a live 96dp target to take it off again.
                    Modifier.drawBehind {
                        val w = border.toPx()
                        drawRoundRect(
                            KitchenTraySlotDash, Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
                            CornerRadius(corner.dp.toPx() - w / 2),
                            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 2.4f, w * 1.6f))),
                        )
                    }
                } else {
                    Modifier
                        .clip(shape)
                        .background(Color.White)
                        .roundBorder(if (down) PawSky else PawSunshine, border, corner.dp)
                },
            )
            .clickable(interactionSource = source, indication = null, enabled = !dimmed, onClick = onClick)
            .semantics { contentDescription = ingredient },
        contentAlignment = Alignment.Center,
    ) {
        IngredientPicture(
            icon,
            Modifier.size((68f * s).dp).alpha(if (onDish) 0.2f else 1f),
        )
    }
}

// ------------------------------------------------------------------ serve button

@Composable
private fun ServeButton(awake: Boolean, s: Float, onClick: () -> Unit, modifier: Modifier) {
    val size = max(SERVE_SIZE * s, TOUCH_FLOOR)
    Box(modifier = modifier.size(size.dp), contentAlignment = Alignment.Center) {
        if (awake) {
            HopAndSparkle(s = s) { AwakeServe(size, s, onClick) }
        } else {
            // Asleep: dimmed, perfectly still, no press effect, and a tap does nothing.
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(KitchenServeAsleep)
                    .semantics { contentDescription = "Serve, not ready yet" },
                contentAlignment = Alignment.Center,
            ) {
                ServeGlyph(awake = false, modifier = Modifier.size((52f * s).dp).alpha(0.75f))
            }
        }
    }
}

@Composable
private fun AwakeServe(size: Float, s: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .drawBehind { drawCircle(PawLeaf, radius = this.size.minDimension / 2f + 6f * s * density, alpha = 0.22f) }
            .clip(CircleShape)
            .background(PawLeaf)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .semantics { contentDescription = "Serve" },
        contentAlignment = Alignment.Center,
    ) {
        ServeGlyph(awake = true, modifier = Modifier.size((52f * s).dp))
    }
}

/** The awake serve button's gentle hop, with three sunshine sparkles twinkling around it. */
@Composable
private fun HopAndSparkle(s: Float, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "serve-hop")
    val hop by transition.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1100
                0f at 0 using FastOutSlowInEasing
                1f at 495 using FastOutSlowInEasing
                0f at 1100
            },
        ),
        label = "hop",
    )
    val twinkle by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "twinkle")
    Box(
        modifier = Modifier.graphicsLayer {
            translationY = -7f * s * density * hop
            scaleX = 1f + 0.06f * hop; scaleY = 1f + 0.06f * hop
        },
        contentAlignment = Alignment.Center,
    ) {
        content()
        Canvas(Modifier.requiredSize((116f * s).dp)) {
            scale(scale = s * density, pivot = Offset.Zero) {
                for ((sx, sy, r, delay) in listOf(
                    listOf(10f, 26f, 7f, 0f), listOf(106f, 14f, 6f, 0.5f), listOf(104f, 98f, 5f, 0.9f),
                )) {
                    val t = tri((twinkle + delay / 1.3f) % 1f)
                    sparkle(sx, sy, r * (0.55f + 0.55f * t), PawSunshine, alpha = 0.6f + 0.4f * t)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ happy customer

@Composable
private fun HappyBounce(active: Boolean, content: @Composable () -> Unit) {
    if (!active) {
        content()
        return
    }
    val transition = rememberInfiniteTransition(label = "customer-bounce")
    val up by transition.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 1000; 0f at 0; 10f at 300; 4f at 650; 0f at 1000 }),
        label = "up",
    )
    val tilt by transition.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 1000; 0f at 0; -4f at 300; 4f at 650; 0f at 1000 }),
        label = "tilt",
    )
    Box(modifier = Modifier.graphicsLayer { translationY = -up * density; rotationZ = tilt }) { content() }
}

/** About two seconds of sunshine sparkles and coral hearts drifting around the happy customer. No coins, stars or words. */
@Composable
private fun CelebrationEffects(modifier: Modifier, s: Float) {
    val transition = rememberInfiniteTransition(label = "celebration")
    val sparkleT by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sparkles")
    val heartT by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "hearts")
    Canvas(modifier) {
        scale(scale = s * density, pivot = Offset.Zero) {
            for ((x, y, r, delay) in listOf(
                listOf(96f, 120f, 13f, 0f), listOf(268f, 112f, 15f, 0.35f), listOf(70f, 176f, 8f, 0.7f),
                listOf(292f, 170f, 9f, 1f), listOf(130f, 92f, 7f, 0.5f), listOf(236f, 96f, 8f, 0.15f),
                listOf(110f, 224f, 9f, 0.9f), listOf(256f, 220f, 8f, 0.55f),
            )) {
                val t = tri((sparkleT + delay / 1.3f) % 1f)
                sparkle(x, y, r * (0.55f + 0.55f * t), PawSunshine, alpha = 0.6f + 0.4f * t)
            }
            for ((x, y, r, delay) in listOf(
                listOf(84f, 146f, 26f, 0.2f), listOf(280f, 144f, 24f, 0.8f), listOf(168f, 100f, 20f, 1.1f), listOf(210f, 104f, 16f, 0.5f),
            )) {
                val p = (heartT + delay / 1.8f) % 1f
                heart(x, y + 6f - 14f * p, r, PawCoral, alpha = 0.3f + 0.7f * tri(p))
            }
        }
    }
}

/** 0 -> 1 -> 0 over a 0..1 phase. */
private fun tri(p: Float) = 1f - abs(2f * p - 1f)

// ------------------------------------------------------------------ small pieces

@Composable
private fun ExitButton(size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Back to home" },
        contentAlignment = Alignment.Center,
    ) {
        HomeGlyphIcon(modifier = Modifier.size(size / 2))
    }
}

/** A solid rounded border in [color], drawn inside the box's edge. */
private fun Modifier.roundBorder(color: Color, width: Dp, corner: Dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(corner.toPx() - w / 2), style = Stroke(w))
}

/** A soft coloured halo around a rounded box (a layered fade, since a coloured shadow needs API 28). */
private fun Modifier.softGlow(color: Color, spread: Dp, corner: Dp, strength: Float): Modifier =
    if (strength <= 0.01f) this else drawBehind {
        val steps = 5
        val reach = spread.toPx()
        for (i in steps downTo 1) {
            val grow = reach * i / steps
            drawRoundRect(
                color,
                Offset(-grow, -grow),
                Size(size.width + 2 * grow, size.height + 2 * grow),
                CornerRadius(corner.toPx() + grow),
                alpha = strength * 0.13f,
            )
        }
    }
