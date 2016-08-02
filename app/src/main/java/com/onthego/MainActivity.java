package com.onthego;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    public static final String TAG = "OnTheGo";
    public static final int PLACE_PICKER_REQUEST = 575;

    private TextView txtView;
    private GoogleMap mMap;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_PICKER_REQUEST) {
            if (resultCode == RESULT_OK) {
                Place place = PlacePicker.getPlace(data, this);
                String toastMsg = String.format("Place: %s", place.getName());
                Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();

                DatabaseReference dRef = FirebaseDatabase.getInstance().getReference("Zones");
                dRef.child(place.getName().toString()).setValue(place.getName().toString());

                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("geofire");
                GeoFire geoFire = new GeoFire(ref);
                geoFire.setLocation(place.getName().toString(), new GeoLocation(place.getLatLng().latitude, place.getLatLng().longitude));
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        findViewById(R.id.hello).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();

                try {
                    startActivityForResult(builder.build(MainActivity.this), PLACE_PICKER_REQUEST);
                } catch (GooglePlayServicesRepairableException e) {
                    e.printStackTrace();
                } catch (GooglePlayServicesNotAvailableException e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMarkerClickListener(this);
        Log.d(TAG, "Got the map");

        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                Log.d(TAG, "Got the location");
                // Add a marker in Sydney, Australia, and move the camera.
                LatLng boston = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.addMarker(new MarkerOptions().position(boston).icon(BitmapDescriptorFactory.fromResource(R.mipmap.mepin)).title("Your location!"));
                mMap.moveCamera(CameraUpdateFactory.newLatLng(boston));

                // Called when a new location is found by the network location provider.
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("geofire");
                GeoFire geoFire = new GeoFire(ref);

                // creates a new query around [37.7832, -122.4056] with a radius of 0.6 kilometers
                GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(location.getLatitude(), location.getLongitude()), 1000);
                geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                    @Override
                    public void onKeyEntered(String key, GeoLocation location) {
                        Log.d(TAG, "key " + key + "lat lon " + location.latitude + " " + location.longitude);


                        LatLng zone = new LatLng(location.latitude, location.longitude);
                        final Marker marker = mMap.addMarker(new MarkerOptions().position(zone));
                        mMap.moveCamera(CameraUpdateFactory.newLatLng(zone));


                        FirebaseDatabase database = FirebaseDatabase.getInstance();
                        DatabaseReference myRef = database.getReference("Zones");

                        myRef.child(key).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                Log.d(TAG, "Got the data value!");
                                marker.setTitle(dataSnapshot.getValue().toString());

                            }

                            @Override
                            public void onCancelled(DatabaseError error) {
                                // Failed to read value
                                Log.w(TAG, "Failed to read value.", error.toException());
                            }
                        });
                        Log.d(TAG, String.format("Key %s entered the search area at [%f,%f]", key, location.latitude, location.longitude));
                    }

                    @Override
                    public void onKeyExited(String key) {
                        Log.d(TAG, String.format("Key %s is no longer in the search area", key));
                    }

                    @Override
                    public void onKeyMoved(String key, GeoLocation location) {
                        Log.d(TAG, String.format("Key %s moved within the search area to [%f,%f]", key, location.latitude, location.longitude));
                    }

                    @Override
                    public void onGeoQueryReady() {
                        Log.d(TAG, "All initial data has been loaded and events have been fired!");
                    }

                    @Override
                    public void onGeoQueryError(DatabaseError error) {
                        Log.d(TAG, "There was an error with this query: " + error);
                    }
                });

            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        try {
// Register the listener with the Location Manager to receive location updates
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 20, locationListener);
        } catch (SecurityException se) {
            Log.e(TAG, "Security Exception ");
            se.printStackTrace();
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        startActivity(new Intent(this, RedeemOfferActivity.class));
        return false;
    }
}


