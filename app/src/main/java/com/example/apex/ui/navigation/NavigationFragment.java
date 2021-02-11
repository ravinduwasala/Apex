// Ravindu

package com.example.apex.ui.navigation;

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
import com.example.apex.api.maps_api.MapApiService;
import com.example.apex.api.maps_api.MapApiServiceGenerator;
import com.example.apex.api.weather_api.WeatherApiService;
import com.example.apex.api.weather_api.WeatherApiServiceGenerator;
import com.example.apex.databinding.FragmentNavigationBinding;
import com.example.apex.models.GeoCode;
import com.example.apex.models.Weather;
import com.example.apex.models.WeatherWrapper;
import com.example.apex.viewadapters.WeatherRecyclerViewAdapter;
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
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.observers.DisposableSingleObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.INPUT_METHOD_SERVICE;

public class NavigationFragment extends Fragment {

    private FragmentNavigationBinding binding; //bind to view

    private GoogleMap map; // map variable
    private CameraPosition cameraPosition; // camera position

    private FusedLocationProviderClient fusedLocationProviderClient; // android location service

    private final LatLng defaultLocation = new LatLng(6.9271, 79.8612);// set location to default location if GPS not allowed
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1; // permission request code
    private boolean locationPermissionGranted; // true if | allowed

    private Location lastKnownLocation; //save location time to time if realtime didn't work
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    private static final int AUTOCOMPLETE_REQUEST_CODE = 1; // auto fill and suggestions

    private CompositeDisposable compositeDisposable; // for API call

    private WeatherRecyclerViewAdapter weatherRecyclerViewAdapter;

    //initialize the binding
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentNavigationBinding.inflate(inflater, container, false);
        compositeDisposable = new CompositeDisposable();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        //rest0re and save app l0cati0n and camera
        super.onViewCreated(view, savedInstanceState);

