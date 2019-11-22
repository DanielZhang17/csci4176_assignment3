package ca.dal.cs4176.azhang.bustracker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.View.inflate
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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

    private lateinit var mMap: GoogleMap
    lateinit var mainHandler: Handler
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var locationRequest:LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var requestingLocationUpdates =false
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
        }
        mainHandler = Handler(Looper.getMainLooper())
    }

    override fun onStart() {
        super.onStart()
        if (!hasLocationPermissions()) {
            //request location on first launch
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 35)
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener(this, OnSuccessListener {
                    location ->
                if (location == null) {
                    createLocationRequest()
                    startLocationUpdates()
                }
            }).addOnFailureListener(this) { e -> Log.w("FailLoc", "getLastLocation:onFailure", e)}
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMapToolbarEnabled=false
        mMap.uiSettings.isRotateGesturesEnabled=true
        val loc = LatLng(44.6392264,-63.5863118)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 14.0f))
        getData()
    }

    private fun locate()
    {
        if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not yet granted, ask the user for permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 35)
            //check the permission again
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)!= PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Permission denied, can't get access to location ", Toast.LENGTH_LONG).show()
            else//permission is granted,proceed
                locUpdate()
        }
        else
            locUpdate()
    }
     private fun locUpdate()
    {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location? ->
                // Got last known location
                if(location!=null) {
                    Log.d("CORD",location.latitude.toString()+","+location.longitude)
                    val loc = LatLng(location.latitude,location.longitude)
                    mMap.addMarker(MarkerOptions().position(loc))
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 16.0f))
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
        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
    private val repeatingTask = object : Runnable {
        override fun run() {
            getData()
            //This is to update the bus location every 15 seconds
            mainHandler.postDelayed(this, 15000)
        }
    }

    fun getData()
    {
        var task = PositionReader().execute()
    }
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        requestingLocationUpdates = true
    }
    override fun onResume() {
        super.onResume()
        mainHandler.post(repeatingTask)
        if (requestingLocationUpdates)
            startLocationUpdates()
    }
    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(repeatingTask)
        stopLocationUpdates()
    }
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        requestingLocationUpdates = false
    }
//Followed the answer on this post to figure out how to display the bus number in the marker properly https://stackoverflow.com/questions/24716987/using-an-xml-layout-for-a-google-map-marker-for-android
    private fun createMaker(cord:LatLng,busNumber:String):MarkerOptions{
        val marker:MarkerOptions = MarkerOptions()
        marker.position(cord)
        val view:View = inflate(this,R.layout.bus_marker_layout, null)
        view.layoutParams=ViewGroup.LayoutParams(50, 50)
        view.layout(50,50,50,50)
        val b: Bitmap = Bitmap.createBitmap(50,50,Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        var busID = view.findViewById<TextView>(R.id.bus_id)
        busID.text = busNumber
        view.draw(c)
        marker.icon(BitmapDescriptorFactory.fromBitmap(b))
        return marker
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
                Log.e("DATA","Failed to connect")//The app will somehow crash even if the exception is thrown so checking the connection before sending the request is the only way around this
                throw e
            }
        }
        override
        fun onPostExecute(feedEntities:List<FeedEntity>) {
            mMap.clear()
            for (entity:FeedEntity in feedEntities) {
                //map updates
                val routeID = entity.vehicle.trip.routeId
                val loc = LatLng(entity.vehicle.position.latitude.toDouble(),entity.vehicle.position.longitude.toDouble())
                mMap.addMarker(MarkerOptions().position(loc).title(routeID).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_bus)))
                //mMap.addMarker(createMaker(loc,routeID))
            }
        }
    }
}

