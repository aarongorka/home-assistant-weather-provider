package com.aarongorka.hassweather

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.aarongorka.hassweather.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val settings = Settings.load(this)
        binding.url.setText(settings.url)
        binding.token.setText(settings.token)
        binding.entity.setText(settings.entity)
        binding.hourly.setText(settings.hourlyEntity)
        binding.airQuality.setText(settings.airQualityEntity)
        binding.location.setText(settings.location)
        binding.interval.setText(settings.intervalMinutes.toString())
        binding.gbPackage.setText(settings.gadgetbridgePackage)
        showDetected()
        showStatus(Settings.loadStatus(this) ?: getString(R.string.status_never))

        binding.load.setOnClickListener { loadEntities() }
        binding.save.setOnClickListener {
            val saved = readForm()
            saved.save(this)
            WeatherSync.schedule(this, Settings.load(this))
            showStatus(getString(R.string.saved))
        }
        binding.sync.setOnClickListener {
            readForm().save(this)
            val current = Settings.load(this)
            background { WeatherSync.run(this, current) }
        }
    }

    private fun readForm() = Settings(
        url = binding.url.text.toString(),
        token = binding.token.text.toString(),
        entity = binding.entity.text.toString(),
        hourlyEntity = binding.hourly.text.toString(),
        airQualityEntity = binding.airQuality.text.toString(),
        location = binding.location.text.toString(),
        intervalMinutes = binding.interval.text.toString().toLongOrNull() ?: Settings.DEFAULT_INTERVAL_MINUTES,
        gadgetbridgePackage = binding.gbPackage.text.toString(),
    )

    private fun loadEntities() {
        val form = readForm()
        background {
            val client = HaClient(form.url, form.token)
            val entities = client.weatherEntities()
            val airQuality = client.airQualityEntities()
            runOnUiThread {
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, entities)
                binding.entity.setAdapter(adapter)
                binding.hourly.setAdapter(adapter)
                binding.airQuality.setAdapter(
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, airQuality),
                )
            }
            getString(R.string.loaded, entities.size)
        }
    }

    private fun showDetected() {
        val found = GadgetbridgeSender.installedPackages(this)
        binding.detected.text = if (found.isEmpty()) {
            getString(R.string.detected_none)
        } else {
            getString(R.string.detected, found.joinToString())
        }
    }

    private fun showStatus(text: String) {
        binding.status.text = text
    }

    private fun background(block: () -> String) {
        thread {
            val message = runCatching(block).getOrElse { getString(R.string.failed, it.message) }
            runOnUiThread { showStatus(message) }
        }
    }
}
