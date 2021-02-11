// Ravindu

package com.example.apex.ui.parking;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.akexorcist.googledirection.DirectionCallback;
import com.akexorcist.googledirection.GoogleDirection;
import com.akexorcist.googledirection.config.GoogleDirectionConfiguration;
import com.akexorcist.googledirection.model.Direction;
import com.akexorcist.googledirection.model.Route;
import com.akexorcist.googledirection.util.DirectionConverter;
import com.example.apex.R;
import com.example.apex.databinding.FragmentParkingBinding;
import com.example.apex.utils.LocationInterface;
import com.example.apex.viewadapters.LocationRecyclerViewAdapter;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import timber.log.Timber;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.INPUT_METHOD_SERVICE;

public class ParkingFragment extends Fragment {

    private FragmentParkingBinding binding; // binding to front end

    private GoogleMap map;
    private CameraPosition cameraPosition;

    private PlacesClient placesClient; //google API to search Places

    private FusedLocationProviderClient fusedLocationProviderClient;  // android location service

    private final LatLng defaultLocation = new LatLng(6.9271, 79.8612); //default location  settings
    private static final int DEFAULT_ZOOM = 15;   //default camera zoom
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean locationPermissionGranted;

    private Location lastKnownLocation; // last location if gps off

    private Place currentPlace; //current location

    private static final String KEY_CAMERA_POSITION = "camera_position"; //camera key for saving instance
    private static final String KEY_LOCATION = "location"; //location key for saving instance

    private static final int AUTOCOMPLETE_REQUEST_CODE = 1; // get the search result

    private final FirebaseFirestore db = FirebaseFirestore.getInstance(); // initialize firebase DB

    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance(); //initialize firebase authentication

    //get the current user
    private final DocumentReference documentReferenceCurrentUser = db.collection("users")
            .document(Objects.requireNonNull(firebaseAuth.getCurrentUser()).getUid());

    //saved location list reference of the user
    private final CollectionReference collectionReferenceLocation = documentReferenceCurrentUser
            .collection("saved_locations");

    private LocationRecyclerViewAdapter locationRecyclerViewAdapter;

    //bind to the view.
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentParkingBinding.inflate(inflater, container, false); //initialize te binding
        return binding.getRoot();
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        hideKeyboard();

