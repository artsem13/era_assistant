package com.era.assistant.core.ui
import android.view.View
import android.view.ViewGroup
class SearchStatusCardController(private val card: View, private val animationView: SearchPulseView, cancelView: View, private val viewportController: ConversationViewportController, private val onCancel: () -> Unit = {}) {
 var state: SearchStatusState = SearchStatusState.HIDDEN; private set
 init { cancelView.setOnClickListener { onCancel(); hide() }; card.visibility = View.GONE }
 fun showSearching() { state=SearchStatusState.SEARCHING; (card.parent as? ViewGroup)?.let { it.removeView(card); it.addView(card) }; card.visibility=View.VISIBLE; animationView.startAnimation(); viewportController.scrollToLatestMessage(force=true) }
 fun hide() { state=SearchStatusState.HIDDEN; animationView.stopAnimation(); card.visibility=View.GONE; viewportController.scrollToLatestMessage(force=true) }
 fun onHostPause() { if (state==SearchStatusState.SEARCHING) animationView.stopAnimation() }
 fun onHostResume() { if (state==SearchStatusState.SEARCHING) animationView.startAnimation() }
 fun release() { state=SearchStatusState.HIDDEN; animationView.stopAnimation() }
}
