package org.dpdns.alwaysup.subflow.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import org.dpdns.alwaysup.subflow.domain.util.CalculationSnapshot
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter
import java.time.LocalDate

val LocalCalculationSnapshot = staticCompositionLocalOf {
    CalculationSnapshot("USD", LocalDate.now(), CurrencyConverter.captureSnapshot())
}
