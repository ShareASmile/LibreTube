package com.github.libretube.ui.listeners

import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.updateLayoutParams
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TimeBar
import coil.request.ImageRequest
import com.github.libretube.api.obj.PreviewFrames
import com.github.libretube.databinding.ExoStyledPlayerControlViewBinding
import com.github.libretube.helpers.ImageHelper
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.util.BitmapUtil

@UnstableApi
class SeekbarPreviewListener(
    private val previewFrames: List<PreviewFrames>,
    private val playerBinding: ExoStyledPlayerControlViewBinding,
    private val duration: Long,
    private val onScrub: (position: Long) -> Unit,
    private val onScrubEnd: (position: Long) -> Unit
) : TimeBar.OnScrubListener {
    private var scrubInProgress = false

    override fun onScrubStart(timeBar: TimeBar, position: Long) {
        scrubInProgress = true

        processPreview(position)
    }

    /**
     * Show a preview of the scrubber position
     */
    override fun onScrubMove(timeBar: TimeBar, position: Long) {
        scrubInProgress = true

        playerBinding.seekbarPreviewPosition.text = DateUtils.formatElapsedTime(position / 1000)
        processPreview(position)

        runCatching {
            onScrub.invoke(position)
        }
    }

    /**
     * Hide the seekbar preview with a short delay
     */
    override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
        scrubInProgress = false
        // animate the disappearance of the preview image
        playerBinding.seekbarPreview.animate()
            .alpha(0f)
            .translationYBy(30f)
            .setDuration(200)
            .withEndAction {
                playerBinding.seekbarPreview.visibility = View.GONE
                playerBinding.seekbarPreview.translationY -= 30f
                playerBinding.seekbarPreview.alpha = 1f
            }
            .start()

        onScrubEnd.invoke(position)
    }

    /**
     * Make a request to get the image frame and update its position
     */
    private fun processPreview(position: Long) {
        val previewFrame = PlayerHelper.getPreviewFrame(previewFrames, position) ?: return

        // update the offset of the preview image view
        updatePreviewX(position)

        val request = ImageRequest.Builder(playerBinding.seekbarPreview.context)
            .data(previewFrame.previewUrl)
            .target {
                if (!scrubInProgress) return@target
                val frame = BitmapUtil.cutBitmapFromPreviewFrame(it.toBitmap(), previewFrame)
                playerBinding.seekbarPreviewImage.setImageBitmap(frame)
                playerBinding.seekbarPreview.visibility = View.VISIBLE
            }
            .build()

        ImageHelper.imageLoader.enqueue(request)
    }

    /**
     * Update the offset of the preview image to fit the current scrubber position
     */
    private fun updatePreviewX(position: Long) {
        playerBinding.seekbarPreview.updateLayoutParams<MarginLayoutParams> {
            val parentWidth = (playerBinding.seekbarPreview.parent as View).width
            // calculate the center-offset of the preview image view
            val offset = parentWidth * (position.toFloat() / duration.toFloat()) -
                playerBinding.seekbarPreview.width / 2
            // normalize the offset to keep a minimum distance at left and right
            val maxPadding = parentWidth - MIN_PADDING - playerBinding.seekbarPreview.width
            marginStart = offset.toInt().coerceIn(MIN_PADDING, maxPadding)
        }
    }

    companion object {
        /**
         * The minimum start and end padding for the seekbar preview
         */
        const val MIN_PADDING = 20
    }
}
