package ca.dal.cs4176.azhang.bustracker

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
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
import java.net.URL

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    lateinit var mainHandler: Handler
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var locationRequest:LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var requestingLocationUpdates =false
    private  var updateIn = 0
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
        //reserved for map following functionality
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    var loc = LatLng(location.latitude, location.longitude)
                    val follow = sharedPreferences.getBoolean("FOLLOW",false)
                    if(follow)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, 15.0f))
                }
            }
        }
        button.setOnClickListener {
            locate()
        }
        mainHandler = Handler(Looper.getMainLooper())
        sharedPreferences = getSharedPreferences("sharedPref", Context.MODE_PRIVATE)

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
        mMap.uiSettings.isMapToolbarEnabled=false
        mMap.uiSettings.isRotateGesturesEnabled=true
        mMap.uiSettings.isCompassEnabled=false
        //Read the saved camera state and go to the default location if not found
        val lat = sharedPreferences.getFloat("LAT",44.6392264f)
        val lon = sharedPreferences.getFloat("LON",-63.5863118f)
        val zoom = sharedPreferences.getFloat("ZOOM",14.0f)
        val pan = sharedPreferences.getFloat("PAM",30.0f)//Not used
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat.toDouble(),lon.toDouble()), zoom))
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
                    //mMap.addMarker(MarkerOptions().position(loc).title("My Location"))
                    //Does not need to show the marker since the button does is to center the map to the current location
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
    private fun createLocationRequest()
    {
        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
    private val repeatingTask = object : Runnable
    {
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
    private fun startLocationUpdates()
    {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        requestingLocationUpdates = true
    }
    override fun onResume()
    {
        super.onResume()
        mainHandler.post(repeatingTask)
        if (requestingLocationUpdates)
            startLocationUpdates()
    }
    override fun onPause()
    {
        super.onPause()
        mainHandler.removeCallbacks(repeatingTask)
        stopLocationUpdates()
        //save map camera state
        val cam = mMap.cameraPosition
        val lon = cam.target.longitude
        val lat = cam.target.latitude
        val zoom = cam.zoom
        val pan = cam.tilt
        with (sharedPreferences.edit()){
            putFloat("LON",lon.toFloat())
            putFloat("LAT",lat.toFloat())
            putFloat("ZOOM",zoom)
            putFloat("PAN",pan)
            apply()
        }
    }
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        requestingLocationUpdates = false
    }
/*https://stackoverflow.com/questions/24716987/using-an-xml-layout-for-a-google-map-marker-for-android
 *https://stackoverflow.com/questions/15001455/saving-canvas-to-bitmap-on-android
 *Those 2 posts are referenced to show the number in the custom marker
 */
    private fun createMaker(cord:LatLng,busNumber:String):MarkerOptions{
        val marker = MarkerOptions()
        marker.position(cord).title("Route: "+busNumber)
        val paint = Paint()
        paint.color = Color.RED
        paint.textSize = 20.toFloat()
        val icon = BitmapFactory.decodeResource(this.resources,R.drawable.ic_bus)
        val b: Bitmap = Bitmap.createScaledBitmap(icon,74,74,false)
        val c = Canvas(b)
        c.drawText(busNumber,30.toFloat(),30.toFloat(),paint)
        marker.icon(BitmapDescriptorFactory.fromBitmap(b))
        return marker
    }

//referenced the Java code in the slides
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
            mMap.clear()//this avoids doubling the amount of buses every 15s
            for (entity:FeedEntity in feedEntities) {
                //map updates
                val routeID = entity.vehicle.trip.routeId
                val direction = entity.vehicle.trip.directionId
                val loc = LatLng(entity.vehicle.position.latitude.toDouble(),entity.vehicle.position.longitude.toDouble())
                //mMap.addMarker(MarkerOptions().position(loc).title(routeID).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_bus)))
                mMap.addMarker(createMaker(loc,routeID))
            }
        }
    }
}

