package com.example.gpsmapdemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MainActivity extends AppCompatActivity {

    private MapView map;
    private IMapController mapController;
    private LocationManager locationManager;
    private Marker currentMarker; // Marqueur unique OpenStreetMap
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Configuration indispensable d'osmdroid avant de charger le layout
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        // 2. Déclaration d'un User Agent unique pour que les serveurs OSM autorisent le téléchargement des tuiles
        Configuration.getInstance().setUserAgentValue("com.example.gpsmapdemo.lab11");

        setContentView(R.layout.activity_main);

        // 3. Initialisation de la carte OpenStreetMap
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK); // Cartographie colorée par défaut
        map.setMultiTouchControls(true);             // Activation du zoom tactile (pincer pour zoomer)
        mapController = map.getController();
        mapController.setZoom(16.0);                 // Zoom initial de niveau 16 (vue de quartier)

        // 4. Initialisation du gestionnaire GPS
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // 5. Vérification des permissions de géolocalisation
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Vérification de l'activation des puces de localisation
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            buildAlertMessageNoGps();
            return;
        }

        Toast.makeText(this, "Suivi GPS & Réseau activé...", Toast.LENGTH_SHORT).show();

        // Écoute prioritaire de la puce matérielle simulée par l'émulateur (GPS_PROVIDER) à distance = 0m
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000, // Toutes les 1 seconde
                    0,    // 0 mètre : réagit instantanément au moindre clic sur "Set Location"
                    locationListener
            );
        }

        // Écoute secondaire des antennes Wi-Fi/4G (NETWORK_PROVIDER)
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000,
                    0,
                    locationListener
            );
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            GeoPoint nouvellePosition = new GeoPoint(location.getLatitude(), location.getLongitude());

            Toast.makeText(MainActivity.this, "Position reçue: " + location.getLatitude() + ", " + location.getLongitude(), Toast.LENGTH_SHORT).show();

            // Gestion du marqueur unique (création s'il n'existe pas, déplacement sinon)
            if (currentMarker == null) {
                currentMarker = new Marker(map);
                currentMarker.setTitle("Ma position actuelle");
                currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                map.getOverlays().add(currentMarker);
            }
            currentMarker.setPosition(nouvellePosition);

            // Centrage animé et fluide de la carte sur les nouvelles coordonnées
            mapController.animateTo(nouvellePosition);
            map.invalidate(); // Rendu visuel mis à jour
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            buildAlertMessageNoGps();
        }
    };

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Votre GPS est désactivé. Voulez-vous l'activer pour suivre votre position sur la carte ?")
                .setCancelable(false)
                .setPositiveButton("Oui", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("Non", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                        Toast.makeText(MainActivity.this, "Le suivi en temps réel nécessite le GPS.", Toast.LENGTH_LONG).show();
                    }
                });
        builder.create().show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission GPS accordée !", Toast.LENGTH_SHORT).show();
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Permission refusée. La carte ne peut pas vous géolocaliser.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Gestion du rafraîchissement d'OpenStreetMap selon l'activité
    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}
