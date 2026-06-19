/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import android.provider.MediaStore
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.webkit.MimeTypeMap
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.hippo.app.EditTextDialogBuilder
import com.hippo.ehviewer.AppConfig
import com.hippo.ehviewer.BuildConfig
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.coil.loadReaderPreviewCache
import com.hippo.ehviewer.coil.saveReaderPreviewCache
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.client.data.GalleryPreview
import com.hippo.ehviewer.gallery.ArchiveGalleryProvider
import com.hippo.ehviewer.gallery.EhGalleryProvider
import com.hippo.ehviewer.gallery.GalleryProvider2
import com.hippo.ehviewer.widget.GalleryGuideView
import com.hippo.ehviewer.widget.GalleryHeader
import com.hippo.ehviewer.widget.ReaderSidebarThumb
import com.hippo.ehviewer.widget.ReversibleSeekBar
import com.hippo.glgallery.GalleryProvider
import com.hippo.glgallery.GalleryView
import com.hippo.glgallery.SimpleAdapter
import com.hippo.glview.view.GLRootView
import com.hippo.unifile.UniFile
import com.hippo.util.ExceptionUtils
import com.hippo.util.getParcelableCompat
import com.hippo.util.getParcelableExtraCompat
import com.hippo.util.isAtLeastP
import com.hippo.util.isAtLeastQ
import com.hippo.util.launchIO
import com.hippo.util.sendTo
import com.hippo.util.withUIContext
import com.hippo.widget.ColorView
import com.hippo.widget.LoadImageView
import com.hippo.yorozuya.AnimationUtils
import com.hippo.yorozuya.ConcurrentPool
import com.hippo.yorozuya.FileUtils
import com.hippo.yorozuya.MathUtils
import com.hippo.yorozuya.ResourcesUtils
import com.hippo.yorozuya.SimpleAnimatorListener
import com.hippo.yorozuya.SimpleHandler
import com.hippo.yorozuya.ViewUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.core.res.isNight
import rikka.core.res.resolveColor
import java.io.File
import java.io.IOException
import java.util.BitSet
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class GalleryActivity :
    EhActivity(),
    OnSeekBarChangeListener,
    GalleryView.Listener {
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        if (result && mSavingPage != -1) {
            saveImage(mSavingPage)
        } else {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
        }
        mSavingPage = -1
    }
    private val saveImageToLauncher = registerForActivityResult(
        CreateDocument("todo/todo"),
    ) { uri ->
        if (uri != null) {
            val filepath = AppConfig.getExternalTempDir().toString() + File.separator + mCacheFileName
            val cacheFile = File(filepath)
            lifecycleScope.launchIO {
                try {
                    ParcelFileDescriptor.open(cacheFile, MODE_READ_ONLY).use { from ->
                        contentResolver.openFileDescriptor(uri, "w")!!.use {
                            from sendTo it
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    runOnUiThread {
                        Toast.makeText(
                            this@GalleryActivity,
                            getString(R.string.image_saved, uri.path),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                cacheFile.delete()
            }
        }
    }
    private val mHideSliderRunnable = Runnable {
        mSeekBarPanel?.let { hideSlider(it) }
    }
    private val mHideReaderSidebarToggleRunnable = Runnable {
        mReaderSidebarToggle?.animate()?.alpha(getReaderSidebarToggleRestAlpha())?.setDuration(READER_SIDEBAR_TOGGLE_FADE_DURATION)?.start()
    }
    private val mHideSliderListener: SimpleAnimatorListener = object : SimpleAnimatorListener() {
        override fun onAnimationEnd(animation: Animator) {
            mSeekBarPanelAnimator = null
            mSeekBarPanel?.visibility = View.INVISIBLE
        }
    }
    private val mUpdateSliderListener = AnimatorUpdateListener {
        mSeekBarPanel?.requestLayout()
    }
    private val mShowSliderListener: SimpleAnimatorListener = object : SimpleAnimatorListener() {
        override fun onAnimationEnd(animation: Animator) {
            mSeekBarPanelAnimator = null
        }
    }
    private val mNotifyTaskPool = ConcurrentPool<NotifyTask>(3)
    private var mAction: String? = null
    private var mFilename: String? = null
    private var mUri: Uri? = null
    private var mGalleryInfo: GalleryInfo? = null
    private var mPage = 0
    private var mCacheFileName: String? = null
    private var mGLRootView: GLRootView? = null
    private var mGalleryView: GalleryView? = null
    private var mGalleryProvider: GalleryProvider2? = null
    private var mGalleryAdapter: GalleryAdapter? = null
    private var insetsController: WindowInsetsControllerCompat? = null
    private var mMaskView: ColorView? = null
    private var mClock: View? = null
    private var mProgress: TextView? = null
    private var mBattery: View? = null
    private var mSeekBarPanel: View? = null
    private var mGLLoading: View? = null
    private var mLeftText: TextView? = null
    private var mRightText: TextView? = null
    private var mSeekBar: ReversibleSeekBar? = null
    private var mAutoTransfer: ImageView? = null
    private var mReaderSidebarRecyclerView: RecyclerView? = null
    private var mReaderSidebarContainer: View? = null
    private var mReaderSidebarDivider: View? = null
    private var mReaderSidebarToggle: ImageView? = null
    private var mReaderContentContainer: View? = null
    private var mReaderSidebarAdapter: ReaderSidebarAdapter? = null
    private var mReaderSidebarPreviewMap = linkedMapOf<Int, GalleryPreview>()
    private var mInitialReaderPreviews: ArrayList<GalleryPreview>? = null
    private val mSpreadPages = BitSet()
    private var mReaderPreviewMetadataRequested = false
    private var mReaderPreviewCacheLoaded = false
    private var mReaderSidebarPageStarts: List<Int> = emptyList()
    private var mReaderSidebarVisible = true
    private var mReaderSidebarOnRight = true
    private var mReaderSidebarPreviewJob: Job? = null
    private val mSidebarScrollHandler = Handler(Looper.getMainLooper())
    private var mSidebarScrollRunnable: Runnable? = null
    private var mSidebarUserScrolling = false
    private var mSidebarDataPending = false
    private var mSidebarPendingPageStarts: List<Int>? = null
    private var mSidebarPendingPreviewUpdate = false
    private var mSidebarAnimator: ValueAnimator? = null
    private var mSidebarAnimGeneration = 0
    private var mSidebarProgrammaticScroll = false
    private var mReaderSidebarToggleDownRawX = 0f
    private var mReaderSidebarToggleDragging = false
    private var mReaderSidebarToggleTouchSlop = 0
    private var mSeekBarPanelAnimator: ObjectAnimator? = null
    private var mLayoutMode = 0
    private var mSize = 0
    private var mCurrentIndex = 0
    private var mSavingPage = -1
    private lateinit var builder: EditTextDialogBuilder
    private lateinit var dialog: AlertDialog
    private var dialogShown = false
    private var mAutoTransferJob: Job? = null
    private var mTurnPageIntervalVal = Settings.turnPageInterval
    private val isDoublePageMode: Boolean
        get() = if (Settings.doublePageModeLandscape) {
            Settings.doublePageMode && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        } else {
            Settings.doublePageMode
        }

    private val useReaderThumbnailSidebarLayout: Boolean
        get() = Settings.layoutReaderThumbnailSidebar

    private fun AlertDialog.applyAmoledBlack() {
        if (Settings.blackDarkTheme && (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            show()
            findViewById<View>(com.google.android.material.R.id.parentPanel)
                ?.setBackgroundColor(Color.BLACK)
        } else {
            show()
        }
    }

    private val galleryDetailUrl: String?
        get() {
            val gid: Long
            val token: String
            if (mGalleryInfo != null) {
                gid = mGalleryInfo!!.gid
                token = mGalleryInfo!!.token!!
            } else {
                return null
            }
            return EhUrl.getGalleryDetailUrl(gid, token, 0, false)
        }

    private fun buildProvider(replace: Boolean = false) {
        if (mGalleryProvider != null) {
            if (replace) mGalleryProvider!!.stop() else return
        }
        if (ACTION_EH == mAction) {
            mGalleryInfo?.let { mGalleryProvider = EhGalleryProvider(it) }
        } else if (Intent.ACTION_VIEW == mAction) {
            if (mUri != null) {
                try {
                    grantUriPermission(
                        BuildConfig.APPLICATION_ID,
                        mUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.error_reading_failed, Toast.LENGTH_SHORT).show()
                }
                val continuation: AtomicReference<Continuation<String>?> = AtomicReference(null)
                mGalleryProvider = ArchiveGalleryProvider(
                    this,
                    mUri!!,
                    flow {
                        if (!dialogShown) {
                            withUIContext {
                                dialogShown = true
                                dialog.run {
                                    applyAmoledBlack()
                                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                        val passwd = builder.text
                                        if (passwd.isEmpty()) {
                                            builder.setError(getString(R.string.passwd_cannot_be_empty))
                                        } else {
                                            continuation.getAndSet(null)?.resume(passwd)
                                        }
                                    }
                                    setOnCancelListener {
                                        finish()
                                    }
                                }
                            }
                        }
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val r = suspendCancellableCoroutine {
                                continuation.set(it)
                                it.invokeOnCancellation { dialog.dismiss() }
                            }
                            emit(r)
                            withUIContext {
                                builder.setError(getString(R.string.passwd_wrong))
                            }
                        }
                    },
                )
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        mAction = intent.action
        mFilename = intent.getStringExtra(KEY_FILENAME)
        mUri = intent.data
        mGalleryInfo = intent.getParcelableExtraCompat(KEY_GALLERY_INFO)
        mInitialReaderPreviews = intent.getGalleryPreviewArrayListExtra(KEY_INITIAL_READER_PREVIEWS)
        mPage = intent.getIntExtra(KEY_PAGE, -1)
    }

    private fun onInit() {
        handleIntent(intent)
        buildProvider()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        buildProvider(true)
        mGalleryProvider?.let {
            lifecycleScope.launchIO {
                it.start()
                if (it.awaitReady()) {
                    withUIContext {
                        mCurrentIndex = 0
                        setGallery()
                    }
                }
            }
        }
    }

    private fun onRestore(savedInstanceState: Bundle) {
        mAction = savedInstanceState.getString(KEY_ACTION)
        mFilename = savedInstanceState.getString(KEY_FILENAME)
        mUri = savedInstanceState.getParcelableCompat(KEY_URI)
        mGalleryInfo = savedInstanceState.getParcelableCompat(KEY_GALLERY_INFO)
        mInitialReaderPreviews = savedInstanceState.getGalleryPreviewArrayList(KEY_INITIAL_READER_PREVIEWS)
        mPage = savedInstanceState.getInt(KEY_PAGE, -1)
        mCurrentIndex = savedInstanceState.getInt(KEY_CURRENT_INDEX)
        buildProvider()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACTION, mAction)
        outState.putString(KEY_FILENAME, mFilename)
        outState.putParcelable(KEY_URI, mUri)
        if (mGalleryInfo != null) {
            outState.putParcelable(KEY_GALLERY_INFO, mGalleryInfo)
        }
        if (!mInitialReaderPreviews.isNullOrEmpty()) {
            outState.putParcelableArrayList(KEY_INITIAL_READER_PREVIEWS, mInitialReaderPreviews)
        }
        outState.putInt(KEY_PAGE, mPage)
        outState.putInt(KEY_CURRENT_INDEX, mCurrentIndex)
    }

    override fun attachBaseContext(newBase: Context) {
        delegate.localNightMode = when (Settings.readTheme) {
            1 -> AppCompatDelegate.MODE_NIGHT_YES
            2 -> AppCompatDelegate.MODE_NIGHT_NO
            else -> Settings.theme
        }
        super.attachBaseContext(newBase)
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Settings.readingFullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            onInit()
        } else {
            onRestore(savedInstanceState)
        }
        builder = EditTextDialogBuilder(this, null, getString(R.string.archive_passwd))
        builder.setTitle(getString(R.string.archive_need_passwd))
        builder.setPositiveButton(getString(android.R.string.ok), null)
        dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)

        mGalleryProvider.let {
            if (it == null) {
                finish()
                return
            }
            initializeGallery()
            lifecycleScope.launchIO {
                it.start()
                if (it.awaitReady()) withUIContext { setGallery() }
            }
        }
    }

    private fun initializeGallery() {
        setContentView(if (useReaderThumbnailSidebarLayout) R.layout.activity_gallery_large else R.layout.activity_gallery)
        mGLRootView = ViewUtils.`$$`(this, R.id.gl_root_view) as GLRootView
        mMaskView = ViewUtils.`$$`(this, R.id.mask) as ColorView
        mClock = ViewUtils.`$$`(this, R.id.clock)
        mProgress = ViewUtils.`$$`(this, R.id.progress) as TextView
        mBattery = ViewUtils.`$$`(this, R.id.battery)
        mSeekBarPanel = ViewUtils.`$$`(this, R.id.seek_bar_panel)
        mGLLoading = ViewUtils.`$$`(this, R.id.gl_loading)
        mLeftText = ViewUtils.`$$`(mSeekBarPanel, R.id.left) as TextView
        mRightText = ViewUtils.`$$`(mSeekBarPanel, R.id.right) as TextView
        mSeekBar = ViewUtils.`$$`(mSeekBarPanel, R.id.seek_bar) as ReversibleSeekBar
        mAutoTransfer = ViewUtils.`$$`(mSeekBarPanel, R.id.auto_transfer) as ImageView
        mClock!!.visibility = if (Settings.showClock) View.VISIBLE else View.GONE
        mProgress!!.visibility = if (Settings.showProgress) View.VISIBLE else View.GONE
        mBattery!!.visibility = if (Settings.showBattery) View.VISIBLE else View.GONE
        mMaskView!!.setOnGenericMotionListener { _: View?, event: MotionEvent ->
            if (mGalleryView == null) {
                return@setOnGenericMotionListener false
            }
            if (event.action == MotionEvent.ACTION_SCROLL) {
                val scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 300
                val isNext = scroll < 0.0f

                when (mLayoutMode) {
                    GalleryView.LAYOUT_RIGHT_TO_LEFT -> {
                        mGalleryView?.run { if (isNext) pageLeft() else pageRight() }
                    }

                    GalleryView.LAYOUT_LEFT_TO_RIGHT -> {
                        mGalleryView?.run { if (isNext) pageRight() else pageLeft() }
                    }

                    GalleryView.LAYOUT_TOP_TO_BOTTOM -> {
                        mGalleryView?.onScroll(0f, -scroll, 0f, -scroll, 0f, -scroll)
                    }
                }
            }
            false
        }
        mSeekBar!!.setOnSeekBarChangeListener(this)
        mAutoTransfer!!.setOnClickListener { autoTransfer() }
        mReaderContentContainer = findViewById(R.id.reader_content_container)
        mReaderSidebarDivider = findViewById(R.id.reader_sidebar_divider)
        mReaderSidebarContainer = findViewById(R.id.reader_sidebar_container)
        mReaderSidebarToggle = findViewById(R.id.reader_sidebar_toggle)
        mReaderSidebarRecyclerView = findViewById(R.id.reader_sidebar_list)
        if (mReaderSidebarRecyclerView != null) {
            mReaderSidebarAdapter = ReaderSidebarAdapter()
            mReaderSidebarRecyclerView!!.layoutManager = LinearLayoutManager(this)
            mReaderSidebarRecyclerView!!.adapter = mReaderSidebarAdapter
            mReaderSidebarVisible = Settings.layoutReaderThumbnailSidebarVisible
            mReaderSidebarOnRight = Settings.layoutReaderThumbnailSidebarOnRight
            mReaderSidebarToggleTouchSlop = ViewConfiguration.get(this).scaledTouchSlop
            mReaderSidebarRecyclerView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                        if (mSidebarProgrammaticScroll) {
                            // Programmatic auto-center scroll — block concurrent auto-center until idle
                            mSidebarUserScrolling = true
                        } else {
                            // User-initiated scroll
                            mSidebarUserScrolling = true
                            // Cancel any pending auto-center scroll so it doesn't fight with user gesture
                            mSidebarScrollRunnable?.let { mSidebarScrollHandler.removeCallbacks(it) }
                        }
                    }
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        mSidebarUserScrolling = false
                        mSidebarProgrammaticScroll = false
                        if (mSidebarDataPending) {
                            mSidebarDataPending = false
                            val pending = mSidebarPendingPageStarts
                            mSidebarPendingPageStarts = null
                            if (pending != null) {
                                mReaderSidebarPageStarts = pending
                            }
                            mReaderSidebarAdapter?.flushPendingData(pending)
                        } else if (mSidebarPendingPreviewUpdate) {
                            mReaderSidebarAdapter?.flushPendingData(null)
                        }
                        // Don't forceCenter — flushPendingData already preserves scroll position
                        updateReaderSidebarSelection(forceCenter = false)
                    }
                }
            })
            mReaderSidebarToggle?.setOnClickListener { toggleReaderSidebar() }
            mReaderSidebarToggle?.setOnTouchListener { view, event -> onReaderSidebarToggleTouch(view, event) }
            updateReaderSidebarVisibility(showHiddenIndicator = !mReaderSidebarVisible)
            updateReaderSidebarData(forceCenter = true)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (Settings.readingFullscreen) {
            insetsController!!.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController!!.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController!!.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            insetsController!!.show(WindowInsetsCompat.Type.systemBars())
        }
        val night = resources.configuration.isNight()
        insetsController!!.isAppearanceLightStatusBars = !night

        // Cutout
        if (isAtLeastP) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val galleryHeader = findViewById<GalleryHeader>(R.id.gallery_header)
        ViewCompat.setOnApplyWindowInsetsListener(galleryHeader) { _: View?, insets: WindowInsetsCompat ->
            if (!Settings.readingFullscreen) {
                galleryHeader.setTopInsets(insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            } else {
                galleryHeader.setDisplayCutout(insets.displayCutout)
            }
            WindowInsetsCompat.CONSUMED
        }

        // Screen lightness
        setScreenLightness(Settings.customScreenLightness, Settings.screenLightness)

        // Update keep screen on
        if (Settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Orientation
        requestedOrientation = when (Settings.screenRotation) {
            0 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            3 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        // Guide
        if (Settings.guideGallery) {
            val mainLayout = ViewUtils.`$$`(this, R.id.main) as FrameLayout
            mainLayout.addView(GalleryGuideView(this))
        }
    }

    private fun setGallery() {
        if (mGalleryProvider?.isReady != true) return

        // TODO: Not well place to call it
        dialog.dismiss()

        mGLLoading?.visibility = View.GONE
        mGLRootView?.visibility = View.VISIBLE
        // Get start page
        if (mCurrentIndex == 0) mCurrentIndex = if (mPage >= 0) mPage else mGalleryProvider!!.startPage
        mGalleryAdapter = GalleryAdapter(mGLRootView!!, mGalleryProvider!!)
        resetReaderPreviewState()
        val resources = resources
        mGalleryView = GalleryView.Builder(this, mGalleryAdapter!!)
            .setListener(this)
            .setLayoutMode(Settings.readingDirection)
            .setScaleMode(Settings.pageScaling)
            .setStartPosition(Settings.startPosition)
            .setStartPage(mCurrentIndex)
            .setDoublePageMode(isDoublePageMode)
            .setDoublePageOffset(Settings.doublePageOffset)
            .setDoublePageGap(Settings.doublePageGap)
            .setSpreadPages(mSpreadPages)
            .setBackgroundColor(theme.resolveColor(android.R.attr.colorBackground))
            .setPagerInterval(if (Settings.showPageInterval) resources.getDimensionPixelOffset(R.dimen.gallery_pager_interval) else 0)
            .setScrollInterval(if (Settings.showPageInterval) resources.getDimensionPixelOffset(R.dimen.gallery_scroll_interval) else 0)
            .setPageMinHeight(resources.getDimensionPixelOffset(R.dimen.gallery_page_min_height))
            .setPageInfoInterval(resources.getDimensionPixelOffset(R.dimen.gallery_page_info_interval))
            .setProgressColor(ResourcesUtils.getAttrColor(this, androidx.appcompat.R.attr.colorPrimary))
            .setProgressSize(resources.getDimensionPixelOffset(R.dimen.gallery_progress_size))
            .setPageTextColor(theme.resolveColor(android.R.attr.textColorSecondary))
            .setPageTextSize(resources.getDimensionPixelOffset(R.dimen.gallery_page_text_size))
            .setPageTextTypeface(Typeface.DEFAULT)
            .setErrorTextColor(this@GalleryActivity.getColor(R.color.red_500))
            .setErrorTextSize(resources.getDimensionPixelOffset(R.dimen.gallery_error_text_size))
            .setEmptyString(resources.getString(R.string.error_empty))
            .build()
        mGLRootView!!.setContentPane(mGalleryView)
        mGalleryProvider!!.setListener(mGalleryAdapter)
        mGalleryProvider!!.setGLRoot(mGLRootView!!)
        if (mGalleryView != null) {
            mLayoutMode = mGalleryView!!.layoutMode
        }
        mSize = mGalleryProvider!!.size
        mGalleryView?.setSpreadPages(mSpreadPages)
        updateDoublePageMode()
        updateSlider()
        updateProgress()
        updateReaderSidebarData(forceCenter = true)
        ensureReaderPreviewMetadata()
    }

    private fun updateDoublePageMode() {
        if (mGalleryView == null) return
        mGalleryView!!.setDoublePageMode(isDoublePageMode)
        mGalleryView!!.setDoublePageOffset(Settings.doublePageOffset)
        mGalleryView!!.setDoublePageGap(Settings.doublePageGap)
        ensureReaderPreviewMetadata()
        updateReaderSidebarData(forceCenter = true)
    }

    private fun shouldLoadReaderPreviewMetadata(): Boolean = mAction == ACTION_EH && (mReaderSidebarRecyclerView != null || isDoublePageMode)

    private fun ensureReaderPreviewMetadata() {
        if (!shouldLoadReaderPreviewMetadata() || mReaderPreviewMetadataRequested) {
            return
        }
        mReaderPreviewMetadataRequested = true
        loadReaderPreviewMetadata()
    }

    private fun pageTurn(isPrevious: Boolean) {
        val isRTL = mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT
        if (isPrevious xor isRTL) {
            mGalleryView?.pageLeft()
        } else {
            mGalleryView?.pageRight()
        }
    }

    private fun autoTransfer() {
        if (mAutoTransferJob == null && mCurrentIndex + 1 != mSize) {
            startAutoTransfer()
        } else {
            stopAutoTransfer()
        }
    }

    private fun startAutoTransfer() {
        mAutoTransfer?.setImageResource(R.drawable.v_pause_x24)
        mAutoTransferJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(mTurnPageIntervalVal.coerceAtLeast(1) * 1000L)
                    pageTurn(false)
                }
            }
        }
    }

    private fun stopAutoTransfer() {
        mAutoTransfer?.setImageResource(R.drawable.v_play_x24)
        mAutoTransferJob?.cancel()
        mAutoTransferJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mAutoTransferJob?.cancel()
        mGLRootView = null
        mGalleryView = null
        if (mGalleryAdapter != null) {
            mGalleryAdapter!!.clearUploader()
            mGalleryAdapter = null
        }
        if (mGalleryProvider != null) {
            mGalleryProvider!!.setListener(null)
            mGalleryProvider!!.stop()
            mGalleryProvider = null
        }
        mMaskView = null
        mClock = null
        mProgress = null
        mBattery = null
        mSeekBarPanel = null
        mGLLoading = null
        mReaderSidebarPreviewJob?.cancel()
        mReaderSidebarPreviewJob = null
        mReaderSidebarRecyclerView = null
        mReaderSidebarAdapter = null
        mReaderSidebarPreviewMap.clear()
        mLeftText = null
        mRightText = null
        mSeekBar = null
        mAutoTransfer = null
        mAutoTransferJob = null
        SimpleHandler.getInstance().removeCallbacks(mHideSliderRunnable)
    }

    override fun onPause() {
        super.onPause()
        mGLRootView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        mGLRootView?.onResume()
        mReaderSidebarRecyclerView?.post { restoreVisibleReaderSidebarPreviews() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        mGalleryView ?: return super.onKeyDown(keyCode, event)

        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!Settings.volumePage) {
                    false
                } else {
                    val isPrevious = Settings.reverseVolumePage.xor(keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                    val shouldTurn = event.repeatCount % (Settings.volumePageInterval + 1) == 0

                    if (shouldTurn) pageTurn(isPrevious)
                    true
                }
            }

            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP -> pageTurn(true).let { true }

            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> pageTurn(false).let { true }

            KeyEvent.KEYCODE_DPAD_LEFT -> mGalleryView!!.pageLeft().let { true }

            KeyEvent.KEYCODE_DPAD_RIGHT -> mGalleryView!!.pageRight().let { true }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MENU -> {
                onTapMenuArea()
                true
            }

            else -> false
        } ||
            super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Check volume
        if (Settings.volumePage) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP
            ) {
                return true
            }
        }

        // Check keyboard and Dpad
        return if (keyCode == KeyEvent.KEYCODE_PAGE_UP ||
            keyCode == KeyEvent.KEYCODE_PAGE_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_SPACE ||
            keyCode == KeyEvent.KEYCODE_MENU
        ) {
            true
        } else {
            super.onKeyUp(keyCode, event)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateProgress() {
        if (mCurrentIndex + 1 == mSize) autoTransfer()
        if (mSize <= 0 || mCurrentIndex < 0) {
            mProgress?.text = null
            return
        }
        val range = getPageRange(mCurrentIndex)
        mProgress?.text = "$range/$mSize"
    }

    private fun getPageRange(index: Int): String {
        val alignedIndex = mGalleryView?.getPagePairStart(index) ?: index
        val count = getStablePagePairSize(alignedIndex)
        return if (count > 1) {
            "${alignedIndex + 1}-${alignedIndex + 2}"
        } else {
            (alignedIndex + 1).toString()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateSlider() {
        if (mSeekBar == null || mRightText == null || mLeftText == null || mSize <= 0 || mCurrentIndex < 0) {
            return
        }
        val start: TextView
        val end: TextView
        if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
            start = mRightText!!
            end = mLeftText!!
            mSeekBar!!.setReverse(true)
        } else {
            start = mLeftText!!
            end = mRightText!!
            mSeekBar!!.setReverse(false)
        }
        start.text = getPageRange(mCurrentIndex)
        end.text = mSize.toString()
        mSeekBar!!.max = mSize - 1
        mSeekBar!!.progress = mCurrentIndex
    }

    private fun buildReaderSidebarPageStarts(): List<Int> {
        if (mSize <= 0) {
            return emptyList()
        }
        val result = ArrayList<Int>()
        var index = 0
        while (index < mSize) {
            result.add(index)
            val pairSize = getStablePagePairSize(index).coerceAtLeast(1)
            index += pairSize
        }
        return result
    }

    private fun resetReaderPreviewState() {
        mReaderSidebarPreviewMap.clear()
        mSpreadPages.clear()
        mReaderPreviewMetadataRequested = false
        mReaderPreviewCacheLoaded = false
        mReaderSidebarPageStarts = emptyList()
        mergeInitialReaderPreviews()
        loadReaderPreviewCacheFromDisk()
    }

    private fun loadReaderPreviewCacheFromDisk() {
        val galleryInfo = mGalleryInfo ?: return
        val token = galleryInfo.token ?: return
        val cached = loadReaderPreviewCache(galleryInfo.gid, token) ?: return
        cached.forEach { (_, preview) ->
            if (preview.position >= 0 && preview.position !in mReaderSidebarPreviewMap) {
                mReaderSidebarPreviewMap[preview.position] = preview
                updateSpreadPage(preview)
            }
        }
        mReaderPreviewCacheLoaded = true
        // Persist merged data so `mInitialReaderPreviews` updates are not lost
        // on subsequent sessions when disk cache exists.
        saveReaderPreviewCache(galleryInfo.gid, token, mReaderSidebarPreviewMap)
    }

    private fun mergeInitialReaderPreviews() {
        val previews = mInitialReaderPreviews ?: return
        previews.forEach { preview ->
            if (preview.position >= 0) {
                mReaderSidebarPreviewMap[preview.position] = preview
                updateSpreadPage(preview)
            }
        }
    }

    private fun getStablePagePairSize(index: Int): Int {
        val galleryView = mGalleryView ?: return 1
        val pairSize = galleryView.getPagePairSize(index)
        if (!isDoublePageMode || pairSize <= 1) {
            return 1
        }
        // When preview metadata is not yet loaded, trust GalleryView's slot-based
        // pairing (all pages assumed paired unless doublePageOffset applies).
        // Once metadata arrives, downgrade to single if either page is a spread.
        val currentPreview = mReaderSidebarPreviewMap[index]
        val secondIndex = index + 1
        if (secondIndex >= mSize) {
            return 1
        }
        if (currentPreview?.hasPreviewAspect() == true) {
            if (currentPreview.previewWidth > currentPreview.previewHeight) {
                return 1
            }
            val secondPreview = mReaderSidebarPreviewMap[secondIndex]
            if (secondPreview?.hasPreviewAspect() == true && secondPreview.previewWidth > secondPreview.previewHeight) {
                return 1
            }
        }
        return pairSize
    }

    @Suppress("DEPRECATION")
    private fun Intent.getGalleryPreviewArrayListExtra(key: String): ArrayList<GalleryPreview>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        getParcelableArrayListExtra(key, GalleryPreview::class.java)
    } else {
        getParcelableArrayListExtra(key)
    }

    @Suppress("DEPRECATION")
    private fun Bundle.getGalleryPreviewArrayList(key: String): ArrayList<GalleryPreview>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        getParcelableArrayList(key, GalleryPreview::class.java)
    } else {
        getParcelableArrayList(key)
    }

    private fun updateReaderSidebarData(forceCenter: Boolean = false) {
        val pageStarts = buildReaderSidebarPageStarts()
        mReaderSidebarAdapter?.updateData(mSize, mReaderSidebarPreviewMap, pageStarts)
        // Only update mReaderSidebarPageStarts if adapter actually applied the change
        // (not deferred due to user scrolling), to keep Activity and adapter state consistent
        if (!mSidebarDataPending) {
            mReaderSidebarPageStarts = pageStarts
        }
        updateReaderSidebarSelection(forceCenter)
    }

    private fun refreshReaderSidebarStructure(forceCenter: Boolean = false) {
        val pageStarts = buildReaderSidebarPageStarts()
        if (pageStarts != mReaderSidebarPageStarts) {
            mReaderSidebarAdapter?.updateData(mSize, mReaderSidebarPreviewMap, pageStarts)
            if (!mSidebarDataPending) {
                mReaderSidebarPageStarts = pageStarts
            }
        }
        updateReaderSidebarSelection(forceCenter)
    }

    private fun updateReaderSidebarSelection(forceCenter: Boolean = true) {
        val adapter = mReaderSidebarAdapter ?: return
        val galleryView = mGalleryView
        val alignedIndex = galleryView?.getPagePairStart(mCurrentIndex) ?: mCurrentIndex
        adapter.updateCurrentIndex(alignedIndex)
        if (!mReaderSidebarVisible || !forceCenter || mSidebarUserScrolling) {
            return
        }
        val targetPosition = adapter.getCurrentAdapterPosition()
        if (targetPosition < 0) {
            return
        }
        val recyclerView = mReaderSidebarRecyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return

        // If the target page is already visible, don't auto-scroll — respect user's scroll position
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION &&
            targetPosition in firstVisible..lastVisible
        ) {
            return
        }

        // RecyclerView hasn't laid out yet — jump directly without animation
        if (firstVisible == RecyclerView.NO_POSITION) {
            layoutManager.scrollToPosition(targetPosition)
            return
        }

        // Debounce: cancel pending scroll
        mSidebarScrollRunnable?.let { mSidebarScrollHandler.removeCallbacks(it) }

        val scrollRunnable = Runnable {
            mSidebarProgrammaticScroll = true
            val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
                override fun getVerticalSnapPreference() = SNAP_TO_START
                override fun calculateDtToFit(
                    viewStart: Int,
                    viewEnd: Int,
                    boxStart: Int,
                    boxEnd: Int,
                    snapPreference: Int,
                ): Int {
                    val viewMid = (viewStart + viewEnd) / 2
                    val boxMid = (boxStart + boxEnd) / 2
                    return boxMid - viewMid
                }
            }
            smoothScroller.targetPosition = targetPosition
            layoutManager.startSmoothScroll(smoothScroller)
        }

        mSidebarScrollRunnable = scrollRunnable
        mSidebarScrollHandler.postDelayed(scrollRunnable, SIDEBAR_SCROLL_DEBOUNCE_MS)
    }

    private fun loadReaderPreviewMetadata() {
        if (mAction != ACTION_EH) {
            return
        }
        val galleryInfo = mGalleryInfo ?: return
        val token = galleryInfo.token ?: return
        if (mReaderPreviewCacheLoaded) {
            return
        }
        // Capture on UI thread to avoid reading mutable fields from IO
        val snapshotCurrentIndex = mCurrentIndex
        val snapshotSize = mSize
        mReaderSidebarPreviewJob?.cancel()
        mReaderSidebarPreviewJob = lifecycleScope.launchIO {
            runCatching {
                val first = EhEngine.getPreviewSet(EhUrl.getGalleryDetailUrl(galleryInfo.gid, token, 0, false))
                val totalPreviewPages = first.second
                val previewPerPage = first.first.size()
                withUIContext {
                    val firstChanged = mergeReaderPreviewSet(first.first)
                    if (firstChanged) {
                        applySpreadPages()
                    }
                    updateReaderSidebarData()
                }
                // Load pages near current index first, then expand outward
                val currentPage = snapshotCurrentIndex.coerceIn(0, (snapshotSize - 1).coerceAtLeast(0))
                val currentPagePreviewPage = if (previewPerPage > 0) {
                    (currentPage / previewPerPage).coerceIn(0, totalPreviewPages - 1)
                } else {
                    0
                }
                val priorityPages = mutableListOf<Int>()
                for (offset in 1 until totalPreviewPages) {
                    val before = currentPagePreviewPage - offset
                    val after = currentPagePreviewPage + offset
                    if (before >= 1) priorityPages.add(before)
                    if (after < totalPreviewPages) priorityPages.add(after)
                    if (before < 1 && after >= totalPreviewPages) break
                }
                for (page in 1 until totalPreviewPages) {
                    if (page !in priorityPages) priorityPages.add(page)
                }
                for (page in priorityPages) {
                    currentCoroutineContext().ensureActive()
                    val result = EhEngine.getPreviewSet(EhUrl.getGalleryDetailUrl(galleryInfo.gid, token, page, false))
                    withUIContext {
                        val changed = mergeReaderPreviewSet(result.first)
                        if (changed) {
                            applySpreadPages()
                        }
                        updateReaderSidebarData()
                    }
                }
                saveReaderPreviewCache(galleryInfo.gid, token, mReaderSidebarPreviewMap)
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    private fun mergeReaderPreviewSet(previewSet: com.hippo.ehviewer.client.data.PreviewSet): Boolean {
        val galleryInfo = mGalleryInfo ?: return false
        var spreadChanged = false
        for (i in 0 until previewSet.size()) {
            val preview = previewSet.getGalleryPreview(galleryInfo.gid, i)
            mReaderSidebarPreviewMap[preview.position] = preview
            if (updateSpreadPage(preview)) {
                spreadChanged = true
            }
        }
        return spreadChanged
    }

    private fun updateSpreadPage(preview: GalleryPreview): Boolean {
        if (!preview.hasPreviewAspect() || preview.position < 0) {
            return false
        }
        val isSpread = preview.previewWidth > preview.previewHeight
        val oldValue = mSpreadPages.get(preview.position)
        if (oldValue == isSpread) {
            return false
        }
        mSpreadPages.set(preview.position, isSpread)
        return true
    }

    private fun applySpreadPages() {
        mGalleryView?.setSpreadPages(mSpreadPages)
    }

    private fun getReaderSidebarWidth(): Int {
        val ratio = READER_SIDEBAR_WIDTH_RATIOS[Settings.layoutReaderThumbnailSidebarWidth]
        val baseWidth = getReaderSidebarAvailableWidth()
        val ratioWidth = (baseWidth * ratio).toInt().coerceAtLeast(0)
        val minWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            READER_SIDEBAR_MIN_WIDTH_DP.toFloat(),
            resources.displayMetrics,
        ).toInt()
        return ratioWidth.coerceAtLeast(minWidthPx)
    }

    private fun getReaderSidebarAvailableWidth(): Int {
        val rootWidth = findViewById<View>(R.id.main)?.width ?: 0
        if (rootWidth > 0) {
            return rootWidth
        }
        val parentWidth = (mReaderContentContainer?.parent as? View)?.width ?: 0
        if (parentWidth > 0) {
            return parentWidth
        }
        val windowWidth = window.decorView.width
        if (windowWidth > 0) {
            return windowWidth
        }
        return resources.displayMetrics.widthPixels
    }

    private fun toggleReaderSidebar() {
        if (mReaderSidebarRecyclerView == null) {
            return
        }
        mReaderSidebarVisible = !mReaderSidebarVisible
        Settings.putLayoutReaderThumbnailSidebarVisible(mReaderSidebarVisible)
        updateReaderSidebarVisibility(showHiddenIndicator = !mReaderSidebarVisible)
    }

    private fun onReaderSidebarToggleTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mReaderSidebarToggleDownRawX = event.rawX
                mReaderSidebarToggleDragging = false
                view.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val rawDeltaX = event.rawX - mReaderSidebarToggleDownRawX
                if (!mReaderSidebarToggleDragging && kotlin.math.abs(rawDeltaX) > mReaderSidebarToggleTouchSlop) {
                    mReaderSidebarToggleDragging = true
                }
                if (mReaderSidebarToggleDragging) {
                    val sidebarContainer = mReaderSidebarContainer
                    val sidebarWidth = getReaderSidebarWidth()
                    view.translationX = rawDeltaX
                    if (mReaderSidebarVisible) {
                        // Drag toward screen edge = slide sidebar to close; else = just switch
                        val closeTranslation = if (mReaderSidebarOnRight) {
                            rawDeltaX.coerceIn(0f, sidebarWidth.toFloat())
                        } else {
                            rawDeltaX.coerceIn(-sidebarWidth.toFloat(), 0f)
                        }
                        sidebarContainer?.translationX = closeTranslation
                    } else {
                        // Drag away from screen edge = reveal sidebar
                        val isOpenDirection = if (mReaderSidebarOnRight) rawDeltaX < 0 else rawDeltaX > 0
                        if (isOpenDirection) {
                            if (sidebarContainer?.isVisible == false) {
                                sidebarContainer.isVisible = true
                                mReaderSidebarDivider?.isVisible = true
                            }
                            val openTranslation = if (mReaderSidebarOnRight) {
                                (sidebarWidth.toFloat() + rawDeltaX).coerceAtLeast(0f)
                            } else {
                                (-sidebarWidth.toFloat() + rawDeltaX).coerceAtMost(0f)
                            }
                            sidebarContainer?.translationX = openTranslation
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val deltaX = event.rawX - mReaderSidebarToggleDownRawX
                val sidebarContainer = mReaderSidebarContainer
                view.parent?.requestDisallowInterceptTouchEvent(false)
                if (mReaderSidebarToggleDragging) {
                    val threshold = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 36f, resources.displayMetrics,
                    )
                    if (mReaderSidebarVisible) {
                        val isCloseDirection = if (mReaderSidebarOnRight) deltaX > 0 else deltaX < 0
                        if (isCloseDirection && kotlin.math.abs(deltaX) > threshold) {
                            // Commit close — sidebar follows animation from current position
                            view.translationX = 0f
                            toggleReaderSidebar()
                        } else {
                            // Bounce sidebar back to visible
                            sidebarContainer?.animate()?.translationX(0f)?.setDuration(160L)?.start()
                            // Try side-switch; if no switch, bounce toggle back
                            view.translationX = 0f
                            if (!maybeSwitchReaderSidebarSide(deltaX)) {
                                view.animate().translationX(0f).setDuration(160L).start()
                            }
                        }
                    } else {
                        val isOpenDirection = if (mReaderSidebarOnRight) deltaX < 0 else deltaX > 0
                        if (isOpenDirection && kotlin.math.abs(deltaX) > threshold) {
                            // Commit open — sidebar animation picks up from current position
                            view.translationX = 0f
                            toggleReaderSidebar()
                        } else {
                            // Bounce sidebar back to hidden
                            val slideOffset = if (mReaderSidebarOnRight) {
                                getReaderSidebarWidth().toFloat()
                            } else {
                                -getReaderSidebarWidth().toFloat()
                            }
                            sidebarContainer?.animate()?.translationX(slideOffset)?.withEndAction {
                                sidebarContainer?.isVisible = false
                                mReaderSidebarDivider?.isVisible = false
                            }?.setDuration(160L)?.start()
                            view.animate().translationX(0f).setDuration(160L).start()
                        }
                    }
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    view.performClick()
                }
                mReaderSidebarToggleDragging = false
                return true
            }
        }
        return false
    }

    private fun maybeSwitchReaderSidebarSide(deltaX: Float): Boolean {
        val threshold = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            36f,
            resources.displayMetrics,
        )
        val targetOnRight = when {
            mReaderSidebarOnRight && deltaX <= -threshold -> false
            !mReaderSidebarOnRight && deltaX >= threshold -> true
            else -> mReaderSidebarOnRight
        }
        if (targetOnRight == mReaderSidebarOnRight) {
            return false
        }
        mReaderSidebarOnRight = targetOnRight
        Settings.putLayoutReaderThumbnailSidebarOnRight(targetOnRight)
        updateReaderSidebarVisibility(showHiddenIndicator = !mReaderSidebarVisible)
        return true
    }

    private fun updateReaderSidebarVisibility(showHiddenIndicator: Boolean = false) {
        val contentContainer = mReaderContentContainer ?: return
        val sidebarContainer = mReaderSidebarContainer ?: return
        val toggleView = mReaderSidebarToggle ?: return
        val contentLayoutParams = contentContainer.layoutParams as? FrameLayout.LayoutParams ?: return
        val dividerWidth = mReaderSidebarDivider?.layoutParams?.width ?: 0
        val sidebarWidth = getReaderSidebarWidth()

        // Cancel any running sidebar animation (stale onAnimationEnd is guarded by generation)
        mSidebarAnimator?.cancel()
        mSidebarAnimator = null
        val generation = ++mSidebarAnimGeneration

        // Update sidebar & divider layout params (size, gravity)
        sidebarContainer.layoutParams = (sidebarContainer.layoutParams as? FrameLayout.LayoutParams)?.apply {
            width = sidebarWidth
            gravity = if (mReaderSidebarOnRight) Gravity.END else Gravity.START
        }
        mReaderSidebarDivider?.layoutParams = (mReaderSidebarDivider?.layoutParams as? FrameLayout.LayoutParams)?.apply {
            gravity = if (mReaderSidebarOnRight) Gravity.END else Gravity.START
            marginEnd = if (mReaderSidebarOnRight) sidebarWidth else 0
            marginStart = if (mReaderSidebarOnRight) 0 else sidebarWidth
        }

        // Toggle positioning (instant)
        val toggleLayoutParams = toggleView.layoutParams as? FrameLayout.LayoutParams ?: return
        toggleLayoutParams.gravity = (if (mReaderSidebarOnRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        val insetMargin = if (mReaderSidebarVisible) sidebarWidth + dividerWidth else 0
        val targetToggleStartMargin = if (mReaderSidebarOnRight) 0 else insetMargin
        val targetToggleEndMargin = if (mReaderSidebarOnRight) insetMargin else 0
        if (toggleLayoutParams.marginStart != targetToggleStartMargin || toggleLayoutParams.marginEnd != targetToggleEndMargin) {
            toggleLayoutParams.marginStart = targetToggleStartMargin
            toggleLayoutParams.marginEnd = targetToggleEndMargin
            toggleView.layoutParams = toggleLayoutParams
        }
        toggleView.animate().cancel()
        toggleView.translationX = 0f
        toggleView.removeCallbacks(mHideReaderSidebarToggleRunnable)
        toggleView.scaleX = if (mReaderSidebarOnRight) 1f else -1f
        if (mReaderSidebarVisible) {
            toggleView.alpha = READER_SIDEBAR_TOGGLE_VISIBLE_ALPHA
        } else {
            toggleView.alpha = if (showHiddenIndicator) READER_SIDEBAR_TOGGLE_VISIBLE_ALPHA else getReaderSidebarToggleRestAlpha()
            if (showHiddenIndicator) {
                toggleView.postDelayed(mHideReaderSidebarToggleRunnable, READER_SIDEBAR_TOGGLE_HINT_DELAY)
            }
        }
        toggleView.bringToFront()

        // Animate sidebar slide + content margin + divider fade
        // Read current animated state so interrupted animations resume seamlessly
        val targetInset = if (mReaderSidebarVisible) sidebarWidth + dividerWidth else 0
        val targetStartMargin = if (mReaderSidebarOnRight) 0 else targetInset
        val targetEndMargin = if (mReaderSidebarOnRight) targetInset else 0
        val startStartMargin = contentLayoutParams.marginStart
        val startEndMargin = contentLayoutParams.marginEnd
        val slideOffset = if (mReaderSidebarOnRight) sidebarWidth.toFloat() else -sidebarWidth.toFloat()

        // Capture current animated values — when interrupted mid-animation, resume from here
        val curTranslation = sidebarContainer.translationX
        val curDividerAlpha = mReaderSidebarDivider?.alpha ?: 1f
        val startTranslation: Float
        val endTranslation: Float
        val startAlpha: Float
        val endAlpha: Float

        if (mReaderSidebarVisible) {
            sidebarContainer.isVisible = true
            mReaderSidebarDivider?.isVisible = true
            // First show: start from off-screen; interrupted hide: reverse from current pos
            startTranslation = if (curTranslation == 0f) slideOffset else curTranslation
            endTranslation = 0f
            startAlpha = if (curDividerAlpha >= 1f) 0f else curDividerAlpha
            endAlpha = 1f
        } else {
            // Interrupted show: reverse from current pos; first hide: start from resting pos
            startTranslation = curTranslation
            endTranslation = slideOffset
            startAlpha = curDividerAlpha
            endAlpha = 0f
        }

        var cancelled = false
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SIDEBAR_ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                // Content margins
                val newStartMargin = startStartMargin + ((targetStartMargin - startStartMargin) * fraction).toInt()
                val newEndMargin = startEndMargin + ((targetEndMargin - startEndMargin) * fraction).toInt()
                if (contentLayoutParams.marginStart != newStartMargin || contentLayoutParams.marginEnd != newEndMargin) {
                    contentLayoutParams.marginStart = newStartMargin
                    contentLayoutParams.marginEnd = newEndMargin
                    contentContainer.layoutParams = contentLayoutParams
                }
                // Sidebar slide
                sidebarContainer.translationX = startTranslation + (endTranslation - startTranslation) * fraction
                // Divider fade
                mReaderSidebarDivider?.alpha = startAlpha + (endAlpha - startAlpha) * fraction
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    // Mark as cancelled — don't modify any view state here.
                    // The view stays at its last animated position so the next
                    // animation can resume seamlessly from there.
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    // Skip if superseded by a newer animation
                    if (generation != mSidebarAnimGeneration) return
                    // Skip if cancelled — leave view state as-is for the next animator
                    if (cancelled) {
                        mSidebarAnimator = null
                        return
                    }
                    if (mReaderSidebarVisible) {
                        sidebarContainer.translationX = 0f
                        mReaderSidebarDivider?.alpha = 1f
                        contentContainer.post {
                            mGLRootView?.requestLayout()
                            mGLRootView?.requestLayoutContentPane()
                            mGalleryView?.requestLayout()
                            restoreVisibleReaderSidebarPreviews()
                            updateReaderSidebarSelection(forceCenter = true)
                        }
                    } else {
                        sidebarContainer.isVisible = false
                        mReaderSidebarDivider?.isVisible = false
                        sidebarContainer.translationX = 0f
                        mReaderSidebarDivider?.alpha = 1f
                        contentContainer.post {
                            mGLRootView?.requestLayout()
                            mGLRootView?.requestLayoutContentPane()
                            mGalleryView?.requestLayout()
                        }
                    }
                    mSidebarAnimator = null
                }
            })
        }
        mSidebarAnimator = animator
        animator.start()
    }

    @SuppressLint("SetTextI18n")
    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        val alignedProgress = mGalleryView?.getPagePairStart(progress) ?: progress
        val start = if (mLayoutMode == GalleryView.LAYOUT_RIGHT_TO_LEFT) {
            mRightText
        } else {
            mLeftText
        }
        if (fromUser && null != start) {
            start.text = getPageRange(alignedProgress)
        }
        if (fromUser && null != mGalleryView) {
            mGalleryView!!.setCurrentPage(alignedProgress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        SimpleHandler.getInstance().removeCallbacks(mHideSliderRunnable)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        SimpleHandler.getInstance().postDelayed(mHideSliderRunnable, HIDE_SLIDER_DELAY)
    }

    override fun onUpdateCurrentIndex(index: Int) {
        mGalleryProvider?.putStartPage(index)
        val task = mNotifyTaskPool.pop() ?: NotifyTask()
        task.setData(NOTIFY_KEY_CURRENT_INDEX, index)
        SimpleHandler.getInstance().post(task)
    }

    override fun onTapSliderArea() {
        val task = mNotifyTaskPool.pop() ?: NotifyTask()
        task.setData(NOTIFY_KEY_TAP_SLIDER_AREA, 0)
        SimpleHandler.getInstance().post(task)
    }

    override fun onTapMenuArea() {
        val task = mNotifyTaskPool.pop() ?: NotifyTask()
        task.setData(NOTIFY_KEY_TAP_MENU_AREA, 0)
        SimpleHandler.getInstance().post(task)
    }

    override fun onTapErrorText(index: Int) {
        val task = mNotifyTaskPool.pop() ?: NotifyTask()
        task.setData(NOTIFY_KEY_TAP_ERROR_TEXT, index)
        SimpleHandler.getInstance().post(task)
    }

    override fun onLongPressPage(index: Int) {
        val task = mNotifyTaskPool.pop() ?: NotifyTask()
        task.setData(NOTIFY_KEY_LONG_PRESS_PAGE, index)
        SimpleHandler.getInstance().post(task)
    }

    private fun showSlider(sliderPanel: View) {
        if (null != mSeekBarPanelAnimator) {
            mSeekBarPanelAnimator!!.cancel()
            mSeekBarPanelAnimator = null
        }
        sliderPanel.translationY = sliderPanel.height.toFloat()
        sliderPanel.visibility = View.VISIBLE
        mSeekBarPanelAnimator = ObjectAnimator.ofFloat(sliderPanel, "translationY", 0.0f)
        mSeekBarPanelAnimator!!.duration = SLIDER_ANIMATION_DURING
        mSeekBarPanelAnimator!!.interpolator = AnimationUtils.FAST_SLOW_INTERPOLATOR
        mSeekBarPanelAnimator!!.addUpdateListener(mUpdateSliderListener)
        mSeekBarPanelAnimator!!.addListener(mShowSliderListener)
        mSeekBarPanelAnimator!!.start()
        if (Settings.readingFullscreen) insetsController?.show(WindowInsetsCompat.Type.systemBars())
    }

    private fun hideSlider(sliderPanel: View) {
        if (null != mSeekBarPanelAnimator) {
            mSeekBarPanelAnimator!!.cancel()
            mSeekBarPanelAnimator = null
        }
        mSeekBarPanelAnimator =
            ObjectAnimator.ofFloat(sliderPanel, "translationY", sliderPanel.height.toFloat())
        mSeekBarPanelAnimator!!.duration = SLIDER_ANIMATION_DURING
        mSeekBarPanelAnimator!!.interpolator = AnimationUtils.SLOW_FAST_INTERPOLATOR
        mSeekBarPanelAnimator!!.addUpdateListener(mUpdateSliderListener)
        mSeekBarPanelAnimator!!.addListener(mHideSliderListener)
        mSeekBarPanelAnimator!!.start()
        if (Settings.readingFullscreen) insetsController?.hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * @param lightness 0 - 200
     */
    private fun setScreenLightness(enable: Boolean, lightness: Int) {
        var mLightness = lightness
        if (null == mMaskView) {
            return
        }
        val w = window
        val lp = w.attributes
        if (enable) {
            mLightness = MathUtils.clamp(mLightness, 0, 200)
            if (mLightness > 100) {
                mMaskView!!.setColor(0)
                // Avoid BRIGHTNESS_OVERRIDE_OFF,
                // screen may be off when lp.screenBrightness is 0.0f
                lp.screenBrightness = ((mLightness - 100) / 100.0f).coerceAtLeast(0.01f)
            } else {
                mMaskView!!.setColor(MathUtils.lerp(0xde, 0x00, mLightness / 100.0f) shl 24)
                lp.screenBrightness = 0.01f
            }
        } else {
            mMaskView!!.setColor(0)
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        w.attributes = lp
    }

    private fun shareImage(page: Int) {
        if (null == mGalleryProvider) {
            return
        }
        val dir = AppConfig.getExternalTempDir()
        if (null == dir) {
            Toast.makeText(this, R.string.error_cant_create_temp_file, Toast.LENGTH_SHORT).show()
            return
        }
        val file = mGalleryProvider!!.save(
            page,
            UniFile.fromFile(dir)!!,
            mGalleryProvider!!.getImageFilename(page),
        )
        if (file == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        }
        val filename = file.name
        if (filename == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        }
        var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            MimeTypeMap.getFileExtensionFromUrl(filename),
        )
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = "image/jpeg"
        }
        val uri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            File(dir, filename),
        )
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        if (mGalleryInfo != null) {
            intent.putExtra(
                Intent.EXTRA_TEXT,
                EhUrl.getGalleryDetailUrl(mGalleryInfo!!.gid, mGalleryInfo!!.token),
            )
        }
        intent.setDataAndType(uri, mimeType)
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share_image)))
        } catch (e: Throwable) {
            ExceptionUtils.throwIfFatal(e)
            Toast.makeText(this, R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyImage(page: Int) {
        if (null == mGalleryProvider) {
            return
        }
        val dir = AppConfig.getExternalCopyTempDir()
        if (null == dir) {
            Toast.makeText(this, R.string.error_cant_create_temp_file, Toast.LENGTH_SHORT).show()
            return
        }
        val file = mGalleryProvider!!.save(
            page,
            UniFile.fromFile(dir)!!,
            mGalleryProvider!!.getImageFilename(page),
        )
        if (file == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        }
        val filename = file.name
        if (filename == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            File(dir, filename),
        )
        val clipboardManager = getSystemService(ClipboardManager::class.java)
        if (clipboardManager != null) {
            val clipData = ClipData.newUri(contentResolver, "ehviewer", uri)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImage(page: Int) {
        if (null == mGalleryProvider) {
            return
        }
        if (!isAtLeastQ &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            mSavingPage = page
            requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        val filename = mGalleryProvider!!.getImageFilenameWithExtension(page)
        var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            MimeTypeMap.getFileExtensionFromUrl(filename),
        )
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = "image/jpeg"
        }
        val realPath: String
        val resolver = contentResolver
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        if (isAtLeastQ) {
            values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + AppConfig.APP_DIRNAME,
            )
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            realPath = Environment.DIRECTORY_PICTURES + File.separator + AppConfig.APP_DIRNAME
        } else {
            val path = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                AppConfig.APP_DIRNAME,
            )
            realPath = path.toString()
            if (!FileUtils.ensureDirectory(path)) {
                Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
                return
            }
            values.put(MediaStore.MediaColumns.DATA, path.toString() + File.separator + filename)
        }
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (imageUri == null) {
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        }
        if (!mGalleryProvider!!.save(page, UniFile.fromMediaUri(this, imageUri))) {
            try {
                resolver.delete(imageUri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Toast.makeText(this, R.string.error_cant_save_image, Toast.LENGTH_SHORT).show()
            return
        } else if (isAtLeastQ) {
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)
        }
        Toast.makeText(
            this,
            getString(R.string.image_saved, realPath + File.separator + filename),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun saveImageTo(page: Int, original: Boolean = false) {
        lifecycleScope.launchIO {
            if (null == mGalleryProvider) {
                return@launchIO
            }
            val dir = AppConfig.getExternalTempDir()
            if (null == dir) {
                withUIContext {
                    Toast.makeText(
                        this@GalleryActivity,
                        R.string.error_cant_create_temp_file,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                return@launchIO
            }
            val file = if (original) {
                withUIContext {
                    Toast.makeText(
                        this@GalleryActivity,
                        R.string.start_download_original,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                mGalleryProvider!!.downloadOriginal(
                    page,
                    UniFile.fromFile(dir)!!,
                    mGalleryProvider!!.getImageFilename(page),
                )
            } else {
                mGalleryProvider!!.save(
                    page,
                    UniFile.fromFile(dir)!!,
                    mGalleryProvider!!.getImageFilename(page),
                )
            }
            if (file == null) {
                withUIContext {
                    Toast.makeText(
                        this@GalleryActivity,
                        R.string.error_cant_save_image,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                return@launchIO
            }
            val filename = file.name
            if (filename == null) {
                withUIContext {
                    Toast.makeText(
                        this@GalleryActivity,
                        R.string.error_cant_save_image,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                return@launchIO
            }
            mCacheFileName = filename
            try {
                saveImageToLauncher.launch(filename)
            } catch (e: Throwable) {
                ExceptionUtils.throwIfFatal(e)
                withUIContext {
                    Toast.makeText(
                        this@GalleryActivity,
                        R.string.error_cant_find_activity,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun showPageDialog(page: Int) {
        val resources = this@GalleryActivity.resources
        val builder = AlertDialog.Builder(this@GalleryActivity)
        builder.setTitle(resources.getString(R.string.page_menu_title, page + 1))
        val items = arrayListOf<CharSequence>(
            getString(R.string.page_menu_refresh),
            getString(R.string.page_menu_share),
            getString(android.R.string.copy),
            getString(R.string.page_menu_save),
            getString(R.string.page_menu_save_to),
        )
        if (ACTION_EH == mAction && !Settings.getDownloadOriginImage(false)) {
            items.add(getString(R.string.page_menu_download_original))
        }
        pageDialogListener(builder, items.toTypedArray(), page)
        builder.create().applyAmoledBlack()
    }

    private fun pageDialogListener(
        builder: AlertDialog.Builder,
        items: Array<CharSequence>,
        page: Int,
    ) {
        builder.setItems(items) { _: DialogInterface?, which: Int ->
            if (mGalleryProvider == null) {
                return@setItems
            }
            when (which) {
                0 -> {
                    mGalleryProvider!!.removeCache(page)
                    mGalleryProvider!!.forceRequest(page)
                }

                1 -> shareImage(page)

                2 -> copyImage(page)

                3 -> saveImage(page)

                4 -> saveImageTo(page)

                5 -> saveImageTo(page, true)
            }
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        galleryDetailUrl?.let {
            outContent.webUri = it.toUri()
        }
    }

    @SuppressLint("InflateParams")
    private inner class GalleryMenuHelper(context: Context?) : DialogInterface.OnClickListener {
        val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_gallery_menu, null)
        private val mScreenRotation: Spinner = view.findViewById(R.id.screen_rotation)
        private val mReadingDirection: Spinner = view.findViewById(R.id.reading_direction)
        private val mDoublePageMode: SwitchCompat = view.findViewById(R.id.double_page_mode)
        private val mScaleMode: Spinner = view.findViewById(R.id.page_scaling)
        private val mStartPosition: Spinner = view.findViewById(R.id.start_position)
        private val mReadTheme: Spinner = view.findViewById(R.id.read_theme)
        private val mKeepScreenOn: SwitchCompat = view.findViewById(R.id.keep_screen_on)
        private val mShowClock: SwitchCompat = view.findViewById(R.id.show_clock)
        private val mShowProgress: SwitchCompat = view.findViewById(R.id.show_progress)
        private val mShowBattery: SwitchCompat = view.findViewById(R.id.show_battery)
        private val mShowPageInterval: SwitchCompat = view.findViewById(R.id.show_page_interval)
        private val mTurnPageInterval: SeekBar = view.findViewById(R.id.turn_page_interval)
        private val mVolumePage: SwitchCompat = view.findViewById(R.id.volume_page)
        private val mVolumePageInterval: SeekBar = view.findViewById(R.id.volume_page_interval)
        private val mReverseVolumePage: SwitchCompat = view.findViewById(R.id.reverse_volume_page)
        private val mReadingFullscreen: SwitchCompat = view.findViewById(R.id.reading_fullscreen)
        private val mCustomScreenLightness: SwitchCompat = view.findViewById(R.id.custom_screen_lightness)
        private val mScreenLightness: SeekBar = view.findViewById(R.id.screen_lightness)
        private val mDoublePageModeLandscape: SwitchCompat = view.findViewById(R.id.double_page_mode_landscape)
        private val mDoublePageOffset: SwitchCompat = view.findViewById(R.id.double_page_offset)
        private val mDoublePageGapLayout: ViewGroup = view.findViewById(R.id.double_page_gap_layout)
        private val mDoublePageGap: SeekBar = view.findViewById(R.id.double_page_gap)

        init {
            mScreenRotation.setSelection(Settings.screenRotation)
            mReadingDirection.setSelection(Settings.readingDirection)
            mDoublePageMode.isChecked = Settings.doublePageMode
            mDoublePageModeLandscape.isChecked = Settings.doublePageModeLandscape
            mDoublePageOffset.isChecked = Settings.doublePageOffset
            mDoublePageGap.progress = Settings.getInt(Settings.KEY_DOUBLE_PAGE_GAP, 0)
            mDoublePageModeLandscape.visibility = if (Settings.doublePageMode) View.VISIBLE else View.GONE
            mDoublePageOffset.visibility = if (Settings.doublePageMode) View.VISIBLE else View.GONE
            mDoublePageGapLayout.visibility = if (Settings.doublePageMode) View.VISIBLE else View.GONE
            mDoublePageMode.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                mDoublePageModeLandscape.visibility = if (isChecked) View.VISIBLE else View.GONE
                mDoublePageOffset.visibility = if (isChecked) View.VISIBLE else View.GONE
                mDoublePageGapLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
            mScaleMode.setSelection(Settings.pageScaling)
            mStartPosition.setSelection(Settings.startPosition)
            mReadTheme.setSelection(Settings.readTheme)
            mKeepScreenOn.isChecked = Settings.keepScreenOn
            mShowClock.isChecked = Settings.showClock
            mShowProgress.isChecked = Settings.showProgress
            mShowBattery.isChecked = Settings.showBattery
            mShowPageInterval.isChecked = Settings.showPageInterval
            mTurnPageInterval.progress = Settings.turnPageInterval - 1
            mVolumePage.isChecked = Settings.volumePage
            mVolumePage.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                (mVolumePageInterval.parent as? ViewGroup)?.visibility = if (isChecked) View.VISIBLE else View.GONE
                mReverseVolumePage.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
            (mVolumePageInterval.parent as? ViewGroup)?.visibility = if (Settings.volumePage) View.VISIBLE else View.GONE
            mVolumePageInterval.progress = Settings.volumePageInterval
            mReverseVolumePage.visibility = if (Settings.volumePage) View.VISIBLE else View.GONE
            mReverseVolumePage.isChecked = Settings.reverseVolumePage
            mReadingFullscreen.isChecked = Settings.readingFullscreen
            mCustomScreenLightness.isChecked = Settings.customScreenLightness
            mCustomScreenLightness.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                mScreenLightness.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
            mScreenLightness.progress = Settings.screenLightness
            mScreenLightness.visibility = if (Settings.customScreenLightness) View.VISIBLE else View.GONE
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            if (mGalleryView == null) {
                return
            }
            val screenRotation = mScreenRotation.selectedItemPosition
            val layoutMode = GalleryView.sanitizeLayoutMode(mReadingDirection.selectedItemPosition)
            val doublePageMode = mDoublePageMode.isChecked
            val doublePageModeLandscape = mDoublePageModeLandscape.isChecked
            val doublePageOffset = mDoublePageOffset.isChecked
            val doublePageGap = mDoublePageGap.progress
            val scaleMode = GalleryView.sanitizeScaleMode(mScaleMode.selectedItemPosition)
            val startPosition =
                GalleryView.sanitizeStartPosition(mStartPosition.selectedItemPosition)
            val readTheme = mReadTheme.selectedItemPosition
            val keepScreenOn = mKeepScreenOn.isChecked
            val showClock = mShowClock.isChecked
            val showProgress = mShowProgress.isChecked
            val showBattery = mShowBattery.isChecked
            val showPageInterval = mShowPageInterval.isChecked
            val turnPageInterval = mTurnPageInterval.progress + 1
            val volumePage = mVolumePage.isChecked
            val volumePageInterval = mVolumePageInterval.progress
            val reverseVolumePage = mReverseVolumePage.isChecked
            val readingFullscreen = mReadingFullscreen.isChecked
            val customScreenLightness = mCustomScreenLightness.isChecked
            val screenLightness = mScreenLightness.progress
            val oldReadingFullscreen = Settings.readingFullscreen
            val oldReadTheme = Settings.readTheme
            Settings.putScreenRotation(screenRotation)
            Settings.putReadingDirection(layoutMode)
            Settings.putDoublePageMode(doublePageMode)
            Settings.putDoublePageModeLandscape(doublePageModeLandscape)
            Settings.putDoublePageOffset(doublePageOffset)
            Settings.putDoublePageGap(doublePageGap)
            Settings.putPageScaling(scaleMode)
            Settings.putStartPosition(startPosition)
            Settings.putReadTheme(readTheme)
            Settings.putKeepScreenOn(keepScreenOn)
            Settings.putShowClock(showClock)
            Settings.putShowProgress(showProgress)
            Settings.putShowBattery(showBattery)
            Settings.putShowPageInterval(showPageInterval)
            Settings.putTurnPageInterval(turnPageInterval)
            Settings.putVolumePage(volumePage)
            Settings.putVolumePageInterval(volumePageInterval)
            Settings.putReverseVolumePage(reverseVolumePage)
            Settings.putReadingFullscreen(readingFullscreen)
            Settings.putCustomScreenLightness(customScreenLightness)
            Settings.putScreenLightness(screenLightness)
            requestedOrientation = when (screenRotation) {
                0 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                3 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            mGalleryView!!.layoutMode = layoutMode
            mGalleryView!!.setScaleMode(scaleMode)
            mGalleryView!!.setStartPosition(startPosition)
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            mClock?.visibility = if (showClock) View.VISIBLE else View.GONE
            mProgress?.visibility = if (showProgress) View.VISIBLE else View.GONE
            mBattery?.visibility = if (showBattery) View.VISIBLE else View.GONE
            mGalleryView!!.setPagerInterval(
                if (showPageInterval) {
                    resources.getDimensionPixelOffset(
                        R.dimen.gallery_pager_interval,
                    )
                } else {
                    0
                },
            )
            mGalleryView!!.setScrollInterval(
                if (showPageInterval) {
                    resources.getDimensionPixelOffset(
                        R.dimen.gallery_scroll_interval,
                    )
                } else {
                    0
                },
            )
            mTurnPageIntervalVal = turnPageInterval
            setScreenLightness(customScreenLightness, screenLightness)
            // Update slider
            mLayoutMode = layoutMode
            updateDoublePageMode()
            updateSlider()
            updateProgress()
            if (oldReadingFullscreen != readingFullscreen || oldReadTheme != readTheme) {
                recreate()
            }
        }
    }

    private inner class NotifyTask : Runnable {
        private var mKey = 0
        private var mValue = 0

        fun setData(key: Int, value: Int) {
            mKey = key
            mValue = value
        }

        private fun onTapMenuArea() {
            val builder = AlertDialog.Builder(this@GalleryActivity)
            val helper = GalleryMenuHelper(builder.context)
            builder.setTitle(R.string.gallery_menu_title)
                .setView(helper.view)
                .setPositiveButton(android.R.string.ok, helper)
                .setOnDismissListener {
                    if (Settings.readingFullscreen) {
                        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            builder.create().applyAmoledBlack()
        }

        private fun onTapSliderArea() {
            if (mSeekBarPanel == null || mSize <= 0 || mCurrentIndex < 0) {
                return
            }
            SimpleHandler.getInstance().removeCallbacks(mHideSliderRunnable)
            if (mSeekBarPanel!!.isVisible) {
                hideSlider(mSeekBarPanel!!)
            } else {
                showSlider(mSeekBarPanel!!)
                SimpleHandler.getInstance().postDelayed(mHideSliderRunnable, HIDE_SLIDER_DELAY)
            }
        }

        private fun onTapErrorText(index: Int) {
            if (mGalleryProvider != null) {
                mGalleryProvider!!.forceRequest(index)
            }
        }

        private fun onLongPressPage(index: Int) {
            showPageDialog(index)
        }

        override fun run() {
            when (mKey) {
                NOTIFY_KEY_LAYOUT_MODE -> {
                    mLayoutMode = mValue
                    updateSlider()
                    updateReaderSidebarData(forceCenter = true)
                }

                NOTIFY_KEY_SIZE -> {
                    mSize = mValue
                    updateSlider()
                    updateProgress()
                    updateReaderSidebarData(forceCenter = true)
                }

                NOTIFY_KEY_CURRENT_INDEX -> {
                    mCurrentIndex = mValue
                    updateSlider()
                    updateProgress()
                    refreshReaderSidebarStructure(forceCenter = true)
                }

                NOTIFY_KEY_TAP_MENU_AREA -> onTapMenuArea()

                NOTIFY_KEY_TAP_SLIDER_AREA -> onTapSliderArea()

                NOTIFY_KEY_TAP_ERROR_TEXT -> onTapErrorText(mValue)

                NOTIFY_KEY_LONG_PRESS_PAGE -> onLongPressPage(mValue)
            }
            mNotifyTaskPool.push(this)
        }
    }

    private class ReaderSidebarHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageLeadingSpace: View = itemView.findViewById(R.id.image_leading_space)
        val imageSlot: View = itemView.findViewById(R.id.image_slot)
        val image: ReaderSidebarThumb = itemView.findViewById(R.id.image)
        val imageLoading: ProgressBar = itemView.findViewById(R.id.image_loading)
        val imageSecondarySlot: View = itemView.findViewById(R.id.image_secondary_slot)
        val imageSecondary: ReaderSidebarThumb = itemView.findViewById(R.id.image_secondary)
        val imageSecondaryLoading: ProgressBar = itemView.findViewById(R.id.image_secondary_loading)
        val imageGap: View = itemView.findViewById(R.id.image_gap)
        val imageTrailingSpace: View = itemView.findViewById(R.id.image_trailing_space)
        val text: TextView = itemView.findViewById(R.id.text)
        val indicator: View = itemView.findViewById(R.id.indicator)
    }

    private fun bindSidebarPreview(
        view: ReaderSidebarThumb,
        preview: GalleryPreview?,
        loadingView: ProgressBar? = null,
    ) {
        if (preview != null) {
            // Always clear stale drawable first to prevent recycled ViewHolders
            // from briefly showing the previous page's sprite sheet during fast scroll.
            if (view.drawable != null) view.setImageDrawable(null)
            view.resetForReuse()
            view.visibility = View.VISIBLE
            view.setBackgroundResource(0)
            if (preview.hasClipAspect()) {
                view.applySidebarAspect(preview.clipWidth, preview.clipHeight)
            } else if (preview.hasPreviewAspect()) {
                view.applySidebarAspect(preview.previewWidth, preview.previewHeight)
            } else {
                view.resetSidebarAspect()
            }
            // Don't show spinner eagerly — let the loading listener handle it.
            // This avoids a flash when Coil delivers from memory cache in the same frame.
            loadingView?.visibility = View.GONE
            view.setOnLoadingStateChangeListener { isLoading ->
                loadingView?.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
            // Only load when clip data is valid. For NormalPreviewSet, multiple pages
            // share the same sprite sheet URL; clip coordinates select the correct
            // region. Loading without valid clip would display the full unclipped sheet.
            if (preview.hasClipAspect()) {
                preview.load(view)
            } else {
                // Clip data not yet available — show placeholder, don't load sprite sheet.
                view.resetClip()
                view.setImageDrawable(null)
            }
        } else {
            view.visibility = View.VISIBLE
            view.resetSidebarAspect()
            view.resetClip()
            view.setImageDrawable(null)
            view.setBackgroundResource(0)
            loadingView?.visibility = View.GONE
            view.setOnLoadingStateChangeListener(null)
        }
    }

    private fun restoreSidebarPreviewIfNeeded(view: ReaderSidebarThumb, preview: GalleryPreview?) {
        if (preview == null || view.drawable != null) {
            return
        }
        bindSidebarPreview(view, preview, null)
    }

    private fun restoreVisibleReaderSidebarPreviews() {
        val recyclerView = mReaderSidebarRecyclerView ?: return
        val pageStarts = mReaderSidebarPageStarts
        if (pageStarts.isEmpty()) {
            return
        }
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            val holder = recyclerView.getChildViewHolder(child) as? ReaderSidebarHolder ?: continue
            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION || position !in pageStarts.indices) {
                continue
            }
            val pageStart = pageStarts[position]
            restoreSidebarPreviewIfNeeded(holder.image, mReaderSidebarPreviewMap[pageStart])
            val pairSize = (mGalleryView?.getPagePairSize(pageStart) ?: 1).coerceAtLeast(1)
            if (pairSize > 1) {
                restoreSidebarPreviewIfNeeded(holder.imageSecondary, mReaderSidebarPreviewMap[pageStart + 1])
            }
        }
    }

    private fun getReaderSidebarToggleRestAlpha(): Float = if (Settings.layoutReaderThumbnailSidebarToggleAutoHide) {
        READER_SIDEBAR_TOGGLE_HIDDEN_ALPHA
    } else {
        READER_SIDEBAR_TOGGLE_REST_ALPHA
    }

    private inner class ReaderSidebarAdapter : RecyclerView.Adapter<ReaderSidebarHolder>() {
        private val inflater: LayoutInflater = layoutInflater
        private var pageCount = 0
        private var currentIndex = -1
        private var previews: Map<Int, GalleryPreview> = emptyMap()
        private var pageStarts: List<Int> = emptyList()
        private var pageStartToPosition: Map<Int, Int> = emptyMap()

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = pageStarts[position].toLong()

        private fun updateSlotLayout(view: View, width: Int, weight: Float) {
            val params = view.layoutParams as? LinearLayout.LayoutParams ?: return
            if (params.width == width && params.weight == weight) {
                return
            }
            params.width = width
            params.weight = weight
            view.layoutParams = params
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReaderSidebarHolder = ReaderSidebarHolder(
            inflater.inflate(R.layout.item_gallery_sidebar_preview, parent, false),
        )

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ReaderSidebarHolder, position: Int) {
            bindSidebarFull(holder, position)
        }

        override fun onViewRecycled(holder: ReaderSidebarHolder) {
            // Cancel in-flight Coil requests to prevent stale callbacks from
            // delivering images after the ViewHolder is rebound to a new position.
            holder.image.setImageDrawable(null)
            holder.imageSecondary.setImageDrawable(null)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ReaderSidebarHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                val hasPreview = payloads.contains(PREVIEW_PAYLOAD)
                val hasIndex = payloads.contains(INDEX_PAYLOAD)
                if (hasPreview) {
                    val pageStart = pageStarts[position]
                    val pairSize = (mGalleryView?.getPagePairSize(pageStart) ?: 1).coerceAtLeast(1)
                    val pageEnd = minOf(pageCount, pageStart + pairSize)
                    bindSidebarPreview(holder.image, previews[pageStart], holder.imageLoading)
                    if (pageEnd - pageStart > 1) {
                        bindSidebarPreview(holder.imageSecondary, previews[pageStart + 1], holder.imageSecondaryLoading)
                    }
                }
                if (hasIndex) {
                    val activated = position == currentIndex
                    holder.indicator.visibility = if (activated) View.VISIBLE else View.GONE
                    holder.text.setTypeface(null, if (activated) Typeface.BOLD else Typeface.NORMAL)
                }
            } else {
                bindSidebarFull(holder, position)
            }
        }

        @SuppressLint("SetTextI18n")
        private fun bindSidebarFull(holder: ReaderSidebarHolder, position: Int) {
            val pageStart = pageStarts[position]
            val pairSize = (mGalleryView?.getPagePairSize(pageStart) ?: 1).coerceAtLeast(1)
            val pageEnd = minOf(pageCount, pageStart + pairSize)
            bindSidebarPreview(holder.image, previews[pageStart], holder.imageLoading)
            holder.image.contentDescription = getString(R.string.reader_sidebar_page, pageStart + 1)
            if (pageEnd - pageStart > 1) {
                holder.imageLeadingSpace.visibility = View.GONE
                holder.imageTrailingSpace.visibility = View.GONE
                updateSlotLayout(holder.imageSlot, 0, 1f)
                updateSlotLayout(holder.imageSecondarySlot, 0, 1f)
                holder.imageSecondarySlot.visibility = View.VISIBLE
                holder.imageGap.visibility = View.VISIBLE
                bindSidebarPreview(holder.imageSecondary, previews[pageStart + 1], holder.imageSecondaryLoading)
                holder.imageSecondary.contentDescription = getString(R.string.reader_sidebar_page, pageStart + 2)
            } else {
                holder.imageSecondary.resetClip()
                holder.imageSecondary.setImageDrawable(null)
                holder.imageSecondary.setBackgroundResource(0)
                holder.imageSecondaryLoading.visibility = View.GONE
                if (isDoublePageMode) {
                    holder.imageLeadingSpace.visibility = View.VISIBLE
                    holder.imageTrailingSpace.visibility = View.VISIBLE
                    updateSlotLayout(holder.imageLeadingSpace, 0, 1f)
                    updateSlotLayout(holder.imageSlot, 0, 2f)
                    updateSlotLayout(holder.imageTrailingSpace, 0, 1f)
                    holder.imageSecondarySlot.visibility = View.GONE
                    holder.imageGap.visibility = View.GONE
                } else {
                    holder.imageLeadingSpace.visibility = View.GONE
                    holder.imageTrailingSpace.visibility = View.GONE
                    updateSlotLayout(holder.imageSlot, 0, 1f)
                    updateSlotLayout(holder.imageSecondarySlot, 0, 1f)
                    holder.imageSecondarySlot.visibility = View.GONE
                    holder.imageGap.visibility = View.GONE
                }
            }
            holder.text.text = if (pageEnd - pageStart > 1) {
                "${pageStart + 1}-$pageEnd"
            } else {
                (pageStart + 1).toString()
            }
            val activated = position == currentIndex
            holder.indicator.visibility = if (activated) View.VISIBLE else View.GONE
            holder.itemView.alpha = 1f
            holder.text.setTypeface(null, if (activated) Typeface.BOLD else Typeface.NORMAL)
            holder.itemView.setOnClickListener {
                mGalleryView?.setCurrentPage(pageStart)
            }
        }

        override fun getItemCount(): Int = pageStarts.size

        fun updateData(pageCount: Int, previews: Map<Int, GalleryPreview>, pageStarts: List<Int>) {
            val oldPageStarts = this.pageStarts
            this.pageCount = pageCount
            this.previews = previews
            if (oldPageStarts == pageStarts) {
                if (mSidebarUserScrolling) {
                    // User is scrolling — defer preview rebind to avoid visual jitter
                    mSidebarPendingPreviewUpdate = true
                } else {
                    notifyItemRangeChanged(0, pageStarts.size, PREVIEW_PAYLOAD)
                }
            } else if (mSidebarUserScrolling) {
                // User is scrolling — defer structural change
                mSidebarDataPending = true
                mSidebarPendingPageStarts = pageStarts
            } else {
                applyDiff(pageStarts)
            }
        }

        fun flushPendingData(newPageStarts: List<Int>?) {
            if (newPageStarts != null) {
                applyDiff(newPageStarts)
                // DiffUtil only rebinds structurally changed items. Items that
                // didn't change structure still have stale preview state from
                // before the scroll — notify them to refresh their thumbnails.
                notifyItemRangeChanged(0, pageStarts.size, PREVIEW_PAYLOAD)
            } else if (mSidebarPendingPreviewUpdate) {
                notifyItemRangeChanged(0, pageStarts.size, PREVIEW_PAYLOAD)
            }
            mSidebarPendingPreviewUpdate = false
        }

        private fun applyDiff(newPageStarts: List<Int>) {
            val oldPageStarts = this.pageStarts
            val oldCurrentIndex = this.currentIndex
            val oldPageCount = this.pageCount
            // Resolve current page's new position before updating state
            val currentAlignedIndex = mGalleryView?.getPagePairStart(mCurrentIndex) ?: mCurrentIndex
            this.pageStarts = newPageStarts
            this.pageStartToPosition = newPageStarts.withIndex().associate { (position, pageStart) -> pageStart to position }
            this.currentIndex = pageStartToPosition[currentAlignedIndex] ?: -1
            val newCurrentIndex = this.currentIndex
            val diff = DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun getOldListSize() = oldPageStarts.size
                    override fun getNewListSize() = newPageStarts.size
                    override fun areItemsTheSame(oldPos: Int, newPos: Int) = oldPageStarts[oldPos] == newPageStarts[newPos]
                    override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                        val wasActive = oldPos == oldCurrentIndex
                        val isActive = newPos == newCurrentIndex
                        if (wasActive != isActive) return false
                        // Detect pair-size change: derive from pageStarts structure
                        val oldStart = oldPageStarts[oldPos]
                        val newStart = newPageStarts[newPos]
                        val oldPairSize = if (oldPos + 1 < oldPageStarts.size) oldPageStarts[oldPos + 1] - oldStart else oldPageCount - oldStart
                        val newPairSize = if (newPos + 1 < newPageStarts.size) newPageStarts[newPos + 1] - newStart else pageCount - newStart
                        return oldPairSize == newPairSize
                    }
                },
                true,
            )
            diff.dispatchUpdatesTo(this)
        }

        fun updateCurrentIndex(index: Int) {
            val newIndex = pageStartToPosition[index] ?: -1
            if (currentIndex == newIndex) return
            val oldIndex = currentIndex
            currentIndex = newIndex
            if (oldIndex in pageStarts.indices) {
                notifyItemChanged(oldIndex, INDEX_PAYLOAD)
            }
            if (currentIndex in pageStarts.indices) {
                notifyItemChanged(currentIndex, INDEX_PAYLOAD)
            }
        }

        fun getCurrentAdapterPosition(): Int = currentIndex
    }

    private inner class GalleryAdapter(glRootView: GLRootView, provider: GalleryProvider) : SimpleAdapter(glRootView, provider) {
        override fun onDataChanged() {
            super.onDataChanged()
            if (mGalleryProvider != null) {
                val size = mGalleryProvider!!.size
                val task = mNotifyTaskPool.pop() ?: NotifyTask()
                task.setData(NOTIFY_KEY_SIZE, size)
                SimpleHandler.getInstance().post(task)
            }
        }
    }

    companion object {
        private const val PREVIEW_PAYLOAD = "preview"
        private const val INDEX_PAYLOAD = "index"
        private const val SIDEBAR_SCROLL_DEBOUNCE_MS = 80L
        const val ACTION_EH = "eh"
        const val KEY_ACTION = "action"
        const val KEY_FILENAME = "filename"
        const val KEY_URI = "uri"
        const val KEY_GALLERY_INFO = "gallery_info"
        const val KEY_INITIAL_READER_PREVIEWS = "initial_reader_previews"
        const val KEY_PAGE = "page"
        const val KEY_CURRENT_INDEX = "current_index"
        private const val SLIDER_ANIMATION_DURING: Long = 150
        private const val HIDE_SLIDER_DELAY: Long = 3000
        private const val READER_SIDEBAR_TOGGLE_HINT_DELAY: Long = 1500
        private const val READER_SIDEBAR_TOGGLE_FADE_DURATION: Long = 180
        private const val SIDEBAR_ANIMATION_DURATION: Long = 250
        private const val READER_SIDEBAR_TOGGLE_VISIBLE_ALPHA = 0.82f
        private const val READER_SIDEBAR_TOGGLE_REST_ALPHA = 0.28f
        private const val READER_SIDEBAR_TOGGLE_HIDDEN_ALPHA = 0f
        private const val NOTIFY_KEY_LAYOUT_MODE = 0
        private const val NOTIFY_KEY_SIZE = 1
        private const val NOTIFY_KEY_CURRENT_INDEX = 2
        private const val NOTIFY_KEY_TAP_SLIDER_AREA = 3
        private const val NOTIFY_KEY_TAP_MENU_AREA = 4
        private const val NOTIFY_KEY_TAP_ERROR_TEXT = 5
        private const val NOTIFY_KEY_LONG_PRESS_PAGE = 6
        private val READER_SIDEBAR_WIDTH_RATIOS = FloatArray(33) { (3 + it) / 100f }
        private const val READER_SIDEBAR_MIN_WIDTH_DP = 56
    }
}
