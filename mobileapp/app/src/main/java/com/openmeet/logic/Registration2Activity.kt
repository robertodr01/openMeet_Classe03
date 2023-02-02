package com.openmeet.logic

import android.Manifest
import android.R.attr
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.children
import androidx.core.widget.doOnTextChanged
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.openmeet.R
import com.openmeet.data.interest.InterestProxyDAO
import java.io.ByteArrayOutputStream


class Registration2Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration_2)

        val instrTxt = findViewById<TextView>(R.id.instructionTxt)
        val continueBtn = findViewById<Button>(R.id.continueBtn)
        val email = intent.getStringExtra("email").toString()

        val sexualPrefsLayout = findViewById<LinearLayout>(R.id.sexualPrefsLayout)
        val interestFilter = findViewById<TextInputLayout>(R.id.filterField)
        val interestView = findViewById<ScrollView>(R.id.interestLayoutView)
        val biographyField = findViewById<TextInputLayout>(R.id.biographyField)

        val snackbarView = findViewById<View>(R.id.auth_reg2_container)


        sexualPrefsLayout.visibility = View.GONE //Programmatically hidden for an Android studio bug

        val sharedPrefs = this.getSharedPreferences(getString(R.string.STD_PREFS), Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("registration_stage", 0).apply() //To remove

        doNextPhase(sharedPrefs.getInt("registration_stage", 0))

        //doImageUploadPhase()
        //doInterestPhase()

        /*val rips = doGetPosition()
        if(rips == null) {
            Snackbar.make(snackbarView, R.string.warn_title, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.retry_dialog) {
                    doGetPosition()
                }
                .show()
        }*/


        val pickMedia = prepareImageUploadPhase()

        continueBtn.setOnClickListener {
            when (sharedPrefs.getInt("registration_stage", 0)) {
                0 -> { /*SEXUAL PREFERENCES STAGE */
                    var valid = true
                    for(input in sexualPrefsLayout.children){
                        if(input is TextInputLayout){
                            input.error = null
                            if(input.editText?.text.isNullOrEmpty()){
                                valid = false
                                input.error = getString(R.string.generic_null_error)
                            }
                        }
                    }

                    if(valid){
                        sexualPrefsLayout.visibility = View.GONE
                        sharedPrefs.edit().putInt("registration_stage", sharedPrefs.getInt("registration_stage", 0) + 1).apply()
                        doNextPhase(sharedPrefs.getInt("registration_stage", 0))
                    }

                }

                1 -> { /* INTEREST PHASE */
                    val selectedList = getSelectedCheckboxes()
                    if (selectedList != null) {
                        if(selectedList.size < 3 || selectedList.size > 6)
                            Snackbar.make(snackbarView, getString(R.string.checkbox_error), Snackbar.LENGTH_SHORT).show()
                        else{
                            interestFilter.visibility = View.GONE
                            interestView.visibility = View.GONE
                            sharedPrefs.edit().putInt("registration_stage", sharedPrefs.getInt("registration_stage", 0) + 1).apply()
                            doNextPhase(sharedPrefs.getInt("registration_stage", 0))
                        }
                    }
                }

                2 -> { /* DO BIOGRAPHY PHASE */
                    if(biographyField.editText?.text.isNullOrEmpty())
                        biographyField.error = getString(R.string.generic_null_error)
                    else{
                        biographyField.visibility = View.GONE
                        sharedPrefs.edit().putInt("registration_stage", sharedPrefs.getInt("registration_stage", 0) + 1).apply()
                        doNextPhase(sharedPrefs.getInt("registration_stage", 0))
                    }
                }


                3 -> { /* PROFILE IMAGES STAGE */
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }

                4 -> { /* FINAL STAGE */
                    startActivity(
                        Intent(this, Registration2Activity::class.java).putExtra("email", intent.getStringExtra("email").toString())
                    )
                    overridePendingTransition(0, 0)
                }
            }

        }



    }

    override fun onBackPressed() { //Reload activity with previus stage
        val sharedPrefs = this.getSharedPreferences(getString(R.string.STD_PREFS), Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("registration_stage", sharedPrefs.getInt("registration_stage", 0) - 1).apply()
        finish();
        startActivity(intent);

    }

    fun doNextPhase(stage: Int) {
        val instrTxt = findViewById<TextView>(R.id.instructionTxt)
        val sexualPrefsLayout = findViewById<LinearLayout>(R.id.sexualPrefsLayout)
        val interestFilter = findViewById<TextInputLayout>(R.id.filterField)
        val interestView = findViewById<ScrollView>(R.id.interestLayoutView)
        val biographyField = findViewById<TextInputLayout>(R.id.biographyField)
        val imageView = findViewById<ScrollView>(R.id.imagesLayoutView)

        when (stage) {
            0 -> { /*SEXUAL PREFERENCES STAGE */
                instrTxt.text = getString(R.string.registration_sexual_stage_title)
                sexualPrefsLayout.visibility = View.VISIBLE
            }

            1 -> { /* INTEREST PHASE */
                instrTxt.text = getString(R.string.registration_interest_stage_title)
                interestFilter.visibility = View.VISIBLE
                interestView.visibility = View.VISIBLE
                doInterestPhase()
            }

            2 -> { /* DO BIOGRAPHY PHASE */
                instrTxt.text = getString(R.string.registration_biography_stage_title)
                biographyField.visibility = View.VISIBLE

            }

            3 -> { /* PROFILE IMAGES STAGE */
                instrTxt.text = getString(R.string.registration_image_stage_title)
                imageView.visibility = View.VISIBLE

            }
        }
    }

    fun doInterestPhase(){

        val snackbarView = findViewById<View>(R.id.auth_reg2_container)

        val interestLayout = findViewById<LinearLayout>(R.id.interestLayout)


        Thread {

            val ret = InterestProxyDAO(this).doRetrieveAll()

            if(ret == null)
                Snackbar.make(snackbarView, R.string.connection_error, Snackbar.LENGTH_LONG).show()
            else{
                runOnUiThread {
                    for (interest in ret){
                        val check = CheckBox(this)
                        check.text = interest.description
                        interestLayout.addView(check)
                    }
                }

            }

        }.start()


        val filterField = findViewById<TextInputLayout>(R.id.filterField)
        filterField.editText?.doOnTextChanged { text, start, before, count ->
            if(text != null){
                for (checkbox in interestLayout.children)
                    if(checkbox is CheckBox) {
                        val textWords = checkbox.text.split(" ")
                        for(word in textWords){
                            if(word.length > text.length) {
                                if (word.substring(0, text.length)
                                        .equals(text.toString(), ignoreCase = true)
                                )
                                    checkbox.visibility = View.VISIBLE
                                else
                                    checkbox.visibility = View.GONE
                            }
                        }
                    }
            }

        }
    }

    fun getSelectedCheckboxes(): MutableList<String>? {
        val interestLayout = findViewById<LinearLayout>(R.id.interestLayout)

        val checkList = mutableListOf<String>()

        for (checkbox in interestLayout.children)
            if(checkbox is CheckBox && checkbox.isChecked)
                checkList.add(checkbox.text.toString())

        return checkList
    }


    fun prepareImageUploadPhase(): ActivityResultLauncher<PickVisualMediaRequest>{

        val sharedPrefs = this.getSharedPreferences(getString(R.string.STD_PREFS), Context.MODE_PRIVATE)
        val imageLayout = findViewById<LinearLayout>(R.id.imageLayout)
        val snackbarView = findViewById<View>(R.id.auth_reg2_container)

        val pickMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
            // Callback is invoked after the user selects a media item or closes the
            // photo picker.
            if (uris.isNotEmpty()) {
                Log.d("PhotoPicker", "Number of items selected: ${uris.size}")

                for(uri in uris){

                    val bitmap = if(Build.VERSION.SDK_INT < 28) {
                        MediaStore.Images.Media.getBitmap(this.contentResolver, uri)

                    } else {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(this.contentResolver, uri))
                    }

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val byteArray: ByteArray = stream.toByteArray()


                    val imageView = ImageView(this)
                    imageView.setImageURI(uri)
                    imageLayout.addView(imageView)
                    imageView.layoutParams.height = 500
                    imageView.setPadding(40, 30, 40, 16)
                }

                /*Thread {
                    for(uri in uris){

                        val img = Image()
                        ImageProxyDAO(this).doSave(img)
                    }

                }*/

                sharedPrefs.edit().putInt("registration_stage", sharedPrefs.getInt("registration_stage", 0) + 1).apply()
            } else {
                Log.d("PhotoPicker", "No media selected")
                Snackbar.make(snackbarView, getString(R.string.image_error), Snackbar.LENGTH_SHORT).show()
            }
        }

        return pickMedia

    }


    //Da spostare in home. Per ora qui per fare test.
    fun doGetPosition(): String? {

        val snackbarView = findViewById<View>(R.id.auth_reg2_container)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(snackbarView, "Dai il permesso", Snackbar.LENGTH_SHORT).show()

            //onRequestPermissionsResult(420, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), r )
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 420)
            return ""
        }
        /*else
            Snackbar.make(snackbarView, "Permesso concesso", Snackbar.LENGTH_SHORT).show()*/

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if(!checkGPSEnabled(locationManager))
            return null

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val latitude = location.latitude
                val longitude = location.longitude

                //Snackbar.make(snackbarView, "$latitude $longitude", Snackbar.LENGTH_SHORT).show()
                Toast.makeText(this@Registration2Activity, "$latitude $longitude", Toast.LENGTH_SHORT ).show()
                locationManager.removeUpdates(this)

                Toast.makeText(this@Registration2Activity, "Qui ci arrivo2", Toast.LENGTH_SHORT ).show()
                if(Build.VERSION.SDK_INT < 33){
                    val addr = Geocoder(this@Registration2Activity).getFromLocation(latitude, longitude, 1)
                   // Snackbar.make(snackbarView, addr.toString(), Snackbar.LENGTH_SHORT).show()

                }
                else{
                    Geocoder(this@Registration2Activity).getFromLocation(latitude, longitude, 1){
                        Snackbar.make(snackbarView, it[0].locality, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            // other overrides
        }


        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 1f, locationListener)

        return "wow"
    }

    fun checkGPSEnabled(lm: LocationManager): Boolean{

        var gpsEnabled = false

        gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)


        if (!gpsEnabled) {
            // notify user
            AlertDialog.Builder(this)
                .setTitle(R.string.warn_title)
                .setMessage(R.string.GPS_disabled_message)
                .setPositiveButton(R.string.positive_dialog
                ) { paramDialogInterface, paramInt -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .show()
        }
        else
            return true

        return false
    }
}