        if (savedInstanceState != null) { //when switch between tabs
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION); // set the previous location
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);  // set the previous camera position
        }

        Places.initialize(requireContext(), getString(R.string.MAPS_API_KEY));  //initialize place library
        placesClient = Places.createClient(requireContext());

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity()); // android location service
        //l0ad the map
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map_parking); // referencing fragment intended to map


        mapFragment.getMapAsync(googleMap -> {
            map = googleMap;  // set map
            map.getUiSettings().setZoomControlsEnabled(true);  //set zoom level
            map.getUiSettings().setZoomGesturesEnabled(true);  //set zoom gesture


            // current l0cati0n pin
            map.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
                @Override
                public View getInfoWindow(Marker marker) {
                    return null;
                }

                @Override
                public View getInfoContents(Marker marker) {
                    View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                            (FrameLayout) requireActivity().findViewById(R.id.map_parking), false); //load map to fragment parking xml

                    TextView title = infoWindow.findViewById(R.id.title);
                    title.setText(marker.getTitle());

                    TextView snippet = infoWindow.findViewById(R.id.snippet);
                    snippet.setText(marker.getSnippet());

                    return infoWindow;
                }
            });

            getLocationPermission();

            updateLocationUI();

            getDeviceLocation();
        });

        //recycle menu 0f list layer initialize
        binding.bottomSheet.recyclerviewParking.setHasFixedSize(true);
        binding.bottomSheet.recyclerviewParking.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        locationRecyclerViewAdapter = new LocationRecyclerViewAdapter(requireContext(), new LocationInterface() {
            @Override
            public void setPath(LatLng latLng) {
                requestDirection(latLng); //set the path to the selected one in the list
            }

            @Override
            public void hideBottomSheet() {  // hide the bottom sheet when one item selected
                BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet.getRoot());
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });



        binding.bottomSheet.recyclerviewParking.setAdapter(locationRecyclerViewAdapter); // initiate te list to recycle view

        getSavedLocations();

        binding.btnCurrentLocation.setOnClickListener(v -> getDeviceLocation()); //btn to get current location

        binding.btnLocationSave.setOnClickListener(v -> { //btn to save the location
            if (currentPlace != null) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

                builder.setMessage("Do you want to save current location?")
                        .setTitle("Save Location")
                        .setPositiveButton("Yes", (dialogInterface, i) -> {
                            com.example.apex.models.Location location = com.example.apex.models.Location.builder() //initialize location in models
                                    .placeId(currentPlace.getId())              //get necessary data to create te object in DB
                                    .name(currentPlace.getName())
                                    .latitude(currentPlace.getLatLng().latitude)
                                    .longitude(currentPlace.getLatLng().longitude)
                                    .build();

                            collectionReferenceLocation.add(location)
                                    .addOnSuccessListener(documentReference -> {       //save the data to DB
                                        //save the current l0cati0n
                                        Toast.makeText(requireContext(), "Successfully Saved.", Toast.LENGTH_SHORT).show();
                                        currentPlace = null;
                                        getSavedLocations();
                                    }).addOnFailureListener(e -> {
                                Toast.makeText(requireContext(), "Error Saving Location. Try Again", Toast.LENGTH_SHORT).show();
                            });
                        }).setNegativeButton("No", (dialogInterface, i) -> dialogInterface.cancel());

                builder.show();

            } else {
                Toast.makeText(requireContext(), "Select a Place to Save.", Toast.LENGTH_SHORT).show();
            }
        });

        binding.txtSearch.setOnClickListener(v -> {  //search text field dropdown suggestion list
            List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG);

            Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                    .setCountry("LK")
                    .build(requireContext());
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE);
        });

        BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet.getRoot()); // initialize bottom sheet

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {  // bottom sheet arrow turning
                switch (newState) {
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        binding.bottomSheet.arrow.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_keyboard_arrow_up_24));
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                        binding.bottomSheet.arrow.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_keyboard_arrow_down_24));
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {  //call this wile sliding (not necessary )

            }
        });
    }


    //fetching saved location from the firebase and show in recycle view
    private void getSavedLocations() {
        collectionReferenceLocation.orderBy("timestamp", Query.Direction.DESCENDING).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<com.example.apex.models.Location> locationList = new ArrayList<>();  //needed list to add the recycle view [location in models  ]

                        for (QueryDocumentSnapshot documentSnapshot : task.getResult()) {
                            com.example.apex.models.Location location = documentSnapshot.toObject(com.example.apex.models.Location.class);

                            locationList.add(location);   // add the locations to a list
                        }

                        locationRecyclerViewAdapter.setLocationList(locationList);  // set the location list to recycle view adapter
                    } else {
                        Toast.makeText(requireContext(), "Error getting Locations.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override

    // get the search results
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        hideKeyboard();
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = Autocomplete.getPlaceFromIntent(data);//get place
                Timber.e("Place: " + place.getName() + ", " + place.getId() + ", " + place.getLatLng());
                currentPlace = place;// set the place name t0 field
                binding.txtSearch.setText(place.getName()); //bind search text field
                moveCameraToLocation(place.getLatLng());  //set camera to selected location
            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {  //error handle to search fuction
                Status status = Autocomplete.getStatusFromIntent(data);
                Timber.e(status.getStatusMessage());
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void moveCameraToLocation(LatLng location) {
        map.clear(); //clear map

        map.addMarker(new MarkerOptions().position(location)); // add marker

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(location,  //set camera to te location
                DEFAULT_ZOOM));
    }

    private void requestDirection(LatLng to) {
        GoogleDirectionConfiguration.getInstance().setLogEnabled(true);

        //set the start and end p0int

        if (lastKnownLocation != null) {
            GoogleDirection.withServerKey(getString(R.string.MAPS_API_KEY))  //google direction
                    .from(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude())) //current location
                    .to(to)  // Destination
                    .execute(new DirectionCallback() {
                        @Override
                        public void onDirectionSuccess(@Nullable Direction direction) {
                            if (direction != null && direction.isOK()) {
                                map.clear();
                                currentPlace = null;
                                Route route = direction.getRouteList().get(0);
                                map.addMarker(new MarkerOptions().position(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude())));
                                map.addMarker(new MarkerOptions().position(to));
                                ArrayList<LatLng> directionPositionList = route.getLegList().get(0).getDirectionPoint();
                                map.addPolyline(DirectionConverter.createPolyline(requireContext(), directionPositionList, 5, Color.RED));
                                setCameraWithCoordinationBounds(route);
                            } else {
                                Toast.makeText(requireContext(), "Error Retrieving Route Data", Toast.LENGTH_SHORT).show();
                                if (direction != null) {
                                    Timber.e(direction.getStatus());
                                }
                            }
                        }

                        @Override
                        public void onDirectionFailure(@NonNull Throwable t) {
                            Toast.makeText(requireContext(), "Error Retrieving Route Data", Toast.LENGTH_SHORT).show();
                            Timber.e(t);
                        }
                    });
        } else {
            Toast.makeText(requireContext(), "Enable GPS", Toast.LENGTH_SHORT).show();  // if last location is null
        }
    }

    private void setCameraWithCoordinationBounds(Route route) {  //set camera to te correct position
        LatLng southwest = route.getBound().getSouthwestCoordination().getCoordination();
        LatLng northeast = route.getBound().getNortheastCoordination().getCoordination();
        LatLngBounds bounds = new LatLngBounds(southwest, northeast);
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
    }

    private void getDeviceLocation() {
        try {
            if (locationPermissionGranted) {
                map.clear();
                binding.txtSearch.setText("Location");
                currentPlace = null;
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        lastKnownLocation = task.getResult();
                        if (lastKnownLocation != null) {
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(lastKnownLocation.getLatitude(),
                                            lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            map.addMarker(new MarkerOptions().position(new LatLng(lastKnownLocation.getLatitude(),
                                    lastKnownLocation.getLongitude())));
                        }
                    } else {
                        Timber.d("Current location is null. Using defaults.");
                        Timber.e("Exception: " + task.getException());
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                        map.addMarker(new MarkerOptions().position(defaultLocation));
                    }
                });
            }
        } catch (SecurityException e) {
            Timber.e(e, "Exception: %s", e.getMessage());
        }
    }

    private void getLocationPermission() {  //ask for permission
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override  //get the permission result
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        locationPermissionGranted = false;
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true;
            }
        }
        updateLocationUI();
    }

    private void updateLocationUI() {
        if (map == null) {
            return;
        }
        try {
            if (!locationPermissionGranted) {
                lastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e) {
            Timber.e("Exception: " + e.getMessage());
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {  //save the previous instance
        if (map != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, map.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, lastKnownLocation);
        }
        super.onSaveInstanceState(outState);
    }

    private void hideKeyboard() {
        final InputMethodManager inputMethodManager = (InputMethodManager) requireActivity().getSystemService(INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(binding.getRoot().getApplicationWindowToken(), 0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}