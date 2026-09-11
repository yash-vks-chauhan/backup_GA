package com.gridee.parking.ui.booking

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gridee.parking.ui.lot.ChooseCategoryActivity

/** Compatibility entry point that now enforces organization -> location -> lot selection. */
class ParkingLotSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(ChooseCategoryActivity.changeIntent(this))
        finish()
    }
}
