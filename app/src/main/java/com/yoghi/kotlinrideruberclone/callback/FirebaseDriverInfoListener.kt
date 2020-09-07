package com.yoghi.kotlinrideruberclone.callback

import com.yoghi.kotlinrideruberclone.model.DriverGeoModel

interface FirebaseDriverInfoListener {
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)
}