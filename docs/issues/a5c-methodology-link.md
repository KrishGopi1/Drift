# a5c: "How this is calculated" needs a host callback

The why sheet should open Settings > Methodology, but feature modules cannot reference the settings route (ADR 0002).
Workaround: `sleepGraph(navController, onFix, onOpenMethodology: ((MetricKey) -> Unit)? = null)`. The button is hidden
when null. `:app` should pass a lambda that navigates to `SETTINGS_ROUTE` / the metric's citation.
Proposed: A0 adds this optional parameter to ADR 0002's table.
