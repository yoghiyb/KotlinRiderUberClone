package com.yoghi.kotlinrideruberclone.model

import com.firebase.geofire.GeoLocation

class DriverGeoModel {
    var key: String? = null
    var geoLocation: GeoLocation? = null
    var driverInfoModel: DriverInfoMode? = null

    constructor(key: String?, geoLocation: GeoLocation?){
        this.key = key
        this.geoLocation = geoLocation!!
    }
}