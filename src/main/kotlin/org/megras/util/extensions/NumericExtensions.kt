package org.megras.util.extensions

import kotlin.math.abs

fun Double.equalsEpsilon(other: Double, epsilon: Double = 0.000001) = abs(this - other) < epsilon

fun Pair<Double, Double>.equalsEpsilon(other: Pair<Double, Double>, epsilon: Double = 0.000001) = abs(this.first - other.first) < epsilon && abs(this.second - other.second) < epsilon