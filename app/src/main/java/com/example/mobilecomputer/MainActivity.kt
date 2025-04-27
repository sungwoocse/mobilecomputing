package com.example.mobilecomputer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 1

    private lateinit var btnUploadMap: Button
    private lateinit var btnDeleteMap: Button
    private lateinit var btnWardriving: Button
    private lateinit var btnLocalization: Button
    private lateinit var btnExportEmail: Button
    private lateinit var imageViewMap: ImageView
    private lateinit var tvCoordinates: TextView

    private var floorMap: Bitmap? = null
    private var mapLoaded = false
    private var currentX = 0f
    private var currentY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        initializeUI()

        // Request permissions
        requestPermissions()
    }

    private fun initializeUI() {
        try {
            btnUploadMap = findViewById(R.id.btnUploadMap)
            btnDeleteMap = findViewById(R.id.btnDeleteMap)
            btnWardriving = findViewById(R.id.btnWardriving)
            btnLocalization = findViewById(R.id.btnLocalization)
            btnExportEmail = findViewById(R.id.btnExportEmail)
            imageViewMap = findViewById(R.id.imageViewMap)
            tvCoordinates = findViewById(R.id.tvCoordinates)

            // Set button click listeners
            btnUploadMap.setOnClickListener { uploadMap() }
            btnDeleteMap.setOnClickListener { deleteMap() }
            btnWardriving.setOnClickListener { Toast.makeText(this, "Wardriving button clicked", Toast.LENGTH_SHORT).show() }
            btnLocalization.setOnClickListener { Toast.makeText(this, "Localization button clicked", Toast.LENGTH_SHORT).show() }
            btnExportEmail.setOnClickListener { Toast.makeText(this, "Export button clicked", Toast.LENGTH_SHORT).show() }

            // Map image click event
            imageViewMap.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN && mapLoaded) {
                    currentX = event.x / imageViewMap.width
                    currentY = event.y / imageViewMap.height
                    handleMapClick(currentX, currentY)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "UI initialization error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allPermissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (!allPermissionsGranted) {
                Toast.makeText(this, "App requires all permissions to function properly.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val getMapContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(it)
                floorMap = BitmapFactory.decodeStream(inputStream)
                imageViewMap.setImageBitmap(floorMap)
                mapLoaded = true

                btnDeleteMap.visibility = View.VISIBLE
                btnWardriving.visibility = View.VISIBLE
                btnLocalization.visibility = View.VISIBLE
                btnExportEmail.visibility = View.VISIBLE

            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Error loading image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadMap() {
        getMapContent.launch("image/*")
    }

    private fun deleteMap() {
        if (mapLoaded) {
            floorMap = null
            imageViewMap.setImageBitmap(null)
            mapLoaded = false

            btnDeleteMap.visibility = View.GONE
            btnWardriving.visibility = View.GONE
            btnLocalization.visibility = View.GONE
            btnExportEmail.visibility = View.GONE
            tvCoordinates.text = ""

            Toast.makeText(this, "Map deleted.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleMapClick(x: Float, y: Float) {
        val coordinateText = "(${String.format("%.2f", x)}, ${String.format("%.2f", y)})"
        tvCoordinates.text = "Selected position: $coordinateText"

        Toast.makeText(this, "Selected position: $coordinateText", Toast.LENGTH_SHORT).show()
    }
}