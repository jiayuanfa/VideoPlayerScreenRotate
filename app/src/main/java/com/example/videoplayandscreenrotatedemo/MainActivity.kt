package com.example.videoplayandscreenrotatedemo

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.videoplayandscreenrotatedemo.ui.theme.VideoPlayAndScreenRotateDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoPlayAndScreenRotateDemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VideoPlayerScreen()
                }
            }
        }
    }
}

@Composable
fun VideoPlayerScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var isFullscreen by remember { mutableStateOf(false) }
    
    /**
     * 【关键点1：使用 remember 保持 ExoPlayer 实例】
     * 
     * 为什么之前的方式不行：
     * - 之前使用 AnimatedContent 时，每次切换都会创建新的 VideoPlayer 组件
     * - 每个 VideoPlayer 组件内部都会创建新的 ExoPlayer 实例
     * - 当 isFullscreen 状态改变时，AnimatedContent 会销毁旧的组件并创建新的组件
     * - 这导致 ExoPlayer 被释放和重新创建，视频播放中断，出现黑屏
     * 
     * 为什么现在的方式可以：
     * - 使用 remember { } 将 ExoPlayer 实例提升到 Composable 函数的作用域
     * - remember 确保在整个 Composable 生命周期内，ExoPlayer 实例只创建一次
     * - 即使 isFullscreen 状态改变导致重组，ExoPlayer 实例也不会重新创建
     * - 视频可以持续播放，不会中断
     */
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            val mediaItem = MediaItem.fromUri(videoUrl)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    
    /**
     * 【关键点2：正确释放资源】
     * DisposableEffect 确保在 Composable 被销毁时释放 ExoPlayer 资源
     * 避免内存泄漏
     */
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    // 监听屏幕方向变化
    val screenOrientation = configuration.orientation
    LaunchedEffect(screenOrientation) {
        isFullscreen = screenOrientation == Configuration.ORIENTATION_LANDSCAPE
    }
    
    // 处理返回键
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        (context as? ComponentActivity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    
    // 全屏切换函数
    val toggleFullscreen: () -> Unit = {
        isFullscreen = !isFullscreen
        val activity = context as? ComponentActivity
        activity?.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    
    /**
     * 【关键点3：使用动画状态平滑过渡】
     * animateDpAsState 会在状态改变时平滑地动画过渡
     * 这样布局变化是渐进的，而不是突然的，用户体验更好
     */
    val topPadding by animateDpAsState(
        targetValue = if (isFullscreen) 0.dp else 16.dp,
        animationSpec = tween(400),
        label = "top_padding_animation"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        /**
         * 【关键点4：使用 animateContentSize 实现平滑的布局变化】
         * 
         * 为什么之前的方式不行：
         * - AnimatedContent 在切换时会完全销毁旧内容并创建新内容
         * - 即使 ExoPlayer 实例相同，PlayerView（AndroidView）也会被销毁和重建
         * - AndroidView 的 factory 函数会在每次重组时被调用，创建新的 View 实例
         * - 这导致视频播放器视图被销毁，出现黑屏
         * 
         * 为什么现在的方式可以：
         * - 不使用 AnimatedContent，而是使用单个 Column 布局
         * - 通过 animateContentSize 让布局尺寸平滑变化
         * - 布局结构保持不变，只是尺寸和约束在改变
         * - PlayerView 不会被销毁，只是布局参数在改变
         */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(
                    animationSpec = tween(400)
                )
        ) {
            Spacer(modifier = Modifier.height(topPadding))
            
            /**
             * 【关键点5：使用 key() 确保 AndroidView 不会重新创建】
             * 
             * 为什么之前的方式不行：
             * - 在 AnimatedContent 中，每次切换都会创建新的 AndroidView
             * - AndroidView 的 factory 函数每次都会执行，创建新的 PlayerView
             * - 即使 ExoPlayer 实例相同，新的 PlayerView 也需要重新绑定
             * - 这个重新绑定的过程会导致视频画面短暂消失（黑屏）
             * 
             * 为什么现在的方式可以：
             * - 使用 key(exoPlayer) 告诉 Compose：只要 exoPlayer 实例不变，就不要重新创建这个 AndroidView
             * - 当 isFullscreen 改变时，Compose 会检查 key 是否改变
             * - 由于 exoPlayer 实例不变，Compose 会复用现有的 AndroidView
             * - 只会更新 modifier，改变布局约束，而不会重新创建 View
             * - 这样 PlayerView 和 ExoPlayer 的连接保持不变，视频持续播放
             * 
             * 【关键点6：通过 modifier 改变布局而不是重新创建组件】
             * - 使用条件表达式 .then() 根据 isFullscreen 状态改变 modifier
             * - 当 isFullscreen 改变时，只有 modifier 在改变
             * - Compose 会重新测量和布局，但不会重新创建 View
             * - animateContentSize 让这个布局变化过程是平滑的动画
             * - 用户可以看到视频播放器从竖屏布局平滑过渡到横屏布局
             */
            key(exoPlayer) {
                AndroidView(
                    factory = { ctx ->
                        // factory 函数只会在第一次创建时执行
                        // 由于使用了 key(exoPlayer)，只要 exoPlayer 不变，这个函数就不会再次执行
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            // 根据 isFullscreen 状态改变布局约束
                            // 这个改变会触发重新布局，但不会重新创建 View
                            if (isFullscreen) {
                                Modifier.fillMaxSize()  // 横屏：填满整个屏幕
                            } else {
                                Modifier.aspectRatio(16f / 9f)  // 竖屏：保持 16:9 比例
                            }
                        )
                        .animateContentSize(
                            // 让布局尺寸的变化是平滑的动画
                            // 这样用户可以看到播放器平滑地从竖屏过渡到横屏
                            animationSpec = tween(400)
                        )
                )
            }
            
            if (!isFullscreen) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        
        // 全屏按钮
        IconButton(
            onClick = toggleFullscreen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                tint = Color.White
            )
        }
    }
}