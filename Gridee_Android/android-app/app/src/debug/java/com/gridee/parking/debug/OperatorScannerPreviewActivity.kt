package com.gridee.parking.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gridee.parking.ui.qr.QrScannerActivity

/** Debug-only entry point for physical-device testing without an operator account. */
class OperatorScannerPreviewActivity : AppCompatActivity() {

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        setResult(if (it.resultCode == Activity.RESULT_OK) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = android.R.id.content })

        if (savedInstanceState == null) {
            scannerLauncher.launch(
                Intent(this, QrScannerActivity::class.java).apply {
                    putExtra(QrScannerActivity.EXTRA_SCAN_TYPE, "VEHICLE_CHECK_IN")
                    putExtra(QrScannerActivity.EXTRA_PARKING_LOT_ID, "DEBUG-DEVICE-TEST")
                }
            )
        }
    }
}
