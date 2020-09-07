package com.yoghi.kotlinrideruberclone.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.yoghi.kotlinrideruberclone.R
import com.yoghi.kotlinrideruberclone.callback.FirebaseDriverInfoListener
import com.yoghi.kotlinrideruberclone.callback.FirebaseFailedListener
import com.yoghi.kotlinrideruberclone.common.Common
import com.yoghi.kotlinrideruberclone.model.DriverGeoModel
import com.yoghi.kotlinrideruberclone.model.DriverInfoMode
import com.yoghi.kotlinrideruberclone.model.GeoQueryModel
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener {

    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment: SupportMapFragment

    // Location
    lateinit var locationRequest: LocationRequest
    lateinit var locationCallback: LocationCallback
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    // Load Driver
    var distance = 1.0
    val LIMIT_RANGE = 10.0
    var previousLocation: Location? = null
    var currentLocation: Location? = null

    var firstTime = true

    // Listener
    lateinit var iFirebaseDriverInfoListener: FirebaseDriverInfoListener
    lateinit var iFirebaseFailedListener: FirebaseFailedListener

    var cityName = ""

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        init()

        mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        return root
    }

    private fun init() {

        iFirebaseDriverInfoListener = this

        locationRequest = LocationRequest()
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationRequest.setFastestInterval(3000)
        locationRequest.setSmallestDisplacement(10f)
        locationRequest.interval = 5000

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(location: LocationResult?) {
                super.onLocationResult(location)
                val newPos =
                    LatLng(location!!.lastLocation.latitude, location!!.lastLocation.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

                // if user has change location, calculate and load driver again
                if (firstTime) {
                    previousLocation = location.lastLocation
                    currentLocation = location.lastLocation

                    firstTime = false
                } else {
                    previousLocation = currentLocation
                    currentLocation = location.lastLocation
                }

                if (previousLocation!!.distanceTo(currentLocation) / 1000 <= LIMIT_RANGE) {
                    loadAvailableDrivers()
                }
            }
        }

        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
//            Snackbar.make(requireView(), getString(R.string.permission_require), Snackbar.LENGTH_LONG).show()
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )

        loadAvailableDrivers()
    }

    private fun loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(
                requireView(),
                getString(R.string.permission_require),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }
        fusedLocationProviderClient.lastLocation
            .addOnFailureListener { e ->
                Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_LONG).show()
            }
            .addOnSuccessListener { location ->
                // Load all drivers in city
                val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                var addressList: List<Address> = ArrayList()

                try {
                    addressList = geoCoder.getFromLocation(location.latitude, location.longitude, 1)
                    cityName = addressList[0].locality

                    // Query
                    val driver_location_ref = FirebaseDatabase.getInstance()
                        .getReference(Common.DRIVERS_LOCATION_REFERENCES)
                        .child(cityName)
                    val gf = GeoFire(driver_location_ref)
                    val geoQuery = gf.queryAtLocation(
                        GeoLocation(location.latitude, location.longitude),
                        distance
                    )
                    geoQuery.removeAllListeners()
                    geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                        override fun onGeoQueryReady() {
                            if (distance <= LIMIT_RANGE) {
                                distance++
                                loadAvailableDrivers()
                            } else {
                                distance = 0.0
                                addDriverMarker()
                            }
                        }

                        override fun onKeyEntered(key: String?, location: GeoLocation?) {
                            Common.driversFound.add(DriverGeoModel(key!!, location!!))
                        }

                        override fun onKeyMoved(key: String?, location: GeoLocation?) {
                            TODO("Not yet implemented")
                        }

                        override fun onKeyExited(key: String?) {
                            TODO("Not yet implemented")
                        }

                        override fun onGeoQueryError(error: DatabaseError?) {
                            Snackbar.make(
                                requireView(),
                                error!!.message,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }

                    })

                    driver_location_ref.addChildEventListener(object:ChildEventListener{
                        override fun onCancelled(error: DatabaseError) {
                            Snackbar.make(
                                requireView(),
                                error.message,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }

                        override fun onChildMoved(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                            TODO("Not yet implemented")
                        }

                        override fun onChildChanged(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                            TODO("Not yet implemented")
                        }

                        override fun onChildAdded(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                            // Have new driver
                            val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                            val geoLocation = GeoLocation(geoQueryModel!!.l!![0], geoQueryModel!!.l!![1])
                            val driverGeoModel = DriverGeoModel(snapshot.key, geoLocation)
                            val newDriverLocation = Location("")
                            newDriverLocation.latitude = geoLocation.latitude
                            newDriverLocation.longitude = geoLocation.longitude
                            val newDistance = location.distanceTo(newDriverLocation) / 1000 // 1000 mean in km
                            if (newDistance <= LIMIT_RANGE){
                                findDriverByKey(driverGeoModel)
                            }
                        }

                        override fun onChildRemoved(snapshot: DataSnapshot) {
                            TODO("Not yet implemented")
                        }
                    })

                } catch (e: IOException) {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.permission_require),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun addDriverMarker() {
        if (Common.driversFound.size > 0){
            Observable.fromIterable(Common.driversFound)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {driverGeoModel: DriverGeoModel? ->
                    findDriverByKey(driverGeoModel)
                    },
                    {
                        t: Throwable? -> Snackbar.make(requireView(), t!!.message!!, Snackbar.LENGTH_LONG).show()
                    }
                )
        }else{
            Snackbar.make(requireView(), getString(R.string.drivers_not_found), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun findDriverByKey(driverGeoModel: DriverGeoModel?) {
        FirebaseDatabase.getInstance()
            .getReference(Common.DRIVER_INFO_REFERENCES)
            .child(driverGeoModel!!.key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onCancelled(error: DatabaseError) {
                    iFirebaseFailedListener.onFirebaseFailed(error.message)
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()){
                        driverGeoModel.driverInfoModel = (snapshot.getValue(DriverInfoMode::class.java))
                        iFirebaseDriverInfoListener.onDriverInfoLoadSuccess(driverGeoModel)
                    }else{
                        iFirebaseFailedListener.onFirebaseFailed(getString(R.string.key_not_found)+driverGeoModel.key)
                    }
                }

            })
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        mMap = googleMap!!

        Dexter.withContext(requireContext())
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationButtonClickListener {
                        fusedLocationProviderClient.lastLocation
                            .addOnFailureListener { e ->
                                Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_LONG)
                                    .show()
                            }
                            .addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude, location.longitude)
                                mMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        userLatLng,
                                        18f
                                    )
                                )
                            }
                        true
                    }

                    // layout button
                    val locationbutton = (mapFragment.requireView()!!
                        .findViewById<View>("1".toInt())!!.parent!! as View)
                        .findViewById<View>("2".toInt())
                    val params = locationbutton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 250
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {
                    TODO("Not yet implemented")
                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Snackbar.make(
                        requireView(),
                        p0!!.permissionName + " needed for run app",
                        Snackbar.LENGTH_LONG
                    ).show()
                }

            })
            .check()

        // Enable zoom
        mMap.uiSettings.isZoomControlsEnabled = true

        try {
            val success = googleMap!!.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.uber_maps_style
                )
            )
            if (!success) {
                Snackbar.make(requireView(), "Load map style field", Snackbar.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Snackbar.make(requireView(), e.message!!, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        // If already have marker with this key, doesn't set it again
        if (!Common.markerList.containsKey(driverGeoModel!!.key)){
            Common.markerList.put(
                driverGeoModel!!.key!!,
                mMap.addMarker(MarkerOptions()
                    .position(LatLng(driverGeoModel!!.geoLocation!!.latitude, driverGeoModel!!.geoLocation!!.longitude))
                    .flat(true)
                    .title(Common.buildName(driverGeoModel.driverInfoModel!!.firstName, driverGeoModel.driverInfoModel!!.lastName))
                    .snippet(driverGeoModel.driverInfoModel!!.phoneNumber)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
            ))
        }

        if (!TextUtils.isEmpty(cityName)){
            val driverLocation = FirebaseDatabase.getInstance()
                .getReference(Common.DRIVERS_LOCATION_REFERENCES)
                .child(cityName)
                .child(driverGeoModel!!.key!!)
            driverLocation.addValueEventListener(object : ValueEventListener{
                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(requireView(), error.message, Snackbar.LENGTH_LONG).show()
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.hasChildren()){
                        if (Common.markerList.get(driverGeoModel!!.key!!) != null){
                            val marker = Common.markerList.get(driverGeoModel.key!!)
                            marker!!.remove() // Remove marker from map
                            Common.markerList.remove(driverGeoModel.key!!) // Remove marker information
                            driverLocation.removeEventListener(this)
                        }
                    }
                }
            })
        }
    }
}