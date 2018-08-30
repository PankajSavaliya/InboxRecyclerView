package me.saket.expand

import android.graphics.Rect
import android.view.ViewTreeObserver
import me.saket.expand.page.ExpandablePageLayout

abstract class ItemExpandAnimator {
  abstract fun onAttachRecyclerView(recyclerView: InboxRecyclerView)
}

// TODO: Find a better name.
// TODO: Combine attach and detach into a single function.
class DefaultItemExpandAnimator : ItemExpandAnimator() {

  private val pagePreDrawListener = object : ViewTreeObserver.OnPreDrawListener {
    private var lastTranslationY = 0F
    private var lastClippedRect = Rect()
    private var lastState = ExpandablePageLayout.PageState.COLLAPSED

    override fun onPreDraw(): Boolean {
      val page = recyclerView.page
      if (lastTranslationY != page.translationY || lastClippedRect != page.clippedRect || lastState != page.currentState) {
        onPageMove()
      }

      lastTranslationY = page.translationY
      lastClippedRect = page.clippedRect
      lastState = page.currentState
      return true
    }
  }

  private val pageLayoutChangeListener = {
    // Changes in the page's dimensions will get handled here.
    onPageMove()
  }

  private lateinit var recyclerView: InboxRecyclerView

  override fun onAttachRecyclerView(recyclerView: InboxRecyclerView) {
    this.recyclerView = recyclerView
    recyclerView.page.viewTreeObserver.addOnGlobalLayoutListener(pageLayoutChangeListener)
    recyclerView.page.viewTreeObserver.addOnPreDrawListener(pagePreDrawListener)
  }

  /**
   * When the page is expanding, this animates all RecyclerView items out of the Window.
   * The expanding item is moved to the top, while the items above it are animated out
   * of the window towards the top and the rest towards the bottom.
   *
   * Vice versa when the page is collapsing.
   */
  private fun onPageMove() {
    val page = recyclerView.page
    if (page.isCollapsed) {
      // Reset everything. This is also useful when the content size
      // changes, say as a result of the soft-keyboard getting dismissed.
      recyclerView.apply {
        for (childIndex in 0 until childCount) {
          val childView = getChildAt(childIndex)
          childView.translationY = 0F
          childView.alpha = 1F
        }
      }
      return
    }

    val (anchorIndex) = recyclerView.expandedItem
    val anchorView = recyclerView.getChildAt(anchorIndex)

    val pageTop = page.translationY
    val pageBottom = page.translationY + page.clippedRect.height()

    val distanceExpandedTowardsTop = pageTop - anchorView.top
    val distanceExpandedTowardsBottom = pageBottom - anchorView.bottom

    // Move the RecyclerView rows with the page.
    recyclerView.apply {
      for (childIndex in 0 until childCount) {
        val childView = getChildAt(childIndex)

        if (anchorView == null) {
          // Anchor View can be null when the page was expanded from
          // an arbitrary location. See InboxRecyclerView#expandFromTop().
          childView.translationY = pageBottom

        } else {
          childView.translationY = when {
            childIndex <= anchorIndex -> distanceExpandedTowardsTop
            else -> distanceExpandedTowardsBottom
          }
        }
      }
    }

    // Fade in the anchor row with the expanding/collapsing page.
    val minPageHeight = anchorView.height
    val maxPageHeight = page.height
    val expandRatio = (page.clippedRect.height() - minPageHeight).toFloat() / (maxPageHeight - minPageHeight)
    anchorView.alpha = 1F - expandRatio
  }
}