        // save the instance and reload it when come back
        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }

        hideKeyboard();

        Places.initialize(requireContext(), getString(R.string.MAPS_API_KEY)); //initialize places API

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext()); //get current location in android

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map_navigation); //finding the load fragment to the map


        mapFragment.getMapAsync(googleMap -> {                //initialize and set the map settings
            map = googleMap;
            map.getUiSettings().setZoomControlsEnabled(true);
            map.getUiSettings().setZoomGesturesEnabled(true);


            map.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
                @Override
                public View getInfoWindow(Marker marker) {
                    return null;
                }

                @Override
                public View getInfoContents(Marker marker) {
                    View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                            (FrameLayout) requireActivity().findViewById(R.id.map_navigation), false); //load map to fragment navigation xml

                    TextView title = infoWindow.findViewById(R.id.title);
                    title.setText(marker.getTitle());

                    TextView snippet = infoWindow.findViewById(R.id.snippet);
                    snippet.setText(marker.getSnippet());

                    return infoWindow;
                }
            });

            getLocationPermission(); //getting location permission

            updateLocationUI(); //getting location permission

            getDeviceLocation(); // get device's  current location
        });


        //recycle menu 0f list layer initialize
        binding.bottomSheet.recyclerviewWeather.setHasFixedSize(true);
        binding.bottomSheet.recyclerviewWeather.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        weatherRecyclerViewAdapter = new WeatherRecyclerViewAdapter(requireContext());
        binding.bottomSheet.recyclerviewWeather.setAdapter(weatherRecyclerViewAdapter);

        binding.btnCurrentLocation.setOnClickListener(v -> getDeviceLocation()); // get device location when the current location btn calls


        binding.txtTo.setOnClickListener(v -> { //search bar and send the word to google API
            List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG); // search suggesion list it get ID, name etc

            Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                    .setCountry("LK")
                    .build(requireContext());
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE);
        });




        BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet.getRoot()); //initialize bottom sheet


        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {  //bottom sheet slide arrow
                switch (newState) {
                    case BottomSheetBehavior.STATE_COLLAPSED:  // slide down
                        weatherRecyclerViewAdapter.collapseCards();
                        binding.bottomSheet.arrow.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_keyboard_arrow_up_24));
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED: // slide up
                        binding.bottomSheet.arrow.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_keyboard_arrow_down_24));
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });
    }

    @Override

    //result from search API comes
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        hideKeyboard();
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = Autocomplete.getPlaceFromIntent(data); //get place

                binding.txtTo.setText(place.getName()); // set the place name t0 field
                requestDirection(place.getLatLng());
            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {  //error handling
                Status status = Autocomplete.getStatusFromIntent(data);
                Timber.e(status.getStatusMessage());
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void requestDirection(LatLng to) {
        GoogleDirectionConfiguration.getInstance().setLogEnabled(true);

        //set the start and end p0int

        if (lastKnownLocation != null) {
            GoogleDirection.withServerKey(getString(R.string.MAPS_API_KEY))  // calculate the rout
                    .from(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude())) // current location
                    .to(to)
                    .execute(new DirectionCallback() {
                        @Override
                        public void onDirectionSuccess(@Nullable Direction direction) {
                            if (direction != null && direction.isOK()) {
                                map.clear();  //clear the map
                                Route route = direction.getRouteList().get(0); //get result
                                //add start and end marker t0 map.
                                map.addMarker(new MarkerOptions().position(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude())));
                                map.addMarker(new MarkerOptions().position(to));

                                ArrayList<LatLng> directionPositionList = route.getLegList().get(0).getDirectionPoint(); //store latlang objects in array
                                map.addPolyline(DirectionConverter.createPolyline(requireContext(), directionPositionList, 5, Color.RED)); // make the polyline
                                setCameraWithCoordinationBounds(route);// set camera middle   f0r the path
                                getWeatherData(directionPositionList);
                            } else {
                                Toast.makeText(requireContext(), "Error Retrieving Route Data", Toast.LENGTH_SHORT).show(); // error handling ( if no internet )
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
            Toast.makeText(requireContext(), "Enable GPS", Toast.LENGTH_SHORT).show();
        }
    }

    private List<LatLng> selectLocations(ArrayList<LatLng> latLngArrayList) {

        if (latLngArrayList.size() <= 8) { //check the size
            return latLngArrayList;
        } else {
            List<LatLng> l = new ArrayList<>();

            l.add(latLngArrayList.get(0)); // get 1st
            l.add(latLngArrayList.get(latLngArrayList.size() - 1));  // get last

            int size = (latLngArrayList.size() - 2) / 3;  // devide the rest

            List<List<LatLng>> subSets = Lists.partition(latLngArrayList, size);

            for (int i = 0; i < 3; i++) {
                l.add(subSets.get(i).get(subSets.get(i).size() / 2));  // get the middle of each part
            }

            return l;
        }
    }
    //get weather data t0 retrofit
    private void getWeatherData(ArrayList<LatLng> latLngArrayList) {

        List<LatLng> list = selectLocations(latLngArrayList);   // get the selected points list

        Observable<LatLng> observable = Observable.fromIterable(list); //get weather data from (Openweather API) (Maps API)

        Disposable disposable = observable
                .concatMap((Function<LatLng, ObservableSource<WeatherWrapper>>) this::getWrapper).toList() //get wrapper
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSingleObserver<List<WeatherWrapper>>() {
                    @Override
                    public void onSuccess(@io.reactivex.rxjava3.annotations.NonNull List<WeatherWrapper> weatherWrapperList) {
                        weatherRecyclerViewAdapter.setWeatherWrapperList(weatherWrapperList);  //send the list to recycle view adapter
                    }

                    @Override
                    public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        Toast.makeText(requireContext(), "Error Getting Weather Data", Toast.LENGTH_SHORT).show();
                        Timber.e(e);
                    }
                });

        compositeDisposable.add(disposable);
    }

    private Observable<WeatherWrapper> getWrapper(LatLng latLng) {  //initialize the weather API and Map API and call them
        WeatherApiService weatherApiService = WeatherApiServiceGenerator.createService(WeatherApiService.class);

        MapApiService mapApiService = MapApiServiceGenerator.createService(MapApiService.class);

        //get  weather and geo code
        Observable<Weather> weatherObservable = weatherApiService.getWeather(latLng.latitude, latLng.longitude);

        Observable<GeoCode> geoCodeObservable = mapApiService.getGeocode(latLng.latitude + "," + latLng.longitude);

        return Observable.zip(weatherObservable, geoCodeObservable, (weather, geoCode) -> new WeatherWrapper(latLng, weather, geoCode));
    }

    private void setCameraWithCoordinationBounds(Route route) {  // set the camera to the correct position
        LatLng southwest = route.getBound().getSouthwestCoordination().getCoordination();
        LatLng northeast = route.getBound().getNortheastCoordination().getCoordination();
        LatLngBounds bounds = new LatLngBounds(southwest, northeast);
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
    }


    //get the current l0cati0n 0f the device
    private void getDeviceLocation() {
        try {
            if (locationPermissionGranted) {
                map.clear(); //clear map
                binding.txtTo.setText("To"); // set search field for TO
                weatherRecyclerViewAdapter.emptyWeatherWrapperList();  //clear existing weather data
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation(); // get current location
                locationResult.addOnCompleteListener(task -> {  // set te map to current location
                    if (task.isSuccessful()) {
                        lastKnownLocation = task.getResult();
                        if (lastKnownLocation != null) {
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(  //move the camera to current location
                                    new LatLng(lastKnownLocation.getLatitude(),
                                            lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            map.addMarker(new MarkerOptions().position(new LatLng(lastKnownLocation.getLatitude(),  // set marker to that location
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


    //get the request permissi0n t0 access l0cati0n
    private void getLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    //<>
    @Override
    //get request permission result
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        locationPermissionGranted = false;
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
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
    public void onSaveInstanceState(@NonNull Bundle outState) {  //holds on activity result like switching in between components.
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
        if (compositeDisposable != null) {
            compositeDisposable.dispose();
        }
    }
}