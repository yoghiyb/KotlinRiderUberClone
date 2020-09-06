package com.yoghi.kotlinrideruberclone.common

import com.yoghi.kotlinrideruberclone.model.RiderModel

object Common {

    fun buildWelcomeMessage(): String {
        return StringBuilder("Welcome, ")
            .append(currentRider!!.firstName)
            .append(" ")
            .append(currentRider!!.lastName)
            .toString()
    }

    var currentRider: RiderModel? = null
    val RIDER_INFO_REFERENCE: String = "Riders"
}