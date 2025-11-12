package com.example.safebankid.services.ml

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// --- FULL MESH (468) + orientación estable + normalización robusta ---
fun featuresFromLandmarksXYZ(
    xs: FloatArray,
    ys: FloatArray,
    zs: FloatArray
): FloatArray {
    val n = xs.size
    require(ys.size == n && zs.size == n && n >= 5)

    // 0) corregir orientación izquierda/derecha (si el set viene espejado por la cámara frontal)
    // MediaPipe FaceMesh: índice 33 ≈ ojo derecho externo, 263 ≈ ojo izquierdo externo
    // (si no existen por algún motivo, se ignora el paso)
    val hasEyes = n > 263
    var flipX = false
    if (hasEyes) {
        val rightEyeX = xs[33]
        val leftEyeX  = xs[263]
        // En coordenadas de imagen (origen arriba-izquierda), el ojo izquierdo debería tener x < ojo derecho.
        // Si no se cumple, el frame está espejado → invertimos X.
        if (leftEyeX > rightEyeX) flipX = true
    }

    // 1) centrar
    var mx = 0f; var my = 0f; var mz = 0f
    for (i in 0 until n) {
        val x = if (flipX) (1f - xs[i]) else xs[i]   // si está espejado, "des-espeja"
        mx += x; my += ys[i]; mz += zs[i]
    }
    mx /= n; my /= n; mz /= n

    val cx = FloatArray(n); val cy = FloatArray(n); val cz = FloatArray(n)
    for (i in 0 until n) {
        val x = if (flipX) (1f - xs[i]) else xs[i]
        cx[i] = x - mx
        cy[i] = ys[i] - my
        cz[i] = zs[i] - mz
    }

    // 2) alinear rotación en plano (x,y) por PCA 2D para que nariz->oreja siempre tenga ángulo estable
    var sxx = 0f; var syy = 0f; var sxy = 0f
    for (i in 0 until n) { sxx += cx[i]*cx[i]; syy += cy[i]*cy[i]; sxy += cx[i]*cy[i] }
    val theta = 0.5f * kotlin.math.atan2(2f*sxy, (sxx - syy))
    val c = kotlin.math.cos(theta); val s = kotlin.math.sin(theta)

    val rx = FloatArray(n); val ry = FloatArray(n); val rz = FloatArray(n)
    var energy = 0f
    for (i in 0 until n) {
        val xr = c*cx[i] - s*cy[i]
        val yr = s*cx[i] + c*cy[i]
        rx[i] = xr
        ry[i] = yr
        rz[i] = cz[i]         // Z solo centrado (profundidad relativa)
        energy += xr*xr + yr*yr + cz[i]*cz[i]
    }

    // 3) escala invariante (evita caras “grandes/pequeñas” por distancia a cámara)
    energy = kotlin.math.sqrt(kotlin.math.max(energy / n.toFloat(), 1e-6f))
    for (i in 0 until n) { rx[i] /= energy; ry[i] /= energy; rz[i] /= energy }

    // 4) aplanar: [x1',y1',z1', x2',y2',z2', ...] → dimensión 468*3 = 1404
    val out = FloatArray(n * 3)
    var t = 0
    for (i in 0 until n) { out[t++] = rx[i]; out[t++] = ry[i]; out[t++] = rz[i] }
    return out
}



/** Distancia Mahalanobis con varianza diagonal, con piso robusto */
fun mahalanobisDiagonal(x: FloatArray, mu: FloatArray, varDiag: FloatArray): Float {
    require(x.size == mu.size && mu.size == varDiag.size)
    var acc = 0f
    for (i in x.indices) {
        val d = x[i] - mu[i]
        // piso mayor evita “explosiones” por var≈0 en alguna dimensión
        val v = kotlin.math.max(varDiag[i], 1e-4f)
        acc += (d*d) / v
    }
    return kotlin.math.sqrt(acc)
}

data class MeanVar(val mean: FloatArray, val varDiag: FloatArray)

/** Media y varianza diagonal robustas para HIGH-D (1404 dims) */
fun meanVar(vectors: List<FloatArray>): MeanVar {
    require(vectors.isNotEmpty())
    val n = vectors.size
    val d = vectors[0].size
    val mean = FloatArray(d)
    val varD  = FloatArray(d)

    // media
    for (v in vectors) for (i in 0 until d) mean[i] += v[i]
    for (i in 0 until d) mean[i] /= n.toFloat()

    // varianza no-bias y piso por dimensión
    for (v in vectors) for (i in 0 until d) {
        val diff = v[i] - mean[i]; varD[i] += diff*diff
    }
    for (i in 0 until d) varD[i] = (varD[i] / (n - 1).coerceAtLeast(1)).coerceAtLeast(1e-6f)

    // piso robusto adicional usando la mediana de varianzas
    val tmp = varD.copyOf().sorted()
    val medianVar = tmp[tmp.size/2].coerceAtLeast(1e-6f)
    val floor = kotlin.math.max(1e-4f, 0.25f * medianVar)
    for (i in 0 until d) varD[i] = kotlin.math.max(varD[i], floor)

    return MeanVar(mean, varD)
}



/** Percentil simple (0..1) */
fun percentile(values: List<Float>, p: Float): Float {
    if (values.isEmpty()) return 0f
    val cl = values.toMutableList()
    cl.sort()
    val idx = min((p * (cl.size - 1)).coerceIn(0f, cl.size - 1f).toInt(), cl.size - 1)
    return cl[idx]
}
