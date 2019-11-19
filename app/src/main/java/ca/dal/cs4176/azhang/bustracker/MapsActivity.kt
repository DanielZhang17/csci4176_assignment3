package ca.dal.cs4176.azhang.bustracker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.transit.realtime.GtfsRealtime.FeedEntity
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.URL
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        lateinit var mMap: GoogleMap
    }
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = getSharedPreferences("sharedPref", Context.MODE_PRIVATE)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        val button = findViewById<FloatingActionButton>(R.id.button)
        button.setOnClickListener {
            locate()
            getData()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!hasLocationPermissions()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 35)
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener(this, OnSuccessListener {
                    location ->
                if (location == null)
                    createLocationRequest()
            }).addOnFailureListener(this) { e -> Log.w("FailLoc", "getLastLocation:onFailure", e)}
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        // Add a marker in Sydney and move the camera

    }
    fun locate()
    {
        if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not yet granted, ask the user for permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 0)
            //check the permission again
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)!= PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Permission denied, can't get access to location ", Toast.LENGTH_LONG).show()
            else//permission is granted,proceed
                LocUpdate()
        }
        else//else proceed directly
            LocUpdate()
    }
     fun LocUpdate()
    {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location? ->
                // Got last known location.
                Log.d("CORD",location.toString())//log the coordinates for debugging
                if(location!=null) {//error check
                    val latitude = location.latitude.toString()
                    val longitude=location.longitude.toString()
                    Log.d("CORD",latitude )
                    Log.d("CORD", longitude)
                    var loc = LatLng(location.latitude,location.longitude)
                    mMap.addMarker(MarkerOptions().position(loc))
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 15.0f))
                }
                else
                    Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener{
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }
    fun hasLocationPermissions() = this.checkSelfPermission(
        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun createLocationRequest() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
    fun getData()
    {
        var task = PositionReader().execute()
    }

    //translated the Java code in the slides to Kotlin
    inner class PositionReader : AsyncTask<Void,Void,List<FeedEntity>>(){

        private val url:URL = URL("http://gtfs.halifax.ca/realtime/Vehicle/VehiclePositions.pb")
        lateinit var list:List<FeedEntity>
        override
        fun doInBackground(vararg params: Void?): List<FeedEntity>?
        {
            try {
                Log.v("DATA","Getting data")
                list = FeedMessage.parseFrom(url.openStream()).entityList
                return list
            }
            catch (e:ConnectException)
            {
                Log.e("DATA","Failed to connect")
                throw e
            }
        }
        override
        fun onPostExecute(feedEntities:List<FeedEntity>) {
            for (entity:FeedEntity in feedEntities) {
                //map updates
                val routeID = entity.vehicle.trip.routeId
                val loc = LatLng(entity.vehicle.position.latitude.toDouble(),entity.vehicle.position.longitude.toDouble())
                mMap.addMarker(MarkerOptions().position(loc).title(routeID))
            }
        }
    }
}
