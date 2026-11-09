package com.example.openruncoach.ui.components

/** Linear fraction of the available track; no visual exaggeration or minimum width. */
internal fun splitBarWidthFraction(speed: Float, fastestSpeed: Float): Float {
  require(speed.isFinite() && speed >= 0f)
  require(fastestSpeed.isFinite() && fastestSpeed > 0f && speed <= fastestSpeed)
  return speed / fastestSpeed
}
