package com.yoghi.kotlinrideruberclone

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.yoghi.kotlinrideruberclone.common.Common
import com.yoghi.kotlinrideruberclone.model.RiderModel
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_splash_screen.*
import java.util.*
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {

    companion object {
        private val LOGIN_REQUEST_CODE = 7171
    }

    lateinit var provider: List<AuthUI.IdpConfig>
    lateinit var firebaseAuth: FirebaseAuth
    lateinit var listener: FirebaseAuth.AuthStateListener

    private lateinit var databse: FirebaseDatabase
    private lateinit var riderInfoRef: DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    private fun delaySplashScreen() {
        Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .subscribe({
                    firebaseAuth.addAuthStateListener(listener)
                })
    }

    override fun onStop() {
        if (firebaseAuth != null && listener != null )firebaseAuth.removeAuthStateListener(listener)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        init()
    }

    private fun init() {
        databse = FirebaseDatabase.getInstance()
        riderInfoRef = databse.getReference(Common.RIDER_INFO_REFERENCE)
        provider = Arrays.asList(
                AuthUI.IdpConfig.PhoneBuilder().build(),
                AuthUI.IdpConfig.GoogleBuilder().build()
        )
        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener { myFirebaseAuth ->
            val user = myFirebaseAuth.currentUser
            Log.d("CURRENTUSER", user.toString())
            if(user != null ){
                checkUserFromFirebase()
            }else {
                showLoginLayout()
            }
        }


    }

    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
                .setPhoneButtonId(R.id.btn_phone_sign_in)
                .setGoogleButtonId(R.id.btn_google_sign_in)
                .build()

        startActivityForResult(AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setTheme(R.style.LoginTheme)
                .setAvailableProviders(provider)
                .setIsSmartLockEnabled(false)
                .build(), LOGIN_REQUEST_CODE)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == LOGIN_REQUEST_CODE ){
            val response = IdpResponse.fromResultIntent(data)
            if(resultCode == Activity.RESULT_OK){
                val user = FirebaseAuth.getInstance().currentUser
            }else{
                Toast.makeText(this@SplashScreenActivity, response!!.error!!.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkUserFromFirebase() {
        riderInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                .addListenerForSingleValueEvent(object : ValueEventListener{
                    override fun onCancelled(error: DatabaseError) {
                        Log.d("onCancelled", error.message)
                        Toast.makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_LONG).show()
                    }

                    override fun onDataChange(snapshot: DataSnapshot) {
                        Log.d("SNAPSHOT", snapshot.toString())
                        if (snapshot.exists()){
                            val model = snapshot.getValue(RiderModel::class.java)
                            goToHomeActivity(model)
                        }else {
                            showRegisterLayout()
                        }
                    }

                })
    }

    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this, R.style.DialogTheme)
        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)

        val edt_phone_number =
                itemView.findViewById<View>(R.id.edt_phone_number) as TextInputEditText
        val edt_first_name = itemView.findViewById<View>(R.id.edt_first_name) as TextInputEditText
        val edt_last_name = itemView.findViewById<View>(R.id.edt_last_name) as TextInputEditText

        val btn_continue = itemView.findViewById<View>(R.id.btn_register) as Button

        Log.d("CEK_DATA", FirebaseAuth.getInstance().currentUser.toString())

        // View
        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        // Event Click
        btn_continue.setOnClickListener {
            if (TextUtils.isDigitsOnly(edt_first_name.text.toString())) {
                Toast.makeText(
                        this@SplashScreenActivity,
                        "Please enter First Name",
                        Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            } else if (TextUtils.isDigitsOnly(edt_last_name.text.toString())) {
                Toast.makeText(
                        this@SplashScreenActivity,
                        "Please enter Last Name",
                        Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            } else if (TextUtils.isDigitsOnly(edt_phone_number.text.toString())) {
                Toast.makeText(
                        this@SplashScreenActivity,
                        "Please enter Phone Number",
                        Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            } else {
                val model = RiderModel()
                model.firstName = edt_first_name.text.toString()
                model.lastName = edt_last_name.text.toString()
                model.phoneNumber = edt_phone_number.text.toString()

                riderInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                        .setValue(model)
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                    this@SplashScreenActivity,
                                    e.message.toString(),
                                    Toast.LENGTH_SHORT
                            ).show()

                            progress_bar.visibility = View.GONE
                        }
                        .addOnSuccessListener {
                            Toast.makeText(
                                    this@SplashScreenActivity,
                                    "Register Successfully",
                                    Toast.LENGTH_SHORT
                            ).show()
                            dialog.dismiss()

                            goToHomeActivity(model)

                            progress_bar.visibility = View.GONE
                        }
            }
        }
    }

    private fun goToHomeActivity(model: RiderModel?) {
        Common.currentRider = model
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}