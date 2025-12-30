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
    
    /**
     * 【可选：手动处理配置变更】
     * 
     * 如果需要在 Activity 层面处理配置变更，可以重写这个方法。
     * 但在我们的项目中，由于使用了 Compose 和 LocalConfiguration，
     * Compose 会自动处理配置变更，所以这个方法不是必需的。
     * 
     * 如果需要在配置变更时做一些额外的处理（例如：更新某些系统设置），
     * 可以在这里添加代码。
     */
    // override fun onConfigurationChanged(newConfig: Configuration) {
    //     super.onConfigurationChanged(newConfig)
    //     // 在这里可以手动处理配置变更
    //     // 例如：更新某些系统设置、重新初始化某些组件等
    // }
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
    
    /**
     * 【监听屏幕方向变化 - Compose 的响应式处理】
     * 
     * 工作流程：
     * 1. LocalConfiguration.current 会获取当前的配置信息
     * 2. 当屏幕方向改变时（由于配置了 configChanges，Activity 不会重建）
     * 3. Compose 会检测到 configuration 的变化
     * 4. 触发重组（recompose），重新执行这个 Composable 函数
     * 5. configuration.orientation 会返回新的方向值
     * 6. LaunchedEffect 检测到 screenOrientation 变化
     * 7. 执行代码块，更新 isFullscreen 状态
     * 8. 状态更新触发 UI 重组，显示新的布局
     * 
     * 关键点：
     * - 由于 Activity 没有重建，所有 remember 的状态都保持
     * - ExoPlayer 实例不会重新创建，视频持续播放
     * - 只是 UI 布局在改变，底层播放器保持不变
     */
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
             * ====================================================================
             * 【深入理解：为什么旧方式会重新创建播放器和 AndroidView？】
             * ====================================================================
             * 
             * 要理解这个问题，需要了解 Compose 的三个核心机制：
             * 
             * 1. 【Compose 的 Slot Table 机制】
             *    - Compose 使用 Slot Table 来跟踪和管理所有 Composable 的状态
             *    - 每个 Composable 在 Slot Table 中都有一个位置（slot）
             *    - 当重组发生时，Compose 会比较新的组合树和旧的组合树
             *    - 如果 Composable 的类型或 key 改变，Compose 会认为这是不同的组件
             *    - 不同的组件会被销毁并重新创建
             * 
             * 2. 【AnimatedContent 的工作原理】
             *    旧代码示例：
             *    ```
             *    AnimatedContent(targetState = isFullscreen) { fullscreen ->
             *        if (fullscreen) {
             *            VideoPlayer(...)  // 这是组件A
             *        } else {
             *            VideoPlayer(...)  // 这是组件B
             *        }
             *    }
             *    ```
             *    
             *    问题分析：
             *    - AnimatedContent 内部使用 Crossfade 或类似的机制
             *    - 当 targetState 改变时，AnimatedContent 会：
             *      ① 将旧的内容标记为"退出"（exit）
             *      ② 将新的内容标记为"进入"（enter）
             *      ③ 在动画过程中，两个内容同时存在
             *      ④ 动画结束后，旧内容被完全移除
             *    
             *    - 在 Slot Table 中：
             *      * isFullscreen = false 时：VideoPlayer(竖屏) 在 slot[0]
             *      * isFullscreen = true 时：VideoPlayer(横屏) 在 slot[0]
             *      * 虽然都在同一个 slot，但 Compose 认为这是"不同的内容"
             *      * 因为它们在条件分支的不同分支中（if-else）
             *    
             *    - Compose 的判断逻辑：
             *      * 当从 false 切换到 true 时
             *      * Compose 看到 slot[0] 的内容从"VideoPlayer(竖屏)"变成了"VideoPlayer(横屏)"
             *      * 虽然都是 VideoPlayer，但 Compose 无法确定它们是否是同一个实例
             *      * 为了安全，Compose 会：
             *        - 先销毁旧的 VideoPlayer（调用 DisposableEffect 的 onDispose）
             *        - 然后创建新的 VideoPlayer（重新执行 Composable 函数）
             * 
             * 3. 【为什么 VideoPlayer 内部会重新创建 ExoPlayer？】
             *    旧代码示例：
             *    ```
             *    @Composable
             *    fun VideoPlayer(videoUrl: String, ...) {
             *        val exoPlayer = remember {  // 问题在这里！
             *            ExoPlayer.Builder(context).build()
             *        }
             *        // ...
             *    }
             *    ```
             *    
             *    问题分析：
             *    - remember { } 的作用域是它所在的 Composable 函数
             *    - 当 VideoPlayer 被销毁时，remember 中保存的值也会丢失
             *    - 当新的 VideoPlayer 被创建时，remember { } 会重新执行
             *    - 这导致每次创建新的 VideoPlayer 时，都会创建新的 ExoPlayer
             *    
             *    为什么 remember 不能跨组件保持状态？
             *    - remember 使用 Composable 的调用位置作为 key
             *    - 如果 Composable 被销毁，它的调用位置就从 Slot Table 中移除了
             *    - 下次在"相同位置"创建时，Compose 无法知道这是"同一个"组件
             *    - 所以 remember 会重新初始化
             * 
             * 4. 【为什么 AndroidView 会重新创建？】
             *    AndroidView 的特殊性：
             *    - AndroidView 是 Compose 和传统 Android View 系统的桥梁
             *    - 它内部维护一个真实的 Android View 实例
             *    - 当 AndroidView Composable 被销毁时，它持有的 View 也会被移除
             *    
             *    旧代码的问题：
             *    ```
             *    AnimatedContent(targetState = isFullscreen) { fullscreen ->
             *        if (fullscreen) {
             *            VideoPlayer(...)  // 包含 AndroidView
             *        } else {
             *            VideoPlayer(...)  // 包含另一个 AndroidView
             *        }
             *    }
             *    ```
             *    
             *    - 当 isFullscreen 改变时：
             *      ① 旧的 VideoPlayer 被销毁 → 旧的 AndroidView 被销毁 → 旧的 PlayerView 被移除
             *      ② 新的 VideoPlayer 被创建 → 新的 AndroidView 被创建 → 新的 PlayerView 被创建
             *      ③ 新的 PlayerView 需要重新绑定 ExoPlayer
             *      ④ 这个过程中，视频画面会短暂消失（黑屏）
             * 
             * 5. 【为什么新方式可以避免重新创建？】
             *    新代码的关键点：
             *    ```
             *    // 1. ExoPlayer 提升到外层，不在 VideoPlayer 内部
             *    val exoPlayer = remember { ... }  // 在 VideoPlayerScreen 中
             *    
             *    // 2. 使用单个布局，不使用 AnimatedContent
             *    Column {
             *        // 3. 使用 key() 确保 AndroidView 不会重新创建
             *        key(exoPlayer) {
             *            AndroidView(factory = { ... })
             *        }
             *    }
             *    ```
             *    
             *    为什么这样可以工作：
             *    - ExoPlayer 在外层 remember，不会因为布局改变而重新创建
             *    - 使用单个 Column，布局结构不变，只是 modifier 在改变
             *    - key(exoPlayer) 告诉 Compose：
             *      * 只要 exoPlayer 实例不变，这个 AndroidView 就是"同一个"
             *      * 即使 modifier 改变，也不要重新创建 View
             *      * 只需要更新布局参数
             *    - 这样 PlayerView 实例保持不变，只是布局尺寸在改变
             *    - 视频可以持续播放，不会中断
             * 
             * 6. 【key() 的作用机制】
             *    key() 的工作原理：
             *    - key() 为 Composable 提供一个稳定的标识符
             *    - Compose 使用这个标识符来判断是否是"同一个"组件
             *    - 如果 key 不变，Compose 会复用现有的组件实例
             *    - 如果 key 改变，Compose 会销毁旧组件并创建新组件
             *    
             *    示例：
             *    ```
             *    key(userId) {  // 如果 userId 不变，这个组件不会重新创建
             *        UserProfile(userId)
             *    }
             *    ```
             *    
             *    在我们的代码中：
             *    ```
             *    key(exoPlayer) {  // exoPlayer 实例不变，所以 AndroidView 不会重新创建
             *        AndroidView(...)
             *    }
             *    ```
             * 
             * ====================================================================
             * 【总结】
             * ====================================================================
             * 
             * 旧方式的问题根源：
             * 1. AnimatedContent 导致组件被销毁和重新创建
             * 2. VideoPlayer 内部的 remember 无法跨组件保持状态
             * 3. AndroidView 随着组件的销毁而销毁
             * 
             * 新方式的解决方案：
             * 1. 不使用 AnimatedContent，使用单个布局 + animateContentSize
             * 2. 将 ExoPlayer 提升到外层，使用 remember 保持
             * 3. 使用 key() 确保 AndroidView 不会重新创建
             * 4. 只改变 modifier，不改变组件结构
             * 
             * ====================================================================
             */
            
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