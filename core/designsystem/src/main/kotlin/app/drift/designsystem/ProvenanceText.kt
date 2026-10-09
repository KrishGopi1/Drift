package app.drift.designsystem

import app.drift.model.Provenance

/**
 * Words for a value's provenance. Screens no longer show provenance tags (owner decision, plan section 1); the words
 * stay in TalkBack descriptions and in the "how this is calculated" explanations.
 */
fun Provenance.labelRes(): Int = when (this) {
    Provenance.MEASURED -> R.string.ds_provenance_measured
    Provenance.DERIVED -> R.string.ds_provenance_derived
    Provenance.ESTIMATED -> R.string.ds_provenance_estimated
    Provenance.USER_ENTERED -> R.string.ds_provenance_user_entered
}
