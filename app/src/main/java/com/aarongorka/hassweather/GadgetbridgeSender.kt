package com.aarongorka.hassweather

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object GadgetbridgeSender {
    const val ACTION = "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER"
    const val EXTRA = "WeatherGz"

    fun gzip(specs: JSONArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(specs.toString().toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }

    fun installedPackages(context: Context): List<String> {
        val probe = Intent(ACTION)
        return context.packageManager.queryBroadcastReceivers(probe, PackageManager.GET_RESOLVED_FILTER)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
    }

    /** Returns the packages the broadcast was sent to. */
    fun send(context: Context, spec: JSONObject, onlyPackage: String): List<String> {
        val targets = if (onlyPackage.isNotBlank()) listOf(onlyPackage) else installedPackages(context)
        val payload = gzip(JSONArray().put(spec))
        targets.forEach { target ->
            context.sendBroadcast(Intent(ACTION).setPackage(target).putExtra(EXTRA, payload))
        }
        return targets
    }
}
