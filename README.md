# 横竖屏切换播放器平滑过渡

## 使用remember保持ExoPlayer实例
- 旧的方式会创建新的组件和新的Exoplayer
- 但是remember能确保Exoplayer只创建一次，不会因为状态改变而重建

## 使用key()确保AndroidView不会重新创建
- 旧的每次切换都会创建新的PlayerView
- 新的方式确保只要实例不变就复用AndroidView

## 通过modifier改变布局而不是重新创建组件
- 只改变布局约束，不重新创建View


/**
 * ====================================================================
 * 【Android Activity 横竖屏切换的完整流程解析】
 * ====================================================================
 * 
 * 一、默认情况（未配置 configChanges）下的横竖屏切换流程：
 * 
 * 当用户旋转设备或程序调用 setRequestedOrientation() 时，Android 系统会执行以下步骤：
 * 
 * 1. 【系统检测配置变更】
 *    - 系统检测到屏幕方向（orientation）改变
 *    - 系统检测到屏幕尺寸（screenSize）可能改变
 *    - 系统检测到屏幕布局（screenLayout）可能改变
 * 
 * 2. 【保存当前状态】
 *    - 系统调用 Activity.onSaveInstanceState(Bundle outState)
 *    - 这个方法用于保存 Activity 的状态，以便重建时恢复
 *    - 例如：保存用户输入、列表滚动位置等
 * 
 * 3. 【销毁当前 Activity】
 *    - 系统调用 Activity.onPause()
 *    - 系统调用 Activity.onStop()
 *    - 系统调用 Activity.onDestroy()
 *    - Activity 实例被完全销毁
 *    - 所有成员变量、View 层次结构都被销毁
 * 
 * 4. 【重新创建 Activity】
 *    - 系统创建新的 Activity 实例
 *    - 系统调用 Activity.onCreate(Bundle savedInstanceState)
 *    - savedInstanceState 包含之前保存的状态
 *    - 系统调用 Activity.onStart()
 *    - 系统调用 Activity.onResume()
 * 
 * 5. 【重新加载资源】
 *    - 系统根据新的配置（横屏/竖屏）重新加载资源
 *    - 例如：从 layout-port/ 切换到 layout-land/
 *    - 从 values-port/ 切换到 values-land/
 * 
 * 问题：
 * - Activity 被完全销毁和重建，所有状态都会丢失（除非保存在 savedInstanceState 中）
 * - 正在播放的视频会中断
 * - 用户输入会丢失
 * - 性能开销大（需要重新创建整个 Activity）
 * 
 * ====================================================================
 * 
 * 二、配置了 configChanges 后的横竖屏切换流程：
 * 
 * 在我们的 AndroidManifest.xml 中配置了：
 * android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
 * 
 * 这个配置告诉系统："当这些配置改变时，不要销毁和重建 Activity，而是通知我"
 * 
 * 1. 【系统检测配置变更】
 *    - 系统检测到 orientation、screenSize、screenLayout 或 keyboardHidden 改变
 *    - 由于配置了 configChanges，系统不会销毁 Activity
 * 
 * 2. 【调用 onConfigurationChanged()】
 *    - 系统调用 Activity.onConfigurationChanged(Configuration newConfig)
 *    - 这个方法接收新的配置信息
 *    - Activity 实例保持不变
 *    - 所有成员变量、View 层次结构都保持不变
 * 
 * 3. 【手动处理配置变更】
 *    - 开发者需要在 onConfigurationChanged() 中手动处理配置变更
 *    - 例如：更新布局、重新加载资源等
 *    - 如果不处理，UI 可能不会自动适应新的配置
 * 
 * 优势：
 * - Activity 不会被销毁，所有状态都保持
 * - 正在播放的视频不会中断
 * - 性能更好（不需要重新创建 Activity）
 * - 可以手动控制如何处理配置变更
 * 
 * ====================================================================
 * 
 * 三、我们项目中的处理方式：
 * 
 * 1. 【AndroidManifest.xml 配置】
 *    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
 *    - 告诉系统：这些配置改变时，不要重建 Activity
 * 
 * 2. 【Compose 的自动处理】
 *    - Compose 使用 LocalConfiguration 来监听配置变更
 *    - 当配置改变时，Compose 会自动重组（recompose）
 *    - 我们通过 LaunchedEffect(screenOrientation) 来响应方向变化
 * 
 * 3. 【代码中的处理流程】
 *    ```
 *    val configuration = LocalConfiguration.current
 *    val screenOrientation = configuration.orientation
 *    LaunchedEffect(screenOrientation) {
 *        isFullscreen = screenOrientation == Configuration.ORIENTATION_LANDSCAPE
 *    }
 *    ```
 *    - LocalConfiguration 会自动获取最新的配置
 *    - 当屏幕方向改变时，screenOrientation 会更新
 *    - LaunchedEffect 会检测到变化并执行代码块
 *    - 我们更新 isFullscreen 状态，触发 UI 重组
 * 
 * 4. 【为什么这样设计？】
 *    - 保持 Activity 不重建，避免视频播放中断
 *    - 使用 Compose 的响应式特性，自动适应配置变更
 *    - 通过状态管理，平滑地切换横竖屏布局
 * 
 * ====================================================================
 * 
 * 四、configChanges 各个参数的含义：
 * 
 * - orientation: 屏幕方向改变（竖屏 ↔ 横屏）
 * - screenSize: 屏幕尺寸改变（例如：从手机切换到平板）
 * - screenLayout: 屏幕布局改变（例如：多窗口模式）
 * - keyboardHidden: 键盘显示/隐藏状态改变
 * 
 * 注意：如果配置了 configChanges，但没有在 onConfigurationChanged() 中处理，
 *       某些 UI 可能不会自动更新。但 Compose 会自动处理，所以我们可以依赖 Compose。
 * 
 * ====================================================================
 * 
 * 五、完整的生命周期对比：
 * 
 * 【默认情况（未配置 configChanges）】
 * 竖屏 → 横屏：
 *   onPause() → onSaveInstanceState() → onStop() → onDestroy()
 *   → onCreate() → onStart() → onResume()
 * 
 * 【配置了 configChanges】
 * 竖屏 → 横屏：
 *   onConfigurationChanged() （Activity 不重建）
 * 
 * ====================================================================
 */
