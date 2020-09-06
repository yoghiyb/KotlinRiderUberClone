package com.yoghi.kotlinrideruberclone.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.yoghi.kotlinrideruberclone.common.Common

object UserUtils {

    fun updateUser(view: View?, updateData: Map<String, Any>){
        FirebaseDatabase.getInstance()
            .getReference(Common.RIDER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener { e ->
                Snackbar.make(view!!, e.message!!, Snackbar.LENGTH_LONG).show()
            }.addOnSuccessListener {
                Snackbar.make(view!!, "Update Information Success", Snackbar.LENGTH_LONG).show()
            }
    }
